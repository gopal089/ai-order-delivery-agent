package com.aiorderdeliveryagent.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.persistence.EntityManager;

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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@Transactional
@Import(TenantDataIsolationIntegrationTests.IsolationTestConfiguration.class)
class TenantDataIsolationIntegrationTests {

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
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Test
	void userCanAccessOwnTenantOwnedResources() throws Exception {
		UserResources owner = createUserResources("own.resources@example.com");

		assertAllowed(owner, "integrations", owner.integrationId());
		assertAllowed(owner, "orders", owner.orderId());
		assertAllowed(owner, "conversations", owner.conversationId());
		assertAllowed(owner, "messages", owner.messageId());
		assertAllowed(owner, "credentials", owner.integrationId());
	}

	@Test
	void userCannotAccessAnotherTenantsResource() throws Exception {
		UserResources userA = createUserResources("tenant.a@example.com");
		UserResources userB = createUserResources("tenant.b@example.com");

		assertDenied(userA, "integrations", userB.integrationId());
	}

	@Test
	void userCannotModifyAnotherUsersResource() throws Exception {
		UserResources userA = createUserResources("modify.a@example.com");
		UserResources userB = createUserResources("modify.b@example.com");

		assertModificationDenied(userA, "integrations", userB.integrationId());
		assertModificationDenied(userA, "orders", userB.orderId());
		assertModificationDenied(userA, "messages", userB.messageId());
	}

	@Test
	void userCannotPassDeleteAuthorizationForAnotherUsersResource() throws Exception {
		UserResources userA = createUserResources("delete.a@example.com");
		UserResources userB = createUserResources("delete.b@example.com");

		mockMvc.perform(delete("/api/v1/test/isolation/integrations/{id}", userB.integrationId())
				.header("Authorization", bearer(userA)))
				.andExpect(status().isForbidden());
	}

	@Test
	void userCannotAccessAnotherUsersIntegration() throws Exception {
		UserResources userA = createUserResources("integration.a@example.com");
		UserResources userB = createUserResources("integration.b@example.com");

		assertDenied(userA, "integrations", userB.integrationId());
	}

	@Test
	void userCannotAccessAnotherUsersOrder() throws Exception {
		UserResources userA = createUserResources("order.a@example.com");
		UserResources userB = createUserResources("order.b@example.com");

		assertDenied(userA, "orders", userB.orderId());
	}

	@Test
	void userCannotAccessAnotherUsersConversation() throws Exception {
		UserResources userA = createUserResources("conversation.a@example.com");
		UserResources userB = createUserResources("conversation.b@example.com");

		assertDenied(userA, "conversations", userB.conversationId());
	}

	@Test
	void userCannotAccessAnotherUsersMessage() throws Exception {
		UserResources userA = createUserResources("message.a@example.com");
		UserResources userB = createUserResources("message.b@example.com");

		assertDenied(userA, "messages", userB.messageId());
	}

	@Test
	void userCannotAccessAnotherUsersCredentialBoundary() throws Exception {
		UserResources userA = createUserResources("credential.a@example.com");
		UserResources userB = createUserResources("credential.b@example.com");

		assertDenied(userA, "credentials", userB.integrationId());
	}

	@Test
	void clientSuppliedTenantCannotOverrideAuthenticatedTenant() throws Exception {
		UserResources userA = createUserResources("tenant.override.a@example.com");
		UserResources userB = createUserResources("tenant.override.b@example.com");

		mockMvc.perform(get("/api/v1/test/isolation/integrations/{id}", userB.integrationId())
				.header("Authorization", bearer(userA))
				.param("tenant_id", userB.user().getTenantId().toString()))
				.andExpect(status().isForbidden());
	}

	@Test
	void clientSuppliedUserCannotOverrideAuthenticatedUser() throws Exception {
		UserResources userA = createUserResources("user.override.a@example.com");
		UserResources userB = createUserResources("user.override.b@example.com");

		mockMvc.perform(get("/api/v1/test/isolation/integrations/{id}", userB.integrationId())
				.header("Authorization", bearer(userA))
				.param("user_id", userB.user().getId().toString()))
				.andExpect(status().isForbidden());
	}

	@Test
	void sameTenantUserCannotAccessAnotherUsersResources() throws Exception {
		UserAccount userA = register("same.tenant.a@example.com");
		UserAccount originalUserB = register("same.tenant.b@example.com");
		jdbcTemplate.update(
				"UPDATE public.users SET tenant_id = ? WHERE id = ?",
				userA.getTenantId(),
				originalUserB.getId());
		entityManager.clear();

		UserResources resourcesA = createResourcesAndLogin(reload(userA.getId()));
		UserResources resourcesB = createResourcesAndLogin(reload(originalUserB.getId()));

		assertDenied(resourcesA, "integrations", resourcesB.integrationId());
		assertDenied(resourcesA, "orders", resourcesB.orderId());
		assertDenied(resourcesA, "conversations", resourcesB.conversationId());
		assertDenied(resourcesA, "messages", resourcesB.messageId());
		assertDenied(resourcesA, "credentials", resourcesB.integrationId());
	}

