package com.aiorderdeliveryagent.backend.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.aiorderdeliveryagent.backend.TestAuthProperties;
import com.aiorderdeliveryagent.backend.auth.UserAccount;
import com.aiorderdeliveryagent.backend.auth.UserAccountRepository;
import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
class SecurityAuditIntegrationTests {

	private static final String PASSWORD = "SecurePass!234";

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void successfulLoginCreatesOwnedAuditEvent() throws Exception {
		UserAccount user = register("audit.login.success@example.com");
		login(user.getEmail(), PASSWORD, "audit-login-success")
				.andExpect(status().isOk());

		Map<String, Object> event = eventByRequestId("audit-login-success", "AUTH_LOGIN_SUCCESS");
		assertThat(event.get("tenant_id")).isEqualTo(user.getTenantId());
		assertThat(((Number) event.get("actor_user_id")).longValue()).isEqualTo(user.getId());
		assertThat(event.get("outcome")).isEqualTo("SUCCESS");
	}

	@Test
	void failedLoginCreatesAnonymousGenericAuditEvent() throws Exception {
		login("unknown.audit@example.com", PASSWORD, "audit-login-failure")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));

		Map<String, Object> event = eventByRequestId("audit-login-failure", "AUTH_LOGIN_FAILURE");
		assertThat(event.get("tenant_id")).isNull();
		assertThat(event.get("actor_user_id")).isNull();
		assertThat(event.get("outcome")).isEqualTo("FAILURE");
	}

	@Test
	void successfulRefreshCreatesOwnedAuditEvent() throws Exception {
		UserAccount user = register("audit.refresh.success@example.com");
		TokenPair tokens = loginTokens(user.getEmail(), "audit-refresh-login");

		refresh(tokens.refreshToken(), "audit-refresh-success")
				.andExpect(status().isOk());

		Map<String, Object> event = eventByRequestId("audit-refresh-success", "AUTH_REFRESH_SUCCESS");
		assertThat(event.get("tenant_id")).isEqualTo(user.getTenantId());
		assertThat(((Number) event.get("actor_user_id")).longValue()).isEqualTo(user.getId());
	}

	@Test
	void failedRefreshCreatesAnonymousAuditEvent() throws Exception {
		refresh("Z".repeat(43), "audit-refresh-failure")
				.andExpect(status().isUnauthorized());

		Map<String, Object> event = eventByRequestId("audit-refresh-failure", "AUTH_REFRESH_FAILURE");
		assertThat(event.get("tenant_id")).isNull();
		assertThat(event.get("actor_user_id")).isNull();
		assertThat(event.get("outcome")).isEqualTo("FAILURE");
	}

	@Test
	void refreshTokenReuseCreatesDedicatedAuditEvent() throws Exception {
		UserAccount user = register("audit.refresh.reuse@example.com");
		TokenPair first = loginTokens(user.getEmail(), "audit-reuse-login");
		refresh(first.refreshToken(), "audit-reuse-rotate")
				.andExpect(status().isOk());

		refresh(first.refreshToken(), "audit-reuse-detected")
				.andExpect(status().isUnauthorized());

		Map<String, Object> event = eventByRequestId(
				"audit-reuse-detected", "AUTH_REFRESH_REUSE_DETECTED");
		assertThat(event.get("tenant_id")).isEqualTo(user.getTenantId());
		assertThat(((Number) event.get("actor_user_id")).longValue()).isEqualTo(user.getId());
		assertThat(event.get("outcome")).isEqualTo("BLOCKED");
	}

	@Test
	void logoutCreatesOwnedAuditEvent() throws Exception {
		UserAccount user = register("audit.logout@example.com");
		TokenPair tokens = loginTokens(user.getEmail(), "audit-logout-login");

		mockMvc.perform(post("/api/v1/auth/logout")
				.header(RequestContext.REQUEST_ID_HEADER, "audit-logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(tokens.refreshToken())))
				.andExpect(status().isNoContent());

		Map<String, Object> event = eventByRequestId("audit-logout", "AUTH_LOGOUT");
		assertThat(event.get("tenant_id")).isEqualTo(user.getTenantId());
		assertThat(((Number) event.get("actor_user_id")).longValue()).isEqualTo(user.getId());
	}

	@Test
	void auditMetadataNeverContainsAuthenticationSecrets() throws Exception {
		UserAccount user = register("audit.secrets@example.com");
		TokenPair first = loginTokens(user.getEmail(), "audit-secret-login");
		MvcResult refreshResult = refresh(first.refreshToken(), "audit-secret-refresh")
				.andExpect(status().isOk())
				.andReturn();
		TokenPair second = tokensFrom(refreshResult);

		List<String> metadata = jdbcTemplate.queryForList("""
				SELECT metadata::text
				FROM public.audit_events
				WHERE tenant_id = ? AND actor_user_id = ?
				""", String.class, user.getTenantId(), user.getId());
		assertThat(metadata).isNotEmpty();
		assertThat(metadata).allSatisfy(value -> assertThat(value)
				.doesNotContain(PASSWORD)
				.doesNotContain(first.accessToken())
				.doesNotContain(first.refreshToken())
				.doesNotContain(second.accessToken())
				.doesNotContain(second.refreshToken())
				.doesNotContainIgnoringCase("authorization")
				.doesNotContainIgnoringCase("credential"));
	}

	private UserAccount register(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
				.andExpect(status().isCreated());
		return userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
	}

	private org.springframework.test.web.servlet.ResultActions login(
			String email,
			String password,
			String requestId) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
				.header(RequestContext.REQUEST_ID_HEADER, requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)));
	}

	private TokenPair loginTokens(String email, String requestId) throws Exception {
		MvcResult result = login(email, PASSWORD, requestId)
				.andExpect(status().isOk())
				.andReturn();
		return tokensFrom(result);
	}

	private org.springframework.test.web.servlet.ResultActions refresh(
			String refreshToken,
			String requestId) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh")
				.header(RequestContext.REQUEST_ID_HEADER, requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(refreshBody(refreshToken)));
	}

	private String refreshBody(String refreshToken) {
		return """
				{"refreshToken":"%s"}
				""".formatted(refreshToken);
	}

	private TokenPair tokensFrom(MvcResult result) throws Exception {
		String response = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		return new TokenPair(JsonPath.read(response, "$.accessToken"), JsonPath.read(response, "$.refreshToken"));
	}

	private Map<String, Object> eventByRequestId(String requestId, String eventType) {
		return jdbcTemplate.queryForMap("""
				SELECT tenant_id, actor_user_id, event_type, outcome, metadata::text AS metadata
				FROM public.audit_events
				WHERE event_type = ? AND metadata ->> 'requestId' = ?
				""", eventType, requestId);
	}

	private record TokenPair(String accessToken, String refreshToken) {
	}
}
