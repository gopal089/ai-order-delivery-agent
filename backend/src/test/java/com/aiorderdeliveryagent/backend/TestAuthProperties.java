package com.aiorderdeliveryagent.backend;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.test.context.DynamicPropertyRegistry;

public final class TestAuthProperties {

	private TestAuthProperties() {
	}

	public static void register(DynamicPropertyRegistry registry) {
		registry.add(
				"auth.access-token-signing-key",
				() -> Base64.getEncoder().encodeToString(
						"generated-test-key-material-32-bytes".getBytes(StandardCharsets.US_ASCII)));
	}
}
