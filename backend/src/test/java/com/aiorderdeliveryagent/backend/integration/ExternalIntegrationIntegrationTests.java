package com.aiorderdeliveryagent.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;

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
class ExternalIntegrationIntegrationTests {

	private static final String PASSWORD = "SecurePass!234";

	@DynamicPropertySource
	static void authenticationProperties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void authenticatedUserCreatesOwnedIntegration() throws Exception {
		Session user = createSession("integration.create@example.com");

		MvcResult result = createIntegration(user, "Provider.One", "Primary", "https://orders.example.com/api/")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.providerKey").value("provider.one"))
				.andExpect(jsonPath("$.displayName").value("Primary"))
				.andExpect(jsonPath("$.baseUrl").value("https://orders.example.com/api"))
				.andExpect(jsonPath("$.enabled").value(true))
				.andExpect(jsonPath("$.credentialConfigured").value(false))
				.andReturn();

		long integrationId = ((Number) JsonPath.read(
				result.getResponse().getContentAsString(), "$.id")).longValue();
		Map<String, Object> ownership = jdbcTemplate.queryForMap(
				"SELECT tenant_id, user_id, credential_reference FROM public.integrations WHERE id = ?",
				integrationId);
		assertThat(ownership.get("tenant_id")).isEqualTo(user.user().getTenantId());
		assertThat(((Number) ownership.get("user_id")).longValue()).isEqualTo(user.user().getId());
		assertThat(ownership.get("credential_reference")).isNull();
	}

