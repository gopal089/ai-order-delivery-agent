package com.aiorderdeliveryagent.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiorderdeliveryagent.backend.TestAuthProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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
class UserRegistrationIntegrationTests {
	@DynamicPropertySource
	static void authenticationProperties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	private static final String VALID_PASSWORD = "SecurePass!234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Test
	void registersUserSuccessfully() throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"new.user@example.com","password":"SecurePass!234"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.email").value("new.user@example.com"))
				.andExpect(jsonPath("$.createdAt").isString())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void rejectsInvalidEmail() throws Exception {
		assertValidationError("""
				{"email":"not-an-email","password":"SecurePass!234"}
				""", "email");
	}

	@Test
	void rejectsMissingEmail() throws Exception {
		assertValidationError("""
				{"password":"SecurePass!234"}
				""", "email");
	}

	@Test
	void rejectsMissingPassword() throws Exception {
		assertValidationError("""
				{"email":"missing.password@example.com"}
				""", "password");
	}

	@Test
	void rejectsWeakPassword() throws Exception {
		assertValidationError("""
				{"email":"weak.password@example.com","password":"short"}
				""", "password");
	}

	@Test
	void rejectsDuplicateEmailIgnoringCase() throws Exception {
		register("duplicate.user@example.com", VALID_PASSWORD)
				.andExpect(status().isCreated());

		register("DUPLICATE.USER@example.com", VALID_PASSWORD)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.message").value("An account with this email is already registered"));
	}

	@Test
	void storesArgon2HashInsteadOfPlaintextPassword() throws Exception {
		String email = "hashed.password@example.com";

		register(email, VALID_PASSWORD)
				.andExpect(status().isCreated());

		UserAccount storedUser = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertThat(storedUser.getPasswordHash())
				.startsWith("$argon2id$")
				.isNotEqualTo(VALID_PASSWORD);
		assertThat(passwordEncoder.matches(VALID_PASSWORD, storedUser.getPasswordHash())).isTrue();
	}

	private org.springframework.test.web.servlet.ResultActions register(String email, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)));
	}

	private void assertValidationError(String requestBody, String field) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.%s".formatted(field)).isString())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}
}
