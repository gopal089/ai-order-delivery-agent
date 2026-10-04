package com.aiorderdeliveryagent.backend.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiorderdeliveryagent.backend.TestAuthProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthEndpointRedisFailureIntegrationTests {

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
	void readinessIsDownWhenRequiredRedisDependencyIsUnavailable() throws Exception {
		mockMvc.perform(get("/actuator/health/readiness"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value("DOWN"))
				.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void livenessRemainsUpWhenRedisIsUnavailable() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}
}
