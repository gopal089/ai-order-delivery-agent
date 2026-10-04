package com.aiorderdeliveryagent.backend.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiorderdeliveryagent.backend.TestAuthProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
class CorsIntegrationTests {

	private static final String LOCAL_FRONTEND = "http://127.0.0.1:3001";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void allowsConfiguredFrontendPreflightForBearerRequest() throws Exception {
		mockMvc.perform(options("/api/v1/integrations")
				.header("Origin", LOCAL_FRONTEND)
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", LOCAL_FRONTEND))
				.andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")))
				.andExpect(header().string("Access-Control-Allow-Headers", containsString("authorization")))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
	}

	@Test
	void includesCorsHeaderOnProtectedUnauthorizedResponse() throws Exception {
		mockMvc.perform(get("/api/v1/integrations").header("Origin", LOCAL_FRONTEND))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Access-Control-Allow-Origin", LOCAL_FRONTEND));
	}

	@Test
	void rejectsUnconfiguredOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/integrations")
				.header("Origin", "https://untrusted.example")
				.header("Access-Control-Request-Method", "GET"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}
}
