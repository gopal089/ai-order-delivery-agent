package com.aiorderdeliveryagent.backend.auth;

import java.time.Clock;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final UserAccountRepository userAccountRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final Clock clock;

	AccessTokenAuthenticationConverter(
			UserAccountRepository userAccountRepository,
			RefreshTokenRepository refreshTokenRepository,
			Clock clock) {
		this.userAccountRepository = userAccountRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.clock = clock;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		long userId = Long.parseLong(jwt.getSubject());
		UUID sessionId = UUID.fromString(jwt.getClaimAsString("sid"));
		UserAccount user = userAccountRepository.findById(userId)
				.filter(UserAccount::isActive)
				.orElseThrow(this::invalidToken);

		boolean activeSession = refreshTokenRepository
				.existsByTenantIdAndUserIdAndSessionIdAndRevokedAtIsNullAndExpiresAtAfter(
						user.getTenantId(),
						user.getId(),
						sessionId,
						clock.instant());
		if (!activeSession) {
			throw invalidToken();
		}

		return new ApplicationAuthenticationToken(new ApplicationPrincipal(
				user.getId(),
				user.getTenantId(),
				sessionId));
	}

	private OAuth2AuthenticationException invalidToken() {
		return new OAuth2AuthenticationException(new OAuth2Error(
				BearerTokenErrorCodes.INVALID_TOKEN,
				"The access token is invalid",
				null));
	}
}
