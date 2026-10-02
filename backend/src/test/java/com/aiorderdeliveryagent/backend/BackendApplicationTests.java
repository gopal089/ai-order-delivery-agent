package com.aiorderdeliveryagent.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
class BackendApplicationTests {
	@DynamicPropertySource
	static void authenticationProperties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	@Test
	void contextLoads() {
	}

}