	private UserResources createUserResources(String email) throws Exception {
		return createResourcesAndLogin(register(email));
	}

	private UserAccount register(String email) {
		registrationService.register(new RegisterRequest(email, PASSWORD));
		return userAccountRepository.findByEmailIgnoreCase(email).orElseThrow();
	}

	private UserAccount reload(long userId) {
		return userAccountRepository.findById(userId).orElseThrow();
	}

	private UserResources createResourcesAndLogin(UserAccount user) throws Exception {
		long integrationId = jdbcTemplate.queryForObject("""
				INSERT INTO public.integrations
				    (tenant_id, user_id, provider_key, display_name)
				VALUES (?, ?, ?, ?)
				RETURNING id
				""", Long.class,
				user.getTenantId(), user.getId(), "test-provider", "Test integration " + UUID.randomUUID());
		long orderId = jdbcTemplate.queryForObject("""
				INSERT INTO public.orders
				    (tenant_id, user_id, integration_id, external_order_id)
				VALUES (?, ?, ?, ?)
				RETURNING id
				""", Long.class,
				user.getTenantId(), user.getId(), integrationId, "test-order-" + UUID.randomUUID());
		long conversationId = jdbcTemplate.queryForObject("""
				INSERT INTO public.conversations
				    (tenant_id, user_id, title)
				VALUES (?, ?, ?)
				RETURNING id
				""", Long.class,
				user.getTenantId(), user.getId(), "Test conversation");
		long messageId = jdbcTemplate.queryForObject("""
				INSERT INTO public.messages
				    (tenant_id, user_id, conversation_id, message_role, content)
				VALUES (?, ?, ?, ?, ?)
				RETURNING id
				""", Long.class,
				user.getTenantId(), user.getId(), conversationId, "user", "Non-secret test message");

		String accessToken = login(user.getEmail());
		return new UserResources(user, accessToken, integrationId, orderId, conversationId, messageId);
	}

	private String login(String email) throws Exception {
		String response = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, PASSWORD)))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(StandardCharsets.UTF_8);
		return JsonPath.read(response, "$.accessToken");
	}

	private void assertAllowed(UserResources user, String resourceType, long resourceId) throws Exception {
		mockMvc.perform(get("/api/v1/test/isolation/{resourceType}/{id}", resourceType, resourceId)
				.header("Authorization", bearer(user)))
				.andExpect(status().isNoContent());
	}

	private void assertDenied(UserResources user, String resourceType, long resourceId) throws Exception {
		mockMvc.perform(get("/api/v1/test/isolation/{resourceType}/{id}", resourceType, resourceId)
				.header("Authorization", bearer(user)))
				.andExpect(status().isForbidden());
	}

	private void assertModificationDenied(UserResources user, String resourceType, long resourceId)
			throws Exception {
		mockMvc.perform(put("/api/v1/test/isolation/{resourceType}/{id}", resourceType, resourceId)
				.header("Authorization", bearer(user)))
				.andExpect(status().isForbidden());
	}

	private String bearer(UserResources user) {
		return "Bearer " + user.accessToken();
	}

	private record UserResources(
			UserAccount user,
			String accessToken,
			long integrationId,
			long orderId,
			long conversationId,
			long messageId) {
	}

	@TestConfiguration
	static class IsolationTestConfiguration {
		@Bean
		IsolationTestController isolationTestController(TenantDataAuthorizationService authorizationService) {
			return new IsolationTestController(authorizationService);
		}
	}

	@RestController
	@RequestMapping("/api/v1/test/isolation")
	static class IsolationTestController {
		private final TenantDataAuthorizationService authorizationService;

		IsolationTestController(TenantDataAuthorizationService authorizationService) {
			this.authorizationService = authorizationService;
		}

		@GetMapping("/{resourceType}/{id}")
		ResponseEntity<Void> read(
				@PathVariable String resourceType,
				@PathVariable long id,
				@RequestParam(name = "tenant_id", required = false) UUID ignoredTenantId,
				@RequestParam(name = "user_id", required = false) Long ignoredUserId) {
			requireAccess(resourceType, id);
			return ResponseEntity.noContent().build();
		}

		@PutMapping("/{resourceType}/{id}")
		ResponseEntity<Void> modify(@PathVariable String resourceType, @PathVariable long id) {
			requireAccess(resourceType, id);
			return ResponseEntity.noContent().build();
		}

		@DeleteMapping("/integrations/{id}")
		ResponseEntity<Void> delete(@PathVariable long id) {
			authorizationService.requireIntegrationAccess(id);
			return ResponseEntity.noContent().build();
		}

		private void requireAccess(String resourceType, long id) {
			switch (resourceType) {
				case "integrations" -> authorizationService.requireIntegrationAccess(id);
				case "orders" -> authorizationService.requireOrderAccess(id);
				case "conversations" -> authorizationService.requireConversationAccess(id);
				case "messages" -> authorizationService.requireMessageAccess(id);
				case "credentials" -> authorizationService.requireCredentialAccess(id);
				default -> throw new IllegalArgumentException("Unsupported test resource");
			}
		}
	}
}
