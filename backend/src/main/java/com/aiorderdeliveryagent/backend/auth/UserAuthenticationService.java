package com.aiorderdeliveryagent.backend.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.aiorderdeliveryagent.backend.auth.TokenService.IssuedAccessToken;
import com.aiorderdeliveryagent.backend.auth.TokenService.IssuedRefreshToken;
import com.aiorderdeliveryagent.backend.observability.AuditEventService;
import com.aiorderdeliveryagent.backend.observability.AuditEventType;
import com.aiorderdeliveryagent.backend.observability.AuditOutcome;
import com.aiorderdeliveryagent.backend.observability.SafeSecurityLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserAuthenticationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(UserAuthenticationService.class);
	private static final String AUTHENTICATION_RESOURCE = "authentication_session";

	private final AuthenticationManager authenticationManager;
	private final UserAccountRepository userAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final TokenService tokenService;
	private final AuthenticationRateLimiter rateLimiter;
	private final AuditEventService auditEventService;
	private final SafeSecurityLogger safeLogger;
	private final Clock clock;

	UserAuthenticationService(
			AuthenticationManager authenticationManager,
			UserAccountRepository userAccountRepository,
			RefreshTokenRepository refreshTokenRepository,
			TokenService tokenService,
			AuthenticationRateLimiter rateLimiter,
			AuditEventService auditEventService,
			SafeSecurityLogger safeLogger,
			Clock clock) {
		this.authenticationManager = authenticationManager;
		this.userAccountRepository = userAccountRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.tokenService = tokenService;
		this.rateLimiter = rateLimiter;
		this.auditEventService = auditEventService;
		this.safeLogger = safeLogger;
		this.clock = clock;
	}

	@Transactional(noRollbackFor = {
		AuthenticationFailedException.class,
		AuthenticationRateLimitExceededException.class
	})
	TokenResponse login(LoginRequest request, String clientAddress) {
		String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);
		try {
			rateLimiter.checkLogin(normalizedEmail, clientAddress);
		}
		catch (AuthenticationRateLimitExceededException exception) {
			recordAnonymous(
					AuditEventType.AUTH_RATE_LIMITED,
					AuditOutcome.BLOCKED,
					"login",
					"RATE_LIMITED");
			logAuthenticationEvent(
					AuditEventType.AUTH_RATE_LIMITED,
					AuditOutcome.BLOCKED,
					"login",
					"RATE_LIMITED",
					null,
					null,
					null);
			throw exception;
		}
		catch (AuthenticationRateLimitUnavailableException exception) {
			logAuthenticationEvent(
					AuditEventType.AUTH_LOGIN_FAILURE,
					AuditOutcome.FAILURE,
					"login",
					"RATE_LIMIT_UNAVAILABLE",
					null,
					null,
					null);
			throw exception;
		}
		final Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, request.password()));
		}
		catch (AuthenticationException exception) {
			recordAnonymous(
					AuditEventType.AUTH_LOGIN_FAILURE,
					AuditOutcome.FAILURE,
					"login",
					"AUTHENTICATION_FAILED");
			logAuthenticationEvent(
					AuditEventType.AUTH_LOGIN_FAILURE,
					AuditOutcome.FAILURE,
					"login",
					"AUTHENTICATION_FAILED",
					null,
					null,
					null);
			throw new AuthenticationFailedException();
		}

		AuthenticatedUserPrincipal principal = (AuthenticatedUserPrincipal) authentication.getPrincipal();
		UUID sessionId = UUID.randomUUID();
		TokenResponse response = createSessionTokens(principal, sessionId);
		try {
			rateLimiter.loginSucceeded(normalizedEmail);
		}
		catch (AuthenticationRateLimitUnavailableException exception) {
			logAuthenticationEvent(
					AuditEventType.AUTH_LOGIN_FAILURE,
					AuditOutcome.FAILURE,
					"login",
					"RATE_LIMIT_UNAVAILABLE",
					principal.tenantId(),
					principal.userId(),
					sessionId);
			throw exception;
		}
		recordAuthenticated(
				AuditEventType.AUTH_LOGIN_SUCCESS,
				AuditOutcome.SUCCESS,
				"login",
				"AUTHENTICATED",
				principal.tenantId(),
				principal.userId(),
				sessionId);
		logAuthenticationEvent(
				AuditEventType.AUTH_LOGIN_SUCCESS,
				AuditOutcome.SUCCESS,
				"login",
				"AUTHENTICATED",
				principal.tenantId(),
				principal.userId(),
				sessionId);
		return response;
	}

	@Transactional(noRollbackFor = {
		AuthenticationFailedException.class,
		AuthenticationRateLimitExceededException.class
	})
	TokenResponse refresh(String rawRefreshToken, String clientAddress) {
		try {
			rateLimiter.checkRefreshIp(clientAddress);
		}
		catch (AuthenticationRateLimitExceededException exception) {
			recordAnonymous(
					AuditEventType.AUTH_RATE_LIMITED,
					AuditOutcome.BLOCKED,
					"refresh",
					"RATE_LIMITED");
			logAuthenticationEvent(
					AuditEventType.AUTH_RATE_LIMITED,
					AuditOutcome.BLOCKED,
					"refresh",
					"RATE_LIMITED",
					null,
					null,
					null);
			throw exception;
		}
		catch (AuthenticationRateLimitUnavailableException exception) {
			logAuthenticationEvent(
					AuditEventType.AUTH_REFRESH_FAILURE,
					AuditOutcome.FAILURE,
					"refresh",
					"RATE_LIMIT_UNAVAILABLE",
					null,
					null,
					null);
			throw exception;
		}
		Instant now = clock.instant();
		Optional<RefreshToken> tokenLookup = refreshTokenRepository
				.findByTokenHashForUpdate(tokenService.hashRefreshToken(rawRefreshToken));
		if (tokenLookup.isEmpty()) {
			recordAnonymous(
					AuditEventType.AUTH_REFRESH_FAILURE,
					AuditOutcome.FAILURE,
					"refresh",
					"AUTHENTICATION_FAILED");
			logAuthenticationEvent(
					AuditEventType.AUTH_REFRESH_FAILURE,
					AuditOutcome.FAILURE,
					"refresh",
					"AUTHENTICATION_FAILED",
					null,
					null,
					null);
			throw new AuthenticationFailedException();
		}
		RefreshToken storedToken = tokenLookup.orElseThrow();
		try {
			rateLimiter.checkRefreshSession(storedToken.getSessionId());
		}
		catch (AuthenticationRateLimitExceededException exception) {
			recordAuthenticated(
					AuditEventType.AUTH_RATE_LIMITED,
					AuditOutcome.BLOCKED,
					"refresh",
					"RATE_LIMITED",
					storedToken.getTenantId(),
					storedToken.getUserId(),
					storedToken.getSessionId());
			logAuthenticationEvent(
					AuditEventType.AUTH_RATE_LIMITED,
					AuditOutcome.BLOCKED,
					"refresh",
					"RATE_LIMITED",
					storedToken.getTenantId(),
					storedToken.getUserId(),
					storedToken.getSessionId());
			throw exception;
		}
		catch (AuthenticationRateLimitUnavailableException exception) {
			logAuthenticationEvent(
					AuditEventType.AUTH_REFRESH_FAILURE,
					AuditOutcome.FAILURE,
					"refresh",
					"RATE_LIMIT_UNAVAILABLE",
					storedToken.getTenantId(),
					storedToken.getUserId(),
					storedToken.getSessionId());
			throw exception;
		}

		if (storedToken.isRevoked()) {
			revokeSession(storedToken, now);
			recordAuthenticated(
					AuditEventType.AUTH_REFRESH_FAILURE,
					AuditOutcome.FAILURE,
					"refresh",
					"REUSE_DETECTED",
					storedToken.getTenantId(),
					storedToken.getUserId(),
					storedToken.getSessionId());
			recordAuthenticated(
					AuditEventType.AUTH_REFRESH_REUSE_DETECTED,
					AuditOutcome.BLOCKED,
					"refresh",
					"REUSE_DETECTED",
					storedToken.getTenantId(),
					storedToken.getUserId(),
					storedToken.getSessionId());
			logAuthenticationEvent(
					AuditEventType.AUTH_REFRESH_REUSE_DETECTED,
					AuditOutcome.BLOCKED,
					"refresh",
					"REUSE_DETECTED",
					storedToken.getTenantId(),
					storedToken.getUserId(),
					storedToken.getSessionId());
			throw new AuthenticationFailedException();
		}

		if (!storedToken.getExpiresAt().isAfter(now)) {
			storedToken.revoke(now);
			recordRefreshFailure(storedToken, "AUTHENTICATION_FAILED");
			throw new AuthenticationFailedException();
		}

		Optional<UserAccount> userLookup = userAccountRepository.findById(storedToken.getUserId())
				.filter(candidate -> candidate.getTenantId().equals(storedToken.getTenantId()));
		if (userLookup.isEmpty()) {
			recordRefreshFailure(storedToken, "AUTHENTICATION_FAILED");
			throw new AuthenticationFailedException();
		}
		UserAccount user = userLookup.orElseThrow();
		if (!user.isActive() || user.getPasswordHash() == null) {
			revokeSession(storedToken, now);
			recordRefreshFailure(storedToken, "AUTHENTICATION_FAILED");
			throw new AuthenticationFailedException();
		}

		storedToken.revoke(now);
		AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
				user.getId(),
				user.getTenantId(),
				user.getEmail(),
				user.getPasswordHash(),
				true);
		TokenResponse response = createSessionTokens(principal, storedToken.getSessionId());
		recordAuthenticated(
				AuditEventType.AUTH_REFRESH_SUCCESS,
				AuditOutcome.SUCCESS,
				"refresh",
				"AUTHENTICATED",
				storedToken.getTenantId(),
				storedToken.getUserId(),
				storedToken.getSessionId());
		logAuthenticationEvent(
				AuditEventType.AUTH_REFRESH_SUCCESS,
				AuditOutcome.SUCCESS,
				"refresh",
				"AUTHENTICATED",
				storedToken.getTenantId(),
				storedToken.getUserId(),
				storedToken.getSessionId());
		return response;
	}

	@Transactional
	void logout(String rawRefreshToken) {
		Optional<RefreshToken> tokenLookup = refreshTokenRepository
				.findByTokenHashForUpdate(tokenService.hashRefreshToken(rawRefreshToken));
		if (tokenLookup.isEmpty()) {
			recordAnonymous(
					AuditEventType.AUTH_LOGOUT,
					AuditOutcome.SUCCESS,
					"logout",
					"NO_ACTIVE_SESSION");
			logAuthenticationEvent(
					AuditEventType.AUTH_LOGOUT,
					AuditOutcome.SUCCESS,
					"logout",
					"NO_ACTIVE_SESSION",
					null,
					null,
					null);
			return;
		}

		RefreshToken token = tokenLookup.orElseThrow();
		revokeSession(token, clock.instant());
		recordAuthenticated(
				AuditEventType.AUTH_LOGOUT,
				AuditOutcome.SUCCESS,
				"logout",
				"SESSION_REVOKED",
				token.getTenantId(),
				token.getUserId(),
				token.getSessionId());
		logAuthenticationEvent(
				AuditEventType.AUTH_LOGOUT,
				AuditOutcome.SUCCESS,
				"logout",
				"SESSION_REVOKED",
				token.getTenantId(),
				token.getUserId(),
				token.getSessionId());
	}

	private void recordRefreshFailure(RefreshToken token, String reason) {
		recordAuthenticated(
				AuditEventType.AUTH_REFRESH_FAILURE,
				AuditOutcome.FAILURE,
				"refresh",
				reason,
				token.getTenantId(),
				token.getUserId(),
				token.getSessionId());
		logAuthenticationEvent(
				AuditEventType.AUTH_REFRESH_FAILURE,
				AuditOutcome.FAILURE,
				"refresh",
				reason,
				token.getTenantId(),
				token.getUserId(),
				token.getSessionId());
	}

	private void recordAnonymous(
			AuditEventType eventType,
			AuditOutcome outcome,
			String endpoint,
			String reason) {
		auditEventService.record(
				eventType,
				null,
				null,
				null,
				null,
				outcome,
				Map.of("endpoint", endpoint, "reason", reason));
	}

	private void recordAuthenticated(
			AuditEventType eventType,
			AuditOutcome outcome,
			String endpoint,
			String reason,
			UUID tenantId,
			long userId,
			UUID sessionId) {
		auditEventService.record(
				eventType,
				tenantId,
				userId,
				AUTHENTICATION_RESOURCE,
				sessionId.toString(),
				outcome,
				Map.of("endpoint", endpoint, "reason", reason));
	}

	private void logAuthenticationEvent(
			AuditEventType eventType,
			AuditOutcome outcome,
			String endpoint,
			String reason,
			UUID tenantId,
			Long userId,
			UUID sessionId) {
		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("eventType", eventType.name());
		fields.put("outcome", outcome.name());
		fields.put("endpoint", endpoint);
		fields.put("reason", reason);
		if (tenantId != null) {
			fields.put("tenantId", tenantId);
		}
		if (userId != null) {
			fields.put("userId", userId);
		}
		if (sessionId != null) {
			fields.put("sessionId", sessionId);
		}
		if (outcome == AuditOutcome.SUCCESS) {
			safeLogger.info(LOGGER, "authentication_security_event", fields);
		}
		else {
			safeLogger.warn(LOGGER, "authentication_security_event", fields);
		}
	}

	private void revokeSession(RefreshToken token, Instant revokedAt) {
		refreshTokenRepository.revokeSession(
				token.getTenantId(),
				token.getUserId(),
				token.getSessionId(),
				revokedAt);
	}

	private TokenResponse createSessionTokens(AuthenticatedUserPrincipal principal, UUID sessionId) {
		Instant now = clock.instant();
		IssuedRefreshToken refreshToken = tokenService.issueRefreshToken();
		refreshTokenRepository.saveAndFlush(new RefreshToken(
				principal.tenantId(),
				principal.userId(),
				sessionId,
				refreshToken.hash(),
				refreshToken.expiresAt(),
				now));
		IssuedAccessToken accessToken = tokenService.issueAccessToken(principal, sessionId);
		return new TokenResponse(
				"Bearer",
				accessToken.value(),
				accessToken.expiresInSeconds(),
				refreshToken.value(),
				refreshToken.expiresAt());
	}
}
