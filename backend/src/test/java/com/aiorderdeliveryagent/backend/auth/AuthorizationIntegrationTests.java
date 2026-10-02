package com.aiorderdeliveryagent.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.aiorderdeliveryagent.backend.TestAuthProperties;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithms;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@Transactional
@Import(AuthorizationIntegrationTests.ProtectedTestConfiguration.class)
class AuthorizationIntegrationTests {

	private static final String PASSWORD = "SecurePass!234";

	@DynamicPropertySource
	static void authenticationProperties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRegistrationService registrationService;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Test
	void validAccessTokenAuthenticatesProtectedRequest() throws Exception {
		Session session = createSession("valid.access@example.com");

		context(session.accessToken()).andExpect(status().isOk());
	}

	@Test
	void missingAccessTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/test/authorization/context"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void malformedAccessTokenReturnsUnauthorized() throws Exception {
		context("not-a-jwt").andExpect(status().isUnauthorized());
	}

	@Test
	void expiredAccessTokenReturnsUnauthorized() throws Exception {
		Session session = createSession("expired.access@example.com");
		String expired = customToken(
				jwtEncoder,
				session.user().getId(),
				session.sessionId(),
				AuthTokenProperties.ACCESS_TOKEN_ISSUER,
				Instant.now().minus(2, ChronoUnit.HOURS),
				Instant.now().minus(1, ChronoUnit.HOURS),
				true);

		context(expired).andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenWithInvalidSignatureReturnsUnauthorized() throws Exception {
		Session session = createSession("signature@example.com");
		SecretKey otherKey = new SecretKeySpec(
				"different-test-signing-key-material".getBytes(java.nio.charset.StandardCharsets.US_ASCII),
				"HmacSHA256");
		JwtEncoder otherEncoder = NimbusJwtEncoder.withSecretKey(otherKey).build();
		String invalidSignature = customToken(
				otherEncoder,
				session.user().getId(),
				session.sessionId(),
				AuthTokenProperties.ACCESS_TOKEN_ISSUER,
				Instant.now(),
				Instant.now().plus(15, ChronoUnit.MINUTES),
				true);

		context(invalidSignature).andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenWithInvalidIssuerReturnsUnauthorized() throws Exception {
		Session session = createSession("issuer@example.com");
		String invalidIssuer = customToken(
				jwtEncoder,
				session.user().getId(),
				session.sessionId(),
				"untrusted-issuer",
				Instant.now(),
				Instant.now().plus(15, ChronoUnit.MINUTES),
				true);

		context(invalidIssuer).andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenMissingRequiredSessionClaimReturnsUnauthorized() throws Exception {
		Session session = createSession("missing.claim@example.com");
		String missingSession = customToken(
				jwtEncoder,
				session.user().getId(),
				session.sessionId(),
				AuthTokenProperties.ACCESS_TOKEN_ISSUER,
				Instant.now(),
				Instant.now().plus(15, ChronoUnit.MINUTES),
				false);

		context(missingSession).andExpect(status().isUnauthorized());
	}

	@Test
	void accessTokenMissingRequiredSubjectReturnsUnauthorized() throws Exception {
		Session session = createSession("missing.subject@example.com");
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(AuthTokenProperties.ACCESS_TOKEN_ISSUER)
				.issuedAt(Instant.now())
				.expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
				.claim("sid", session.sessionId().toString())
				.build();
		String missingSubject = jwtEncoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(() -> JwsAlgorithms.HS256).build(),
				claims)).getTokenValue();

		context(missingSubject).andExpect(status().isUnauthorized());
	}

	@Test
	void authenticatedContextContainsServerResolvedIdentity() throws Exception {
		Session session = createSession("context@example.com");

		context(session.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(session.user().getId()))
				.andExpect(jsonPath("$.tenantId").value(session.user().getTenantId().toString()))
				.andExpect(jsonPath("$.sessionId").value(session.sessionId().toString()))
				.andExpect(jsonPath("$.authenticated").value(true));
	}

	@Test
	void tenantContextComesFromServerSideUserRecord() throws Exception {
		Session session = createSession("tenant.context@example.com");

		context(session.accessToken())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value(session.user().getTenantId().toString()));
	}

	@Test
	void clientSuppliedTenantCannotOverrideAuthenticatedTenant() throws Exception {
		Session session = createSession("tenant.override@example.com");
		UUID attemptedTenant = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/test/authorization/context")
				.header("Authorization", "Bearer " + session.accessToken())
				.param("tenantId", attemptedTenant.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenantId").value(session.user().getTenantId().toString()))
				.andExpect(jsonPath("$.tenantId").value(org.hamcrest.Matchers.not(attemptedTenant.toString())));
	}

	@Test
	void clientSuppliedUserCannotOverrideAuthenticatedUser() throws Exception {
		Session session = createSession("user.override@example.com");
		long attemptedUserId = session.user().getId() + 1000;

		mockMvc.perform(get("/api/v1/test/authorization/context")
				.header("Authorization", "Bearer " + session.accessToken())
				.param("userId", Long.toString(attemptedUserId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(session.user().getId()))
				.andExpect(jsonPath("$.userId").value(org.hamcrest.Matchers.not(attemptedUserId)));
	}

	@Test
	void unknownSessionClaimCannotImpersonateSession() throws Exception {
		Session session = createSession("session.override@example.com");
		String unknownSession = customToken(
				jwtEncoder,
				session.user().getId(),
				UUID.randomUUID(),
				AuthTokenProperties.ACCESS_TOKEN_ISSUER,
				Instant.now(),
				Instant.now().plus(15, ChronoUnit.MINUTES),
				true);

		context(unknownSession).andExpect(status().isUnauthorized());
	}

	@Test
	void userSubjectCannotReuseAnotherUsersSession() throws Exception {
		Session firstUser = createSession("first.subject@example.com");
		Session secondUser = createSession("second.subject@example.com");
		String mismatchedIdentity = customToken(
				jwtEncoder,
				secondUser.user().getId(),
				firstUser.sessionId(),
				AuthTokenProperties.ACCESS_TOKEN_ISSUER,
				Instant.now(),
				Instant.now().plus(15, ChronoUnit.MINUTES),
				true);

		context(mismatchedIdentity).andExpect(status().isUnauthorized());
	}

	@Test
	void crossTenantOwnerAccessIsRejected() throws Exception {
		Session requester = createSession("requester@example.com");
		Session otherTenant = createSession("other.tenant@example.com");

		mockMvc.perform(get(
				"/api/v1/test/authorization/resources/{tenantId}/{userId}",
				otherTenant.user().getTenantId(),
				otherTenant.user().getId())
				.header("Authorization", "Bearer " + requester.accessToken()))
				.andExpect(status().isForbidden());
	}

	@Test
	void revokedSessionInvalidatesExistingAccessToken() throws Exception {
		Session session = createSession("revoked.session@example.com");

		mockMvc.perform(post("/api/v1/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"refreshToken":"%s"}
						""".formatted(session.refreshToken())))
				.andExpect(status().isNoContent());

		context(session.accessToken()).andExpect(status().isUnauthorized());
	}

	private Session createSession(String email) throws Exception {
		registrationService.register(new RegisterRequest(email, PASSWORD));
		UserAccount user = userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn();
		String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		String accessToken = JsonPath.read(body, "$.accessToken");
		String refreshToken = JsonPath.read(body, "$.refreshToken");
		Jwt jwt = jwtDecoder.decode(accessToken);
		return new Session(user, UUID.fromString(jwt.getClaimAsString("sid")), accessToken, refreshToken);
	}

	private org.springframework.test.web.servlet.ResultActions context(String accessToken)
			throws Exception {
		return mockMvc.perform(get("/api/v1/test/authorization/context")
				.header("Authorization", "Bearer " + accessToken));
	}

	private String customToken(
			JwtEncoder encoder,
			long userId,
			UUID sessionId,
			String issuer,
			Instant issuedAt,
			Instant expiresAt,
			boolean includeSession) {
		JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(Long.toString(userId))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt);
		if (includeSession) {
			claims.claim("sid", sessionId.toString());
		}
		return encoder.encode(JwtEncoderParameters.from(
				JwsHeader.with(() -> JwsAlgorithms.HS256).build(),
				claims.build())).getTokenValue();
	}

	private record Session(
			UserAccount user,
			UUID sessionId,
			String accessToken,
			String refreshToken) {
	}

	@TestConfiguration
	static class ProtectedTestConfiguration {
		@Bean
		ProtectedTestController protectedTestController(
				AuthenticatedUserContextProvider contextProvider,
				TenantAuthorization tenantAuthorization) {
			return new ProtectedTestController(contextProvider, tenantAuthorization);
		}
	}

	@RestController
	@RequestMapping("/api/v1/test/authorization")
	static class ProtectedTestController {
		private final AuthenticatedUserContextProvider contextProvider;
		private final TenantAuthorization tenantAuthorization;

		ProtectedTestController(
				AuthenticatedUserContextProvider contextProvider,
				TenantAuthorization tenantAuthorization) {
			this.contextProvider = contextProvider;
			this.tenantAuthorization = tenantAuthorization;
		}

		@GetMapping("/context")
		AuthenticatedUserContext context(
				@RequestParam(required = false) UUID tenantId,
				@RequestParam(required = false) Long userId,
				@RequestParam(required = false) UUID sessionId) {
			return contextProvider.getCurrentUser();
		}

		@GetMapping("/resources/{tenantId}/{userId}")
		void resource(@PathVariable UUID tenantId, @PathVariable long userId) {
			tenantAuthorization.requireOwner(tenantId, userId);
		}
	}
}
