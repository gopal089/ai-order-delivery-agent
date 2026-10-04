package com.aiorderdeliveryagent.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.aiorderdeliveryagent.backend.TestAuthProperties;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@Transactional
class UserAuthenticationIntegrationTests {
	@DynamicPropertySource
	static void authenticationProperties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	private static final String PASSWORD = "SecurePass!234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRegistrationService registrationService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private Clock clock;

	@Test
	void logsInWithNormalizedEmailAndValidPassword() throws Exception {
		register("login.user@example.com");

		login("LOGIN.USER@example.com", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tokenType").value("Bearer"));
	}

	@Test
	void unknownEmailReturnsGenericAuthenticationFailure() throws Exception {
		assertGenericAuthenticationFailure(login("unknown@example.com", PASSWORD));
	}

	@Test
	void incorrectPasswordReturnsGenericAuthenticationFailure() throws Exception {
		register("wrong.password@example.com");

		assertGenericAuthenticationFailure(login("wrong.password@example.com", "WrongPass!234"));
	}

	@Test
	void invalidAndMissingLoginInputIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"not-an-email"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors.email").isString())
				.andExpect(jsonPath("$.fieldErrors.password").isString());
	}

	@Test
	void inactiveUserReturnsGenericAuthenticationFailure() throws Exception {
		register("inactive@example.com");
		UserAccount user = userAccountRepository.findByEmailIgnoreCase("inactive@example.com").orElseThrow();
		user.deactivate(clock.instant());
		userAccountRepository.saveAndFlush(user);

		assertGenericAuthenticationFailure(login("inactive@example.com", PASSWORD));
	}

	@Test
	void issuesShortLivedSignedAccessToken() throws Exception {
		UserAccount user = register("access.token@example.com");
		TokenPair tokens = loginTokens("access.token@example.com");

		Jwt jwt = jwtDecoder.decode(tokens.accessToken());
		assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
		assertThat(jwt.getClaimAsString("sid")).isNotBlank();
		assertThat(jwt.getExpiresAt()).isAfter(jwt.getIssuedAt());
		assertThat(jwt.getExpiresAt().getEpochSecond() - jwt.getIssuedAt().getEpochSecond()).isEqualTo(900);
	}

	@Test
	void issuesOpaqueRefreshTokenAndStoresOnlyItsHash() throws Exception {
		register("refresh.token@example.com");
		TokenPair tokens = loginTokens("refresh.token@example.com");

		assertThat(tokens.refreshToken()).hasSize(43);
		String expectedHash = tokenService.hashRefreshToken(tokens.refreshToken());
		RefreshToken storedToken = refreshTokenRepository.findByTokenHashForUpdate(expectedHash).orElseThrow();
		assertThat(storedToken.getTokenHash())
				.isEqualTo(expectedHash)
				.isNotEqualTo(tokens.refreshToken());
		assertThat(refreshTokenRepository.findByTokenHashForUpdate(expectedHash)).isPresent();
	}

	@Test
	void rotatesRefreshTokenAndRevokesPreviousToken() throws Exception {
		register("rotation@example.com");
		TokenPair first = loginTokens("rotation@example.com");
		TokenPair second = refreshTokens(first.refreshToken());

		assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
		RefreshToken oldToken = findStored(first.refreshToken());
		RefreshToken newToken = findStored(second.refreshToken());
		assertThat(oldToken.isRevoked()).isTrue();
		assertThat(newToken.isRevoked()).isFalse();
		assertThat(newToken.getSessionId()).isEqualTo(oldToken.getSessionId());
	}

	@Test
	void expiredRefreshTokenIsRejectedAndRevoked() throws Exception {
		UserAccount user = register("expired@example.com");
		String rawToken = "A".repeat(43);
		RefreshToken expired = new RefreshToken(
				user.getTenantId(),
				user.getId(),
				UUID.randomUUID(),
				tokenService.hashRefreshToken(rawToken),
				clock.instant().minus(1, ChronoUnit.SECONDS),
				clock.instant().minus(31, ChronoUnit.DAYS));
		refreshTokenRepository.saveAndFlush(expired);

		assertGenericAuthenticationFailure(refresh(rawToken));
		assertThat(findStored(rawToken).isRevoked()).isTrue();
	}

	@Test
	void revokedRefreshTokenIsRejected() throws Exception {
		UserAccount user = register("revoked@example.com");
		String rawToken = "B".repeat(43);
		Instant now = clock.instant();
		RefreshToken revoked = new RefreshToken(
				user.getTenantId(),
				user.getId(),
				UUID.randomUUID(),
				tokenService.hashRefreshToken(rawToken),
				now.plus(1, ChronoUnit.DAYS),
				now);
		revoked.revoke(now);
		refreshTokenRepository.saveAndFlush(revoked);

		assertGenericAuthenticationFailure(refresh(rawToken));
	}

	@Test
	void refreshTokenReuseRevokesTheRotatedSession() throws Exception {
		register("reuse@example.com");
		TokenPair first = loginTokens("reuse@example.com");
		TokenPair second = refreshTokens(first.refreshToken());

		assertGenericAuthenticationFailure(refresh(first.refreshToken()));
		assertGenericAuthenticationFailure(refresh(second.refreshToken()));
		assertThat(findStored(second.refreshToken()).isRevoked()).isTrue();
	}

	@Test
	void logoutRevokesSessionAndIsIdempotent() throws Exception {
		register("logout@example.com");
		TokenPair tokens = loginTokens("logout@example.com");

		logout(tokens.refreshToken()).andExpect(status().isNoContent());
		logout(tokens.refreshToken()).andExpect(status().isNoContent());
		assertGenericAuthenticationFailure(refresh(tokens.refreshToken()));
		assertThat(findStored(tokens.refreshToken()).isRevoked()).isTrue();
	}

	private UserAccount register(String email) {
		registrationService.register(new RegisterRequest(email, PASSWORD));
		return userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
	}

	private org.springframework.test.web.servlet.ResultActions login(String email, String password)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)));
	}

	private TokenPair loginTokens(String email) throws Exception {
		MvcResult result = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
		return tokensFrom(result);
	}

	private TokenPair refreshTokens(String refreshToken) throws Exception {
		MvcResult result = refresh(refreshToken).andExpect(status().isOk()).andReturn();
		return tokensFrom(result);
	}

	private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(refreshToken)));
	}

	private org.springframework.test.web.servlet.ResultActions logout(String refreshToken)
			throws Exception {
		return mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(refreshToken)));
	}

	private TokenPair tokensFrom(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return new TokenPair(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
	}

	private RefreshToken findStored(String rawToken) {
		return refreshTokenRepository.findByTokenHashForUpdate(tokenService.hashRefreshToken(rawToken)).orElseThrow();
	}

	private void assertGenericAuthenticationFailure(
			org.springframework.test.web.servlet.ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Authentication failed"))
				.andExpect(jsonPath("$.fieldErrors").isEmpty())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist())
				.andExpect(jsonPath("$.tokenHash").doesNotExist());
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
