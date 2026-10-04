package com.aiorderdeliveryagent.backend;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestAuthProperties {

	private TestAuthProperties() {
	}

	public static void register(DynamicPropertyRegistry registry) {
		registerSigningKey(registry);
		String namespace = "test:auth:ratelimit:" + UUID.randomUUID();
		registry.add("auth.rate-limit.namespace", () -> namespace);
		registry.add("auth.rate-limit.login-identity-max-attempts", () -> "10000");
		registry.add("auth.rate-limit.login-identity-window", () -> "PT30S");
		registry.add("auth.rate-limit.login-ip-max-attempts", () -> "10000");
		registry.add("auth.rate-limit.login-ip-window", () -> "PT30S");
		registry.add("auth.rate-limit.refresh-session-max-attempts", () -> "10000");
		registry.add("auth.rate-limit.refresh-session-window", () -> "PT30S");
		registry.add("auth.rate-limit.refresh-ip-max-attempts", () -> "10000");
		registry.add("auth.rate-limit.refresh-ip-window", () -> "PT30S");
	}

	public static void registerSigningKey(DynamicPropertyRegistry registry) {
		registry.add(
				"auth.access-token-signing-key",
				() -> Base64.getEncoder().encodeToString(
						"generated-test-key-material-32-bytes".getBytes(StandardCharsets.US_ASCII)));
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
		registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
	}
}
