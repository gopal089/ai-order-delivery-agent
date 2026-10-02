package com.aiorderdeliveryagent.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthTokenProperties(
		String accessTokenSigningKey,
		Duration accessTokenTtl,
		Duration refreshTokenTtl) {

	public AuthTokenProperties {
		if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
			throw new IllegalArgumentException("auth.access-token-ttl must be positive");
		}
		if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
			throw new IllegalArgumentException("auth.refresh-token-ttl must be positive");
		}
	}
}
