package com.aiorderdeliveryagent.backend.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.rate-limit")
public record AuthenticationRateLimitProperties(
		String namespace,
		int loginIdentityMaxAttempts,
		Duration loginIdentityWindow,
		int loginIpMaxAttempts,
		Duration loginIpWindow,
		int refreshSessionMaxAttempts,
		Duration refreshSessionWindow,
		int refreshIpMaxAttempts,
		Duration refreshIpWindow) {

	public AuthenticationRateLimitProperties {
		if (namespace == null || namespace.isBlank()) {
			throw new IllegalArgumentException("Rate-limit namespace is required");
		}
		validateLimit("login identity", loginIdentityMaxAttempts, loginIdentityWindow);
		validateLimit("login IP", loginIpMaxAttempts, loginIpWindow);
		validateLimit("refresh session", refreshSessionMaxAttempts, refreshSessionWindow);
		validateLimit("refresh IP", refreshIpMaxAttempts, refreshIpWindow);
		namespace = namespace.strip().replaceAll(":+$", "");
	}

	private static void validateLimit(String name, int maxAttempts, Duration window) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException(name + " maximum attempts must be positive");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException(name + " window must be positive");
		}
	}
}
