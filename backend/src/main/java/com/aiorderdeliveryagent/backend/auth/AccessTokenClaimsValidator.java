package com.aiorderdeliveryagent.backend.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;
import org.springframework.security.oauth2.jwt.Jwt;

public class AccessTokenClaimsValidator implements OAuth2TokenValidator<Jwt> {
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
			BearerTokenErrorCodes.INVALID_TOKEN,
			"The access token is invalid",
			null);
	private final Clock clock;

	public AccessTokenClaimsValidator(Clock clock) {
		this.clock = clock;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt token) {
		if (token.getIssuedAt() == null || token.getExpiresAt() == null) {
			return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
		}
		if (token.getIssuedAt().isAfter(clock.instant().plus(CLOCK_SKEW))
				|| !token.getExpiresAt().isAfter(token.getIssuedAt())) {
			return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
		}

		try {
			long userId = Long.parseLong(token.getSubject());
			if (userId <= 0) {
				return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
			}
			UUID.fromString(token.getClaimAsString("sid"));
		}
		catch (IllegalArgumentException | NullPointerException exception) {
			return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
		}

		return OAuth2TokenValidatorResult.success();
	}
}
