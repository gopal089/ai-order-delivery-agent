package com.aiorderdeliveryagent.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import com.aiorderdeliveryagent.backend.TestAuthProperties;
import com.aiorderdeliveryagent.backend.observability.RequestContext;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticationRateLimitingIntegrationTests {

	private static final String NAMESPACE = "test:auth:rate-limit:" + UUID.randomUUID();
	private static final String PASSWORD = "SecurePass!234";
	private static final String CLIENT_ADDRESS = "192.0.2.10";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestAuthProperties.registerSigningKey(registry);
		registry.add("auth.rate-limit.namespace", () -> NAMESPACE);
		registry.add("auth.rate-limit.login-identity-max-attempts", () -> "2");
		registry.add("auth.rate-limit.login-identity-window", () -> "PT1S");
		registry.add("auth.rate-limit.login-ip-max-attempts", () -> "5");
		registry.add("auth.rate-limit.login-ip-window", () -> "PT1M");
		registry.add("auth.rate-limit.refresh-session-max-attempts", () -> "2");
		registry.add("auth.rate-limit.refresh-session-window", () -> "PT1M");
		registry.add("auth.rate-limit.refresh-ip-max-attempts", () -> "5");
		registry.add("auth.rate-limit.refresh-ip-window", () -> "PT1M");
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRegistrationService registrationService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@AfterEach
	void clearRateLimitKeys() {
		Set<String> keys = redisTemplate.keys(NAMESPACE + ":*");
		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
		}
	}

	@Test
	void permitsLoginRequestsBelowTheLimit() throws Exception {
		register("below.limit@example.com");

		login("below.limit@example.com", PASSWORD, CLIENT_ADDRESS)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void blocksLoginAfterIdentityLimitWithGenericResponseAndRetryAfter() throws Exception {
		login("limited@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("LIMITED@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());

		login("Limited@example.com", "WrongPass!234", CLIENT_ADDRESS, "audit-login-rate-limited")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
				.andExpect(jsonPath("$.message").value("Too many authentication requests. Try again later."))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.tokenHash").doesNotExist());

		Integer auditCount = jdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM public.audit_events
				WHERE event_type = 'AUTH_RATE_LIMITED'
				  AND metadata ->> 'requestId' = 'audit-login-rate-limited'
				""", Integer.class);
		assertThat(auditCount).isEqualTo(1);
	}

	@Test
	void failedLoginAttemptsIncrementTheIdentityCounter() throws Exception {
		login("counter@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());

		Set<String> identityKeys = redisTemplate.keys(NAMESPACE + ":login:identity:*");
		assertThat(identityKeys).hasSize(1);
		assertThat(redisTemplate.opsForValue().get(identityKeys.iterator().next())).isEqualTo("1");
	}

	@Test
	void blocksPasswordSprayingAcrossDifferentIdentitiesAtTheIpLimit() throws Exception {
		for (int attempt = 1; attempt <= 5; attempt++) {
			login("spray-" + attempt + "@example.com", "WrongPass!234", CLIENT_ADDRESS)
					.andExpect(status().isUnauthorized());
		}

		login("spray-6@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
	}

	@Test
	void permitsLoginAgainAfterTheFixedWindowExpires() throws Exception {
		login("expiring@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("expiring@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("expiring@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isTooManyRequests());

		Thread.sleep(Duration.ofMillis(1_250));

		login("expiring@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void successfulLoginResetsOnlyTheIdentityFailureCounter() throws Exception {
		register("reset@example.com");
		login("reset@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("reset@example.com", PASSWORD, CLIENT_ADDRESS)
				.andExpect(status().isOk());

		login("reset@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("reset@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("reset@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isTooManyRequests());

		Set<String> ipKeys = redisTemplate.keys(NAMESPACE + ":login:ip:*");
		assertThat(ipKeys).hasSize(1);
		assertThat(redisTemplate.opsForValue().get(ipKeys.iterator().next())).isEqualTo("5");
	}

	@Test
	void separatesDifferentNormalizedIdentities() throws Exception {
		login("first@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("first@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
		login("second@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());

		login("first@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isTooManyRequests());
		login("second@example.com", "WrongPass!234", CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized());
	}

	@Test
	void limitsRefreshByStableSessionAcrossRotation() throws Exception {
		register("refresh.limit@example.com");
		TokenPair first = loginTokens("refresh.limit@example.com", CLIENT_ADDRESS);
		TokenPair second = refreshTokens(first.refreshToken(), CLIENT_ADDRESS);
		TokenPair third = refreshTokens(second.refreshToken(), CLIENT_ADDRESS);

		refresh(third.refreshToken(), CLIENT_ADDRESS)
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
	}

	@Test
	void limitsInvalidRefreshRequestsByIp() throws Exception {
		String unknownRefreshToken = "A".repeat(43);
		for (int attempt = 1; attempt <= 5; attempt++) {
			refresh(unknownRefreshToken, CLIENT_ADDRESS)
					.andExpect(status().isUnauthorized());
		}

		refresh(unknownRefreshToken, CLIENT_ADDRESS)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
	}

	@Test
	void rotatedTokenReuseDetectionStillReturnsGenericUnauthorized() throws Exception {
		register("reuse.limit@example.com");
		TokenPair first = loginTokens("reuse.limit@example.com", CLIENT_ADDRESS);
		refreshTokens(first.refreshToken(), CLIENT_ADDRESS);

		refresh(first.refreshToken(), CLIENT_ADDRESS)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"))
				.andExpect(jsonPath("$.message").value("Authentication failed"));
	}

	@Test
	void redisKeysDoNotContainRawIdentityPasswordOrRefreshToken() throws Exception {
		String email = "secret.key.check@example.com";
		String password = "VisibleOnlyInsideThisTest!234";
		register(email);
		TokenPair tokens = loginTokens(email, CLIENT_ADDRESS);
		login(email, password, "192.0.2.11").andExpect(status().isUnauthorized());
		refreshTokens(tokens.refreshToken(), "192.0.2.12");

		Set<String> keys = redisTemplate.keys(NAMESPACE + ":*");
		assertThat(keys).isNotEmpty();
		assertThat(keys).allSatisfy(key -> {
			assertThat(key)
					.doesNotContain(email)
					.doesNotContain(password)
					.doesNotContain(tokens.refreshToken());
			assertThat(key.substring(key.lastIndexOf(':') + 1)).matches("[0-9a-f]{64}");
		});
	}

	private void register(String email) {
		registrationService.register(new RegisterRequest(email, PASSWORD));
	}

	private org.springframework.test.web.servlet.ResultActions login(
			String email,
			String password,
			String clientAddress) throws Exception {
		return login(email, password, clientAddress, UUID.randomUUID().toString());
	}

	private org.springframework.test.web.servlet.ResultActions login(
			String email,
			String password,
			String clientAddress,
			String requestId) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.with(remoteAddress(clientAddress))
				.header(RequestContext.REQUEST_ID_HEADER, requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)));
	}

	private TokenPair loginTokens(String email, String clientAddress) throws Exception {
		MvcResult result = login(email, PASSWORD, clientAddress)
				.andExpect(status().isOk())
				.andReturn();
		return tokensFrom(result);
	}

	private TokenPair refreshTokens(String refreshToken, String clientAddress) throws Exception {
		MvcResult result = refresh(refreshToken, clientAddress)
				.andExpect(status().isOk())
				.andReturn();
		return tokensFrom(result);
	}

	private org.springframework.test.web.servlet.ResultActions refresh(
			String refreshToken,
			String clientAddress) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh")
				.with(remoteAddress(clientAddress))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(refreshToken)));
	}

	private RequestPostProcessor remoteAddress(String clientAddress) {
		return request -> {
			request.setRemoteAddr(clientAddress);
			return request;
		};
	}

	private TokenPair tokensFrom(MvcResult result) throws Exception {
		String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return new TokenPair(JsonPath.read(body, "$.accessToken"), JsonPath.read(body, "$.refreshToken"));
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
