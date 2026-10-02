package com.aiorderdeliveryagent.backend.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.aiorderdeliveryagent.backend.auth.TokenService.IssuedAccessToken;
import com.aiorderdeliveryagent.backend.auth.TokenService.IssuedRefreshToken;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserAuthenticationService {

	private final AuthenticationManager authenticationManager;
	private final UserAccountRepository userAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final TokenService tokenService;
	private final Clock clock;

	UserAuthenticationService(
			AuthenticationManager authenticationManager,
			UserAccountRepository userAccountRepository,
			RefreshTokenRepository refreshTokenRepository,
			TokenService tokenService,
			Clock clock) {
		this.authenticationManager = authenticationManager;
		this.userAccountRepository = userAccountRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.tokenService = tokenService;
		this.clock = clock;
	}

	@Transactional
	TokenResponse login(LoginRequest request) {
		String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);
		final Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(normalizedEmail, request.password()));
		}
		catch (AuthenticationException exception) {
			throw new AuthenticationFailedException();
		}

		AuthenticatedUserPrincipal principal = (AuthenticatedUserPrincipal) authentication.getPrincipal();
		return createSessionTokens(principal, UUID.randomUUID());
	}

	@Transactional(noRollbackFor = AuthenticationFailedException.class)
	TokenResponse refresh(String rawRefreshToken) {
		Instant now = clock.instant();
		RefreshToken storedToken = refreshTokenRepository
				.findByTokenHashForUpdate(tokenService.hashRefreshToken(rawRefreshToken))
				.orElseThrow(AuthenticationFailedException::new);

		if (storedToken.isRevoked()) {
			revokeSession(storedToken, now);
			throw new AuthenticationFailedException();
		}

		if (!storedToken.getExpiresAt().isAfter(now)) {
			storedToken.revoke(now);
			throw new AuthenticationFailedException();
		}

		UserAccount user = userAccountRepository.findById(storedToken.getUserId())
				.filter(candidate -> candidate.getTenantId().equals(storedToken.getTenantId()))
				.orElseThrow(AuthenticationFailedException::new);
		if (!user.isActive() || user.getPasswordHash() == null) {
			revokeSession(storedToken, now);
			throw new AuthenticationFailedException();
		}

		storedToken.revoke(now);
		AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
				user.getId(),
				user.getTenantId(),
				user.getEmail(),
				user.getPasswordHash(),
				true);
		return createSessionTokens(principal, storedToken.getSessionId());
	}

	@Transactional
	void logout(String rawRefreshToken) {
		refreshTokenRepository.findByTokenHashForUpdate(tokenService.hashRefreshToken(rawRefreshToken))
				.ifPresent(token -> revokeSession(token, clock.instant()));
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
