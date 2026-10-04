package com.aiorderdeliveryagent.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.aiorderdeliveryagent.backend.TestAuthProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticationRateLimitRedisFailureIntegrationTests {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestAuthProperties.registerSigningKey(registry);
		registry.add("spring.data.redis.host", () -> "127.0.0.1");
		registry.add("spring.data.redis.port", () -> "1");
		registry.add("spring.data.redis.connect-timeout", () -> "100ms");
		registry.add("spring.data.redis.timeout", () -> "100ms");
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void loginFailsClosedWithGenericServiceUnavailableWhenRedisIsDown() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"unavailable@example.com","password":"SecurePass!234"}
						"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_TEMPORARILY_UNAVAILABLE"))
				.andExpect(jsonPath("$.message")
						.value("Authentication is temporarily unavailable. Try again later."))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.tokenHash").doesNotExist());
	}

	@Test
	void registrationRemainsAvailableWhenRedisIsDown() throws Exception {
		String email = "redis.unavailable." + UUID.randomUUID() + "@example.com";

		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"SecurePass!234"}
						""".formatted(email)))
				.andExpect(status().isCreated());
	}
}