	@Test
	void unauthenticatedUserCannotCreateIntegration() throws Exception {
		mockMvc.perform(post("/api/v1/integrations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody("provider", "Primary", "https://orders.example.com")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void listContainsOnlyAuthenticatedUsersIntegrations() throws Exception {
		Session userA = createSession("integration.list.a@example.com");
		Session userB = createSession("integration.list.b@example.com");
		long integrationA = createdId(createIntegration(
				userA, "provider-a", "Integration A", "https://a.example.com"));
		createIntegration(userB, "provider-b", "Integration B", "https://b.example.com")
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/integrations").header("Authorization", bearer(userA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(integrationA));
	}

	@Test
	void userCannotReadUpdateOrDeleteAnotherUsersIntegration() throws Exception {
		Session userA = createSession("integration.owner.a@example.com");
		Session userB = createSession("integration.owner.b@example.com");
		long integrationB = createdId(createIntegration(
				userB, "provider", "Owned by B", "https://b.example.com"));

		mockMvc.perform(get("/api/v1/integrations/{id}", integrationB)
				.header("Authorization", bearer(userA)))
				.andExpect(status().isForbidden());
		mockMvc.perform(patch("/api/v1/integrations/{id}", integrationB)
				.header("Authorization", bearer(userA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"displayName\":\"Changed\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/v1/integrations/{id}", integrationB)
				.header("Authorization", bearer(userA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void sameTenantUserCannotAccessAnotherUsersIntegration() throws Exception {
		Session userA = createSession("integration.same.a@example.com");
		UserAccount userB = register("integration.same.b@example.com");
		jdbcTemplate.update(
				"UPDATE public.users SET tenant_id = ? WHERE id = ?",
				userA.user().getTenantId(), userB.getId());
		entityManager.clear();
		Session sameTenantUserB = login(reload(userB.getId()));
		long integrationB = createdId(createIntegration(
				sameTenantUserB, "provider", "Owned by B", "https://b.example.com"));

		mockMvc.perform(get("/api/v1/integrations/{id}", integrationB)
				.header("Authorization", bearer(userA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void clientIdentityFieldsCannotOverrideAuthenticatedIdentity() throws Exception {
		Session user = createSession("integration.override@example.com");
		UUID suppliedTenant = UUID.randomUUID();
		long suppliedUser = user.user().getId() + 1000;

		MvcResult result = mockMvc.perform(post("/api/v1/integrations")
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "providerKey":"provider",
						  "displayName":"Identity test",
						  "baseUrl":"https://orders.example.com",
						  "enabled":true,
						  "tenant_id":"%s",
						  "user_id":%d
						}
						""".formatted(suppliedTenant, suppliedUser)))
				.andExpect(status().isCreated())
				.andReturn();

		long integrationId = ((Number) JsonPath.read(
				result.getResponse().getContentAsString(), "$.id")).longValue();
		Map<String, Object> ownership = jdbcTemplate.queryForMap(
				"SELECT tenant_id, user_id FROM public.integrations WHERE id = ?", integrationId);
		assertThat(ownership.get("tenant_id")).isEqualTo(user.user().getTenantId());
		assertThat(((Number) ownership.get("user_id")).longValue()).isEqualTo(user.user().getId());
	}

	@Test
	void invalidBaseUrlIsRejectedByApi() throws Exception {
		Session user = createSession("integration.url@example.com");

		createIntegration(user, "provider", "Unsafe", "https://127.0.0.1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_BASE_URL"));
	}

	@Test
	void safeResponsesNeverExposeCredentialReferenceOrMaterial() throws Exception {
		Session user = createSession("integration.response@example.com");
		long integrationId = createdId(createIntegration(
				user, "provider", "Safe response", "https://orders.example.com"));

		mockMvc.perform(get("/api/v1/integrations/{id}", integrationId)
				.header("Authorization", bearer(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.credentialReference").doesNotExist())
				.andExpect(jsonPath("$.credentialMaterial").doesNotExist())
				.andExpect(jsonPath("$.tenantId").doesNotExist())
				.andExpect(jsonPath("$.userId").doesNotExist());
	}

	@Test
	void createUpdateAndDeleteEmitSafeOwnedAuditEvents() throws Exception {
		Session user = createSession("integration.audit@example.com");
		long integrationId = createdId(createIntegration(
				user, "provider", "Audited", "https://orders.example.com"));

		mockMvc.perform(patch("/api/v1/integrations/{id}", integrationId)
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\":false}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.enabled").value(false));
		mockMvc.perform(delete("/api/v1/integrations/{id}", integrationId)
				.header("Authorization", bearer(user)))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		List<Map<String, Object>> events = jdbcTemplate.queryForList("""
				SELECT event_type, metadata::text AS metadata
				FROM public.audit_events
				WHERE tenant_id = ? AND actor_user_id = ? AND resource_id = ?
				ORDER BY id
				""", user.user().getTenantId(), user.user().getId(), Long.toString(integrationId));
		assertThat(events).extracting(event -> event.get("event_type"))
				.containsExactly("INTEGRATION_CREATED", "INTEGRATION_UPDATED", "INTEGRATION_DELETED");
		assertThat(events).allSatisfy(event -> assertThat(event.get("metadata").toString())
				.contains("requestId")
				.doesNotContain("credential")
				.doesNotContain("password")
				.doesNotContain("token"));
	}

	@Test
	void persistedCredentialReferenceIsAbsentFromResponsesLogsAndAuditMetadata() throws Exception {
		Session user = createSession("integration.reference.security@example.com");
		long id = createdId(createIntegration(user, "test-only", "Reference fixture", "https://example.com"));
		String reference = "synthetic-private-reference-only";
		jdbcTemplate.update("UPDATE integrations SET credential_reference=?, credential_type='test-only' WHERE id=?", reference, id);
		entityManager.clear();
		var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
		appender.start(); logger.addAppender(appender);
		try {
			String body = mockMvc.perform(get("/api/v1/integrations/{id}", id).header("Authorization", bearer(user)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.credentialConfigured").value(true))
				.andReturn().getResponse().getContentAsString();
			assertThat(body).doesNotContain(reference, "credentialReference", "credentialMaterial");
			mockMvc.perform(patch("/api/v1/integrations/{id}", id).header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
				.andExpect(status().isOk());
			assertThat(jdbcTemplate.queryForList("SELECT metadata::text FROM audit_events WHERE resource_type='integration' AND resource_id=?", String.class, Long.toString(id)))
				.allSatisfy(metadata -> assertThat(metadata).doesNotContain(reference, "credentialReference"));
			assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage()).doesNotContain(reference));
		}
		finally { logger.detachAppender(appender); appender.stop(); }
	}

	private org.springframework.test.web.servlet.ResultActions createIntegration(
			Session user,
			String providerKey,
			String displayName,
			String baseUrl) throws Exception {
		return mockMvc.perform(post("/api/v1/integrations")
				.header("Authorization", bearer(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(providerKey, displayName, baseUrl)));
	}

	private long createdId(org.springframework.test.web.servlet.ResultActions action) throws Exception {
		String response = action.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(response, "$.id")).longValue();
	}

	private String createBody(String providerKey, String displayName, String baseUrl) {
		return """
				{
				  "providerKey":"%s",
				  "displayName":"%s",
				  "baseUrl":"%s",
				  "enabled":true
				}
				""".formatted(providerKey, displayName, baseUrl);
	}

	private Session createSession(String email) throws Exception {
		return login(register(email));
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

	private UserAccount reload(long userId) {
		return userAccountRepository.findById(userId).orElseThrow();
	}

	private Session login(UserAccount user) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(user.getEmail(), PASSWORD)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return new Session(user, JsonPath.read(response, "$.accessToken"));
	}

	private String bearer(Session session) {
		return "Bearer " + session.accessToken();
	}

	private record Session(UserAccount user, String accessToken) {
	}
}
