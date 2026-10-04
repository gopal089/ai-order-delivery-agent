package com.aiorderdeliveryagent.backend.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.aiorderdeliveryagent.backend.TestAuthProperties;
import com.aiorderdeliveryagent.backend.auth.*;
import com.aiorderdeliveryagent.backend.integration.credential.*;
import com.aiorderdeliveryagent.backend.integration.order.*;
import com.aiorderdeliveryagent.backend.integration.order.exception.*;
import com.aiorderdeliveryagent.backend.integration.order.model.*;
import com.aiorderdeliveryagent.backend.integration.order.tool.*;
import com.aiorderdeliveryagent.backend.domain.order.*;
import com.aiorderdeliveryagent.backend.domain.order.OrderDomainData.Authority;
import com.aiorderdeliveryagent.backend.ai.*;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Propagation;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import com.aiorderdeliveryagent.backend.integration.transport.SecureHttpTransport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@Transactional
@Import(ExternalOrderExecutionIntegrationTests.ApiFixtureConfiguration.class)
class ExternalOrderExecutionIntegrationTests {
	@TestConfiguration(proxyBeanMethods=false) static class ApiFixtureConfiguration {
		@Bean CredentialStore apiFixtureStore() {
			return new CredentialStore() {
				public CredentialReference store(CredentialScope s,CredentialMaterial m){throw new UnsupportedOperationException();}
				public CredentialMaterial retrieve(CredentialScope s,CredentialReference r){return CredentialMaterial.copyOf(new char[]{'x'});}
				public void delete(CredentialScope s,CredentialReference r){throw new UnsupportedOperationException();}
			};
		}
		@Bean BackendOrderProviderAdapter apiFixtureAdapter() {
			return new BackendOrderProviderAdapter() {
				public String providerKey(){return "test-only";}
				public ExternalOrderProvider open(java.net.URI u,CredentialMaterial m,SecureHttpTransport t){return new DeterministicTestOrderProvider(DeterministicTestOrderProvider.Scenario.SUCCESS);}
			};
		}
	}
	@DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { TestAuthProperties.register(registry); }
	@Autowired MockMvc mvc;
	@Autowired AuthenticatedUserContextProvider contexts;
	@Autowired TenantDataAuthorizationService authorization;
	@Autowired ExternalIntegrationRepository integrations;
	@Autowired ExternalBaseUrlValidator urls;
	@Autowired SecureHttpTransport transport;
	@Autowired JdbcTemplate jdbc;
	@Autowired jakarta.persistence.EntityManager entityManager;
	@Autowired JwtDecoder decoder;
	@Autowired AccessTokenAuthenticationConverter converter;
	@Autowired tools.jackson.databind.ObjectMapper json;
	@Autowired ControlledOrderToolFactory toolFactory;
	private String lastAccessToken;
	private final AtomicInteger retrievals = new AtomicInteger();
	private final AtomicInteger opens = new AtomicInteger();
	private final AtomicReference<CredentialScope> retrievedScope = new AtomicReference<>();
	private final AtomicReference<CredentialMaterial> retrievedMaterial = new AtomicReference<>();
	private final AtomicReference<ExternalOrderProviderContext> receivedContext = new AtomicReference<>();
	private final ExternalOrderProvider provider = mock(ExternalOrderProvider.class);
	private final CredentialStore store = new CredentialStore() {
		public CredentialReference store(CredentialScope scope, CredentialMaterial material) { throw new UnsupportedOperationException(); }
		public CredentialMaterial retrieve(CredentialScope scope, CredentialReference reference) {
			retrievals.incrementAndGet(); retrievedScope.set(scope);
			CredentialMaterial material = CredentialMaterial.copyOf("synthetic-test-only".toCharArray());
			retrievedMaterial.set(material); return material;
		}
		public void delete(CredentialScope scope, CredentialReference reference) { throw new UnsupportedOperationException(); }
	};
	private final BackendOrderProviderAdapter adapter = new BackendOrderProviderAdapter() {
		public String providerKey() { return "test-only"; }
		public ExternalOrderProvider open(java.net.URI base, CredentialMaterial material, SecureHttpTransport transport) {
			opens.incrementAndGet(); return provider;
		}
	};
	@AfterEach void clear() { SecurityContextHolder.clearContext(); }

	@SuppressWarnings("unchecked")
	private ExternalOrderExecutionService service(CredentialStore available, List<BackendOrderProviderAdapter> adapters) {
		ObjectProvider<CredentialStore> stores = mock(ObjectProvider.class);
		when(stores.getIfAvailable()).thenReturn(available);
		return new ExternalOrderExecutionService(contexts, authorization, integrations, urls, stores, adapters, transport);
	}
	private ExternalOrderExecutionService service() { return service(store, List.of(adapter)); }
	private ExternalOrderExecutionService deterministic(DeterministicTestOrderProvider.Scenario scenario) {
		return service(store, List.of(new BackendOrderProviderAdapter() {
			public String providerKey() { return "test-only"; }
			public ExternalOrderProvider open(java.net.URI base, CredentialMaterial material, SecureHttpTransport transport) {
				opens.incrementAndGet(); return new DeterministicTestOrderProvider(scenario);
			}
		}));
	}
	private ControlledOrderTools tools(long integrationId, DeterministicTestOrderProvider.Scenario scenario) {
		return new ControlledOrderToolFactory(contexts, authorization, deterministic(scenario), jdbc).bind(integrationId);
	}
	private long order(AuthenticatedUserContext owner, long integrationId) {
		return jdbc.queryForObject("""
			INSERT INTO orders (tenant_id,user_id,integration_id,external_order_id)
			VALUES (?,?,?,'fixture-order') RETURNING id
			""", Long.class, owner.tenantId(), owner.userId(), integrationId);
	}
	private OrderDomainService orderDomain() { return new OrderDomainService(contexts, authorization, jdbc); }
	private ShipmentTrackingService tracking(DeterministicTestOrderProvider.Scenario scenario) {
		return new ShipmentTrackingService(contexts, authorization, orderDomain(), deterministic(scenario), jdbc, java.time.Clock.systemUTC());
	}
	private long shipment(AuthenticatedUserContext owner, long orderId, boolean externalId) {
		return jdbc.queryForObject("""
			INSERT INTO shipments (tenant_id,user_id,order_id,external_shipment_id,shipment_status)
			VALUES (?,?,?,?,'stale-persisted-status') RETURNING id
			""", Long.class, owner.tenantId(), owner.userId(), orderId, externalId ? "fixture-shipment" : null);
	}
	private long event(AuthenticatedUserContext owner, long shipmentId) {
		return jdbc.queryForObject("""
			INSERT INTO tracking_events (tenant_id,user_id,shipment_id,event_status,event_location,occurred_at)
			VALUES (?,?,?,'stale-event-status','stale-event-location','2020-01-01T00:00:00Z') RETURNING id
			""", Long.class, owner.tenantId(), owner.userId(), shipmentId);
	}
	@Test void domainListsOnlyOwnedPersistedOrdersWithInternalIdsAndPagination() throws Exception {
		var foreign = login(); long foreignOrder = order(foreign, integration(foreign, true, true));
		var owner = login(); long integrationId = integration(owner, true, true);
		long first = order(owner, integrationId);
		jdbc.update("UPDATE orders SET external_order_id='second-fixture' WHERE id=?", first);
		long second = order(owner, integrationId);
		var page = orderDomain().listCustomerOrders(0, 1);
		assertThat(page.orders()).extracting(OrderDomainData.Order::id).containsExactly(first).doesNotContain(foreignOrder);
		assertThat(page.nextCursor()).contains(first);
		assertThat(orderDomain().listCustomerOrders(first, 1).orders()).extracting(OrderDomainData.Order::id).containsExactly(second);
		assertThat(orderDomain().getOrder(first).provenance().authority()).isEqualTo(Authority.PERSISTED_APPLICATION);
		assertThat(orderDomain().resolveForProvider(second).externalOrderId()).isEqualTo(DeterministicTestOrderProvider.ORDER);
		assertThat(retrievals.get()).isZero();
	}
	@Test void orderDomainRejectsInvalidPaginationAndAnonymousAccess() {
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> orderDomain().listCustomerOrders()).isInstanceOf(InsufficientAuthenticationException.class);
		assertThatThrownBy(() -> orderDomain().getOrder(1)).isInstanceOf(InsufficientAuthenticationException.class);
	}
	@Test void domainPaginationIsBoundedAndProjectionsExcludeRawPayloadsAndProviderReferences() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, true);
		assertThatThrownBy(() -> orderDomain().listCustomerOrders(0, 101)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> orderDomain().listCustomerOrders(-1, 10)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> orderDomain().listCustomerOrders(0, 0)).isInstanceOf(IllegalArgumentException.class);
		staleMemory(owner, orderId, shipmentId);
		String stored = json.writeValueAsString(orderDomain().getOrder(orderId));
		String current = json.writeValueAsString(tracking(DeterministicTestOrderProvider.Scenario.SUCCESS).getCurrentPackageLocation(shipmentId));
		for (String output : List.of(stored, current)) assertThat(output).doesNotContain("cached_payload", "cachedPayload", "sourcePayload",
			"credential", "tenantId", "userId", "externalOrderId", "externalShipmentId", "old-cache-location");
	}
	@Test void ownedShipmentAndTrackingEventArePersistedApplicationState() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true));
		long shipmentId = shipment(owner, orderId, true); long eventId = event(owner, shipmentId);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThat(domain.getShipment(shipmentId).orderId()).isEqualTo(orderId);
		assertThat(domain.getShipment(shipmentId).provenance().authority()).isEqualTo(Authority.PERSISTED_APPLICATION);
		assertThat(domain.getPersistedTrackingEvent(eventId).location()).contains("stale-event-location");
		assertThat(domain.getPersistedTrackingEvent(eventId).provenance().authority()).isEqualTo(Authority.PERSISTED_APPLICATION);
		assertThat(retrievals.get()).isZero();
	}
	@Test void shipmentAndEventDenialsAreIdenticalForForeignAndMissingResources() throws Exception {
		var foreign = login(); long orderId = order(foreign, integration(foreign, true, true));
		long shipmentId = shipment(foreign, orderId, true); long eventId = event(foreign, shipmentId);
		login(); var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		for (long id : new long[] {shipmentId, Long.MAX_VALUE}) {
			assertThatThrownBy(() -> domain.getShipment(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
			assertThatThrownBy(() -> domain.getCurrentPackageLocation(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
			assertThatThrownBy(() -> domain.getCurrentShipmentStatus(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
			assertThatThrownBy(() -> domain.getTrackingHistory(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
		}
		for (long id : new long[] {eventId, Long.MAX_VALUE}) {
			assertThatThrownBy(() -> domain.getPersistedTrackingEvent(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
		}
		for (long id : new long[] {orderId, Long.MAX_VALUE}) {
			assertThatThrownBy(() -> orderDomain().getOrder(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
			assertThatThrownBy(() -> orderDomain().resolveForProvider(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
		}
		assertThat(retrievals.get()).isZero();
	}
	@Test void sameTenantDifferentUserCannotReadOrderShipmentOrEvent() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true));
		long shipmentId = shipment(owner, orderId, true); long eventId = event(owner, shipmentId);
		login(owner.tenantId()); var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThat(orderDomain().listCustomerOrders().orders()).isEmpty();
		assertThatThrownBy(() -> orderDomain().getOrder(orderId)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> domain.getShipment(shipmentId)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> domain.getPersistedTrackingEvent(eventId)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(() -> domain.getTrackingHistory(shipmentId)).isInstanceOf(AccessDeniedException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void actualSchemaUsesUserTenantColumnNotATenantTableAndHasRequiredForeignKeys() throws Exception {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='tenants'", Integer.class)).isZero();
		var keys = jdbc.queryForList("""
			SELECT conname FROM pg_constraint WHERE contype='f'
			AND conname IN ('orders_integration_fk','shipments_order_fk','tracking_events_shipment_fk')
			""", String.class);
		assertThat(keys).containsExactlyInAnyOrder("orders_integration_fk", "shipments_order_fk", "tracking_events_shipment_fk");
	}
	@Test void compositeForeignKeysRejectCrossOwnerOrderShipmentAndEventLinks() throws Exception {
		var foreign = login(); long foreignIntegration = integration(foreign, true, true);
		long foreignOrder = order(foreign, foreignIntegration); long foreignShipment = shipment(foreign, foreignOrder, true);
		var owner = login();
		jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
			for (Runnable insert : List.<Runnable>of(() -> order(owner, foreignIntegration),
				() -> shipment(owner, foreignOrder, true), () -> event(owner, foreignShipment))) {
				var savepoint = connection.setSavepoint();
				try { assertThatThrownBy(insert::run).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
				finally { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
			}
			return null;
		});
	}
	@Test void currentTrackingStatusAndLocationUseProviderAndServerResolvedShipment() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, true);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThat(domain.getTrackingHistory(shipmentId).events()).hasSize(1);
		assertThat(domain.getCurrentShipmentStatus(shipmentId).status()).contains("fixture-status");
		var location = domain.getCurrentPackageLocation(shipmentId);
		assertThat(location.location()).contains("fixture-location");
		assertThat(location.provenance().authority()).isEqualTo(Authority.EXTERNAL_PROVIDER_RESPONSE);
		assertThat(location.provenance().sourceObservedAt()).contains(DeterministicTestOrderProvider.TIME);
		assertThat(location.provenance().retrievedAt()).isPresent();
		assertThat(retrievedScope.get().userId()).isEqualTo(owner.userId());
		assertThat(retrievals.get()).isEqualTo(3);
	}
	@Test void currentCallsReFetchRatherThanCacheProviderResults() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, true);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		domain.getCurrentShipmentStatus(shipmentId); domain.getCurrentShipmentStatus(shipmentId);
		domain.getCurrentPackageLocation(shipmentId); domain.getCurrentPackageLocation(shipmentId);
		assertThat(retrievals.get()).isEqualTo(4);
	}
	@Test void trackingStatusAndLocationUnavailableAreExplicitWithoutInventedData() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, true);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.TRACKING_UNAVAILABLE);
		assertThat(domain.getTrackingHistory(shipmentId).events()).isEmpty();
		assertThat(domain.getTrackingHistory(shipmentId).provenance().authority()).isEqualTo(Authority.UNAVAILABLE);
		assertThat(domain.getCurrentShipmentStatus(shipmentId).status()).isEmpty();
		assertThat(domain.getCurrentPackageLocation(shipmentId).location()).isEmpty();
		assertThat(domain.getCurrentPackageLocation(shipmentId).provenance().sourceObservedAt()).isEmpty();
	}
	@Test void missingExternalShipmentIdDoesNotGuessAnOrderWideLocation() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, false);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThat(domain.getCurrentPackageLocation(shipmentId).location()).isEmpty();
		assertThat(domain.getCurrentShipmentStatus(shipmentId).provenance().authority()).isEqualTo(Authority.UNAVAILABLE);
		assertThat(retrievals.get()).isZero();
	}
	@Test void providerFailureRemainsSafeErrorWithoutPersistedFallback() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, true);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.API_FAILURE);
		for (Runnable call : List.<Runnable>of(() -> domain.getTrackingHistory(shipmentId),
			() -> domain.getCurrentShipmentStatus(shipmentId), () -> domain.getCurrentPackageLocation(shipmentId))) {
			assertThatThrownBy(call::run).isInstanceOf(ProviderExecutionException.class).hasCause(null);
		}
	}
	@Test void disabledIntegrationFailsBeforeDomainRetrievesCredentials() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, false, true)); long shipmentId = shipment(owner, orderId, true);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThatThrownBy(() -> domain.getCurrentPackageLocation(shipmentId)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.DISABLED));
		assertThat(retrievals.get()).isZero();
	}
	private void staleMemory(AuthenticatedUserContext owner, long orderId, long shipmentId) {
		long conversation = jdbc.queryForObject("INSERT INTO conversations (tenant_id,user_id) VALUES (?,?) RETURNING id", Long.class, owner.tenantId(), owner.userId());
		jdbc.update("INSERT INTO messages (tenant_id,user_id,conversation_id,message_role,content) VALUES (?,?,?,'assistant','old-memory-location old-memory-status')",
			owner.tenantId(), owner.userId(), conversation);
		jdbc.update("UPDATE orders SET order_status='old-order-status',cached_payload='{\"location\":\"old-cache-location\"}',cache_expires_at=fetched_at+interval '1 hour' WHERE id=?", orderId);
		jdbc.update("UPDATE shipments SET cached_payload='{\"location\":\"old-cache-location\"}',cache_expires_at=fetched_at+interval '1 hour' WHERE id=?", shipmentId);
	}
	@Test void providerCurrentDataWinsOverStoredCacheEventsAndConversationMemory() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, true);
		event(owner, shipmentId); staleMemory(owner, orderId, shipmentId);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThat(domain.getCurrentPackageLocation(shipmentId).location()).contains("fixture-location");
		assertThat(domain.getCurrentShipmentStatus(shipmentId).status()).contains("fixture-status");
		assertThat(domain.getTrackingHistory(shipmentId).events()).extracting(OrderDomainData.TrackingPoint::status).containsExactly("fixture-status");
	}
	@Test void unavailableProviderNeverSubstitutesConversationOrCacheAsCurrentData() throws Exception {
		var owner = login(); long orderId = order(owner, integration(owner, true, true)); long shipmentId = shipment(owner, orderId, true);
		staleMemory(owner, orderId, shipmentId);
		var domain = tracking(DeterministicTestOrderProvider.Scenario.TRACKING_UNAVAILABLE);
		assertThat(domain.getCurrentPackageLocation(shipmentId).location()).isEmpty();
		assertThat(domain.getCurrentShipmentStatus(shipmentId).status()).isEmpty();
		assertThat(domain.getTrackingHistory(shipmentId).provenance().authority()).isEqualTo(Authority.UNAVAILABLE);
	}
	@Test void domainMethodInputsCannotCarryExternalIdsUrlsOrTenantOverrides() {
		for (Class<?> type : List.of(OrderDomainService.class, ShipmentTrackingService.class)) {
			for (var method : type.getDeclaredMethods()) {
				if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
				for (Class<?> input : method.getParameterTypes()) assertThat(input).isIn(long.class, int.class);
			}
		}
		assertThat(Arrays.stream(OrderDomainData.Order.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
			.doesNotContain("cachedPayload", "credentialReference", "tenantId", "userId");
	}
	@Test void deterministicFixtureExercisesAllFiveOperationsAndCredentialCleanup() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		var execution = deterministic(DeterministicTestOrderProvider.Scenario.SUCCESS);
		var orderId = DeterministicTestOrderProvider.ORDER;
		assertThat(execution.getOrders(id)).isEqualTo(execution.getOrders(id));
		assertThat(execution.getOrderDetails(id, orderId).orderId()).isEqualTo(orderId);
		assertThat(execution.getTrackingHistory(id, orderId, Optional.empty()).events()).hasSize(1);
		assertThat(execution.getCurrentShipmentStatus(id, orderId, Optional.empty()).observedAt()).isEqualTo(DeterministicTestOrderProvider.TIME);
		assertThat(execution.getCurrentPackageLocation(id, orderId, Optional.empty()).location()).isEqualTo("fixture-location");
		assertThat(retrievals.get()).isEqualTo(6);
		assertThatThrownBy(retrievedMaterial.get()::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
	}
	@Test void serviceEnforcesItsTwentySecondDeadlineAndDisposesCredential() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		var stopped = new java.util.concurrent.CountDownLatch(1);
		when(provider.getOrders(any())).thenAnswer(call -> {
			try { Thread.sleep(60000); } catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
			finally { stopped.countDown(); }
			return new OrdersResult(List.of());
		});
		long started = System.nanoTime();
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.TIMEOUT));
		assertThat(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isBetween(19000L, 24000L);
		assertThat(stopped.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
		assertThatThrownBy(retrievedMaterial.get()::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
	}
	@Test void providerFailureDoesNotEmitCredentialOrReferenceMarkersToLogs() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
		appender.start(); logger.addAppender(appender);
		try {
			when(provider.getOrders(any())).thenThrow(new IllegalStateException("synthetic-test-only synthetic-reference-only"));
			assertThatThrownBy(() -> service().getOrders(id)).isInstanceOf(ProviderExecutionException.class).hasCause(null);
			assertThat(appender.list).allSatisfy(event -> {
				assertThat(event.getFormattedMessage()).doesNotContain("synthetic-test-only", "synthetic-reference-only");
				assertThat(event.getThrowableProxy()).isNull();
			});
		}
		finally { logger.detachAppender(appender); appender.stop(); }
	}
	@ParameterizedTest
	@CsvSource({"NOT_FOUND,ORDER_NOT_FOUND", "TRACKING_UNAVAILABLE,TRACKING_UNAVAILABLE",
		"AUTHENTICATION_FAILURE,AUTHENTICATION_FAILED", "API_FAILURE,EXECUTION_FAILED", "NULL_RESPONSE,EXECUTION_FAILED"})
	void deterministicFailuresAreSanitizedAndCloseMaterial(DeterministicTestOrderProvider.Scenario scenario,
			ProviderExecutionException.Reason reason) throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		assertThatThrownBy(() -> deterministic(scenario).getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> { assertThat(e.reason()).isEqualTo(reason); assertThat(e.getCause()).isNull(); });
		assertThatThrownBy(retrievedMaterial.get()::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
	}
	@Test void mismatchedOrderResponseIsRejectedForAllFourOrderOperations() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		var execution = deterministic(DeterministicTestOrderProvider.Scenario.WRONG_ORDER);
		var orderId = DeterministicTestOrderProvider.ORDER;
		for (Runnable call : List.<Runnable>of(() -> execution.getOrderDetails(id, orderId),
			() -> execution.getTrackingHistory(id, orderId, Optional.empty()),
			() -> execution.getCurrentShipmentStatus(id, orderId, Optional.empty()),
			() -> execution.getCurrentPackageLocation(id, orderId, Optional.empty()))) {
			assertThatThrownBy(call::run).isInstanceOfSatisfying(ProviderExecutionException.class,
				e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.EXECUTION_FAILED));
		}
	}
	@Test void mismatchedShipmentResponseIsRejected() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		when(provider.getCurrentShipmentStatus(any())).thenReturn(new ShipmentStatus(DeterministicTestOrderProvider.ORDER,
			Optional.of(new ExternalShipmentId("wrong-fixture")), "fixture", DeterministicTestOrderProvider.TIME));
		assertThatThrownBy(() -> service().getCurrentShipmentStatus(id, DeterministicTestOrderProvider.ORDER,
			Optional.of(new ExternalShipmentId("expected-fixture")))).isInstanceOf(ProviderExecutionException.class);
	}
	@Test void retrievalFailureCannotReachAdapterOrLeakItsCause() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		CredentialStore failed = mock(CredentialStore.class);
		when(failed.retrieve(any(), any())).thenThrow(new IllegalStateException("synthetic-sensitive-marker"));
		assertThatThrownBy(() -> service(failed, List.of(adapter)).getOrders(id)).isInstanceOf(ProviderExecutionException.class)
			.hasMessageNotContaining("synthetic").hasCause(null);
		assertThat(opens.get()).isZero();
	}
	@Test void transportFailureIsSanitizedAndClosesRetrievedMaterial() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		var blockedTransportAdapter = new BackendOrderProviderAdapter() {
			public String providerKey() { return "test-only"; }
			public ExternalOrderProvider open(java.net.URI base, CredentialMaterial material, SecureHttpTransport transport) {
				transport.execute(SecureHttpTransport.Method.GET, java.net.URI.create("https://127.0.0.1/"), Map.of());
				throw new AssertionError("Transport must reject before provider creation");
			}
		};
		assertThatThrownBy(() -> service(store, List.of(blockedTransportAdapter)).getOrders(id))
			.isInstanceOf(ProviderExecutionException.class).hasCause(null).hasMessageNotContaining("127.0.0.1");
		assertThatThrownBy(retrievedMaterial.get()::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
	}
	@Test void ownedToolsExecuteAllFiveOperationsUsingPersistedOrderMapping() throws Exception {
		var owner = login(); long id = integration(owner, true, true); long orderId = order(owner, id);
		var tools = tools(id, DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThat(tools.getCustomerOrders().data().orders()).hasSize(1);
		assertThat(tools.getOrderDetails(orderId).data().orderId()).isEqualTo(DeterministicTestOrderProvider.ORDER);
		assertThat(tools.getTrackingHistory(orderId).data().events()).hasSize(1);
		assertThat(tools.getCurrentShipmentStatus(orderId).data().status()).isEqualTo("fixture-status");
		assertThat(tools.getCurrentPackageLocation(orderId).data().location()).isEqualTo("fixture-location");
		assertThat(retrievedScope.get()).isEqualTo(new CredentialScope(owner.tenantId(), owner.userId(), id));
	}
	@Test void toolsDenyForeignOrdersAndMissingOrdersIdenticallyBeforeRetrieval() throws Exception {
		var foreign = login(); long foreignIntegration = integration(foreign, true, true); long foreignOrder = order(foreign, foreignIntegration);
		var owner = login(); long id = integration(owner, true, true);
		var tools = tools(id, DeterministicTestOrderProvider.Scenario.SUCCESS);
		for (long orderId : new long[] {foreignOrder, Long.MAX_VALUE}) {
			for (Runnable call : List.<Runnable>of(() -> tools.getOrderDetails(orderId), () -> tools.getTrackingHistory(orderId),
				() -> tools.getCurrentShipmentStatus(orderId), () -> tools.getCurrentPackageLocation(orderId))) {
				assertThatThrownBy(call::run).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
			}
		}
		assertThat(retrievals.get()).isZero();
	}
	@Test void sameTenantOtherUserOrderIsDenied() throws Exception {
		var foreign = login(); long foreignIntegration = integration(foreign, true, true); long foreignOrder = order(foreign, foreignIntegration);
		var owner = login(foreign.tenantId()); long id = integration(owner, true, true);
		assertThatThrownBy(() -> tools(id, DeterministicTestOrderProvider.Scenario.SUCCESS).getOrderDetails(foreignOrder))
			.isInstanceOf(AccessDeniedException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void ownedOrderInAnotherIntegrationCannotOverrideBoundIntegration() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		jdbc.update("UPDATE integrations SET display_name='Other fixture' WHERE id=?", id);
		long other = integration(owner, true, true); long otherOrder = order(owner, other);
		assertThatThrownBy(() -> tools(id, DeterministicTestOrderProvider.Scenario.SUCCESS).getOrderDetails(otherOrder))
			.isInstanceOf(AccessDeniedException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void boundToolsCannotBeReusedByAnotherTenantOrUser() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		var tools = tools(id, DeterministicTestOrderProvider.Scenario.SUCCESS);
		login(owner.tenantId());
		assertThatThrownBy(tools::getCustomerOrders).isInstanceOf(AccessDeniedException.class);
		login();
		assertThatThrownBy(tools::getCustomerOrders).isInstanceOf(AccessDeniedException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void boundToolsCannotOverrideSession() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		var tools = tools(id, DeterministicTestOrderProvider.Scenario.SUCCESS);
		SecurityContextHolder.getContext().setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
			new ApplicationPrincipal(owner.userId(), owner.tenantId(), UUID.randomUUID()), null, List.of()));
		assertThatThrownBy(tools::getCustomerOrders).isInstanceOf(AccessDeniedException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void toolSurfaceHasOnlyBusinessIdsAndCannotAcceptUrlsOrCredentialFields() throws Exception {
		var methods = ControlledOrderTools.class.getDeclaredMethods(); assertThat(methods).hasSize(5);
		for (var method : methods) {
			assertThat(method.getReturnType()).isEqualTo(UntrustedProviderData.class);
			for (Class<?> argument : method.getParameterTypes()) assertThat(argument).isEqualTo(long.class);
		}
		var owner = login(); long id = integration(owner, true, true);
		var tools = tools(id, DeterministicTestOrderProvider.Scenario.SUCCESS);
		assertThatThrownBy(() -> ControlledOrderTools.class.getMethod("getOrderDetails", long.class)
			.invoke(tools, "https://127.0.0.1/" )).isInstanceOf(IllegalArgumentException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void maliciousProviderTextRemainsExplicitlyUntrustedDataNotAnInstruction() throws Exception {
		var owner = login(); long id = integration(owner, true, true); long orderId = order(owner, id);
		var result = tools(id, DeterministicTestOrderProvider.Scenario.UNTRUSTED_TEXT).getOrderDetails(orderId);
		assertThat(result.trust()).isEqualTo(UntrustedProviderData.Trust.EXTERNAL_UNTRUSTED);
		assertThat(result.data().status()).contains(DeterministicTestOrderProvider.UNTRUSTED_TEXT);
		String serialized = json.writeValueAsString(result);
		assertThat((String) JsonPath.read(serialized, "$.trust")).isEqualTo("EXTERNAL_UNTRUSTED");
		assertThat((String) JsonPath.read(serialized, "$.data.status")).isEqualTo(DeterministicTestOrderProvider.UNTRUSTED_TEXT);
		assertThat(serialized).doesNotContain("\"credential\"", "\"credentialReference\"", "\"tenantId\"",
			"\"userId\"", "\"sessionId\"", "\"systemPrompt\"", "\"authorizationHeader\"");
		assertThat(result.toString()).doesNotContain(DeterministicTestOrderProvider.UNTRUSTED_TEXT);
		assertThat(contexts.getCurrentUser()).isEqualTo(owner);
		assertThat(retrievals.get()).isEqualTo(1);
	}

	private AuthenticatedUserContext login() throws Exception {
		return login(null);
	}
	private AuthenticatedUserContext login(UUID tenant) throws Exception {
		String email = "execution-" + UUID.randomUUID() + "@example.com";
		String body = "{\"email\":\"" + email + "\",\"password\":\"SyntheticPass!123\"}";
		mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
		if (tenant != null) {
			jdbc.update("UPDATE users SET tenant_id=? WHERE email=?", tenant, email);
			entityManager.clear();
		}
		String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String token = JsonPath.read(response, "$.accessToken");
		lastAccessToken = token;
		SecurityContextHolder.getContext().setAuthentication(converter.convert(decoder.decode(token)));
		return contexts.getCurrentUser();
	}
	@Test void domainApisRequireBearerAuthentication() throws Exception {
		SecurityContextHolder.clearContext();
		for(String path:List.of("/api/v1/orders","/api/v1/orders/1","/api/v1/shipments/1",
			"/api/v1/shipments/1/tracking-history","/api/v1/shipments/1/current-status","/api/v1/shipments/1/current-location"))
			mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-ID"));
	}
	@Test void authenticatedOrderAndShipmentApisReturnSafeApplicationProjections() throws Exception {
		var owner=login();long id=order(owner,integration(owner,true,true));long shipmentId=shipment(owner,id,true);String token=lastAccessToken;
		mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.orders[0].id").value(id)).andExpect(jsonPath("$.orders[0].provenance.authority").value("PERSISTED_APPLICATION"));
		mvc.perform(get("/api/v1/orders/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.credentialReference").doesNotExist()).andExpect(jsonPath("$.externalOrderId").doesNotExist());
		mvc.perform(get("/api/v1/shipments/"+shipmentId).header("Authorization","Bearer "+token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.orderId").value(id));
	}
	@Test void trackingApisExerciseDeterministicProviderAndPreserveAuthority() throws Exception {
		var owner=login();long id=order(owner,integration(owner,true,true));long shipmentId=shipment(owner,id,true);String token=lastAccessToken;
		mvc.perform(get("/api/v1/shipments/"+shipmentId+"/tracking-history").header("Authorization","Bearer "+token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.events[0].status").value("fixture-status")).andExpect(jsonPath("$.provenance.authority").value("EXTERNAL_PROVIDER_RESPONSE"));
		mvc.perform(get("/api/v1/shipments/"+shipmentId+"/current-status").header("Authorization","Bearer "+token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("fixture-status"));
		mvc.perform(get("/api/v1/shipments/"+shipmentId+"/current-location").header("Authorization","Bearer "+token)).andExpect(status().isOk())
			.andExpect(jsonPath("$.location").value("fixture-location")).andExpect(jsonPath("$.provenance.sourceObservedAt").value("2026-01-01T00:00:00Z"));
	}
	@Test void apiUnavailableLocationIsExplicitNotFabricated() throws Exception {
		var owner=login();long id=order(owner,integration(owner,true,true));long shipmentId=shipment(owner,id,false);String token=lastAccessToken;
		for(String operation:List.of("current-status","current-location","tracking-history"))
			mvc.perform(get("/api/v1/shipments/"+shipmentId+"/"+operation).header("Authorization","Bearer "+token)).andExpect(status().isOk())
				.andExpect(jsonPath("$.provenance.authority").value("UNAVAILABLE"));
	}
	@Test void apiCrossTenantAndMissingResourcesUseEstablishedForbiddenResponse() throws Exception {
		var owner=login();long id=order(owner,integration(owner,true,true));long shipmentId=shipment(owner,id,true);login();String token=lastAccessToken;
		for(String path:List.of("/api/v1/orders/"+id,"/api/v1/orders/"+Long.MAX_VALUE,"/api/v1/shipments/"+shipmentId,
			"/api/v1/shipments/"+shipmentId+"/current-location","/api/v1/shipments/"+Long.MAX_VALUE+"/current-location"))
			mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
	}
	@Test void apiRejectsInvalidIdsIdentityUrlCredentialQueryAndGetBodiesWithRequestIds() throws Exception {
		login();String token=lastAccessToken;
		for(String path:List.of("/api/v1/orders/abc","/api/v1/orders/-1","/api/v1/shipments/0/current-location"))
			mvc.perform(get(path).header("Authorization","Bearer "+token).header("X-Request-ID","domain-invalid-test")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.requestId").value("domain-invalid-test"));
		for(String field:List.of("tenantId","userId","sessionId","url","credentialReference"))
			mvc.perform(get("/api/v1/orders").queryParam(field,"test").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/orders").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"tenantId\":\"test\"}"))
			.andExpect(status().isBadRequest());
	}
	@Test void clientIdentityHeadersCannotOverrideOwnedApiContext() throws Exception {
		var owner=login();long id=order(owner,integration(owner,true,true));String token=lastAccessToken;
		mvc.perform(get("/api/v1/orders/"+id).header("Authorization","Bearer "+token).header("tenantId",UUID.randomUUID().toString()).header("userId","999999"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
	}
	@Test void apiDisabledProviderErrorIsSafeAndCorrelated() throws Exception {
		var owner=login();long id=order(owner,integration(owner,false,true));long shipmentId=shipment(owner,id,true);String token=lastAccessToken;
		mvc.perform(get("/api/v1/shipments/"+shipmentId+"/current-location").header("Authorization","Bearer "+token).header("X-Request-ID","domain-provider-test"))
			.andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PROVIDER_UNAVAILABLE"))
			.andExpect(jsonPath("$.requestId").value("domain-provider-test")).andExpect(jsonPath("$.credentialReference").doesNotExist());
	}
	@Test void conversationHistoryIsOwnedBoundedAndSystemRolesRemainUntrusted() throws Exception {
		var owner=login();long conversation=jdbc.queryForObject("INSERT INTO conversations (tenant_id,user_id) VALUES (?,?) RETURNING id",Long.class,owner.tenantId(),owner.userId());
		for(int i=0;i<21;i++) jdbc.update("INSERT INTO messages (tenant_id,user_id,conversation_id,message_role,content) VALUES (?,?,?,'system','test-only untrusted history')",owner.tenantId(),owner.userId(),conversation);
		var service=new ConversationContextService(contexts,authorization,jdbc);var history=service.getHistory(conversation);
		assertThat(history.truncated()).isTrue();assertThat(history.messages()).hasSize(20);
		assertThat(history.messages()).allSatisfy(text->{assertThat(text.role()).isEqualTo(AiModelContract.ContextRole.OTHER);assertThat(text.trust()).isEqualTo(AiModelContract.Trust.UNTRUSTED_HISTORY);});
		login(owner.tenantId());assertThatThrownBy(()->service.getHistory(conversation)).isInstanceOf(AccessDeniedException.class);
		login();assertThatThrownBy(()->service.getHistory(conversation)).isInstanceOf(AccessDeniedException.class);
		assertThatThrownBy(()->service.getHistory(Long.MAX_VALUE)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
	}
	@SuppressWarnings("unchecked")
	private AgentOrchestrationService agent(ScriptedTestModel model) {
		ObjectProvider<AiModelProvider> models=mock(ObjectProvider.class);when(models.getIfAvailable()).thenReturn(model);
		return new AgentOrchestrationService(contexts,new ConversationContextService(contexts,authorization,jdbc),toolFactory,models,json);
	}
	private void cleanupCommitted(AuthenticatedUserContext owner) {
		for(String table:List.of("audit_events","refresh_tokens","messages","conversations","tracking_events","shipments","orders","integrations")) jdbc.update("DELETE FROM public."+table+" WHERE tenant_id=?",owner.tenantId());
		jdbc.update("DELETE FROM users WHERE id=? AND tenant_id=?",owner.userId(),owner.tenantId());
	}
	@Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
	void actualAgentToolRunsThroughOwnedDomainAndFreshProviderDespiteStaleHistory() throws Exception {
		var owner=login();
		try {
			long integrationId=integration(owner,true,true);long orderId=order(owner,integrationId);
			long conversation=jdbc.queryForObject("INSERT INTO conversations (tenant_id,user_id) VALUES (?,?) RETURNING id",Long.class,owner.tenantId(),owner.userId());
			jdbc.update("INSERT INTO messages (tenant_id,user_id,conversation_id,message_role,content) VALUES (?,?,?,'assistant','stale-memory-location')",owner.tenantId(),owner.userId(),conversation);
			var model=new ScriptedTestModel(ScriptedTestModel.Scenario.LOCATION,orderId);var result=agent(model).execute(conversation,integrationId,"test-only current location question");
			assertThat(result.evidence().getFirst().success()).isTrue();assertThat(result.evidence().getFirst().data()).contains("fixture-location","EXTERNAL_UNTRUSTED").doesNotContain("stale-memory-location","synthetic-reference-only","tenantId","userId","sessionId");
			assertThat(model.requests.getLast().history().messages().getFirst().text()).isEqualTo("stale-memory-location");
			assertThat(result.trust()).isEqualTo(AiModelContract.Trust.MODEL_GENERATED_UNVERIFIED);
		} finally {cleanupCommitted(owner);}
	}
	@Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
	void actualAgentCannotUseModelSuppliedForeignOrderId() throws Exception {
		var foreign=login();AuthenticatedUserContext owner=null;
		try {
			long foreignOrder=order(foreign,integration(foreign,true,true));owner=login();long integrationId=integration(owner,true,true);
			long conversation=jdbc.queryForObject("INSERT INTO conversations (tenant_id,user_id) VALUES (?,?) RETURNING id",Long.class,owner.tenantId(),owner.userId());
			var model=new ScriptedTestModel(ScriptedTestModel.Scenario.DETAILS,foreignOrder);var result=agent(model).execute(conversation,integrationId,"test-only");
			assertThat(result.evidence().getFirst().success()).isFalse();assertThat(result.evidence().getFirst().errorCode()).contains("ACCESS_DENIED");
			assertThat(model.requests.getLast().toolResults().getFirst().data()).isEmpty();
		} finally {if(owner!=null)cleanupCommitted(owner);cleanupCommitted(foreign);}
	}
	private long integration(AuthenticatedUserContext owner, boolean enabled, boolean credential) {
		return jdbc.queryForObject("""
			INSERT INTO integrations (tenant_id,user_id,provider_key,display_name,base_url,is_enabled,credential_reference,credential_type)
			VALUES (?,?,'test-only','Test fixture','https://example.com',?,?,?) RETURNING id
			""", Long.class, owner.tenantId(), owner.userId(), enabled,
			credential ? "synthetic-reference-only" : null, credential ? "test-only" : null);
	}
	@Test void ownedIntegrationPassesOnlyServerContextAndClosesCredential() throws Exception {
		var user = login(); long id = integration(user, true, true);
		when(provider.getOrders(any())).thenAnswer(call -> {
			receivedContext.set(((GetOrdersRequest) call.getArgument(0)).context());
			return new OrdersResult(List.of());
		});
		assertThat(service().getOrders(id).orders()).isEmpty();
		assertThat(receivedContext.get()).isEqualTo(new ExternalOrderProviderContext(user.tenantId(), user.userId(), user.sessionId(), id));
		assertThat(retrievedScope.get()).isEqualTo(new CredentialScope(user.tenantId(), user.userId(), id));
		assertThatThrownBy(retrievedMaterial.get()::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
	}
	@Test void otherTenantAndMissingIntegrationProduceSameDenialWithoutCredentialAccess() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		login();
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
		assertThatThrownBy(() -> service().getOrders(Long.MAX_VALUE)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
		assertThat(retrievals.get()).isZero(); assertThat(opens.get()).isZero();
	}
	@Test void sameTenantOtherUserCannotExecute() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		login(owner.tenantId());
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOf(AccessDeniedException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void unauthenticatedExecutionFailsBeforeLookup() {
		SecurityContextHolder.clearContext();
		assertThatThrownBy(() -> service().getOrders(1)).isInstanceOf(InsufficientAuthenticationException.class);
		assertThat(retrievals.get()).isZero();
	}
	@Test void disabledIntegrationNeverRetrievesCredentialsOrOpensAdapter() throws Exception {
		var owner = login(); long id = integration(owner, false, true);
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.DISABLED));
		assertThat(retrievals.get()).isZero(); assertThat(opens.get()).isZero();
	}
	@Test void missingCredentialIsReportedWithoutExecution() throws Exception {
		var owner = login(); long id = integration(owner, true, false);
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.UNCONFIGURED));
		assertThat(opens.get()).isZero();
	}
	@Test void noProductionAdapterFailsClosed() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		assertThatThrownBy(() -> service(store, List.of()).getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.PROVIDER_UNAVAILABLE));
		assertThat(retrievals.get()).isZero();
	}
	@Test void noCredentialStoreFailsClosed() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		assertThatThrownBy(() -> service(null, List.of(adapter)).getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.CREDENTIAL_STORE_UNAVAILABLE));
		assertThat(opens.get()).isZero();
	}
	@Test void adapterExceptionCannotExposeCredentialsAndStillClosesMaterial() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		when(provider.getOrders(any())).thenThrow(new IllegalStateException("synthetic-test-only synthetic-reference-only"));
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOf(ProviderExecutionException.class)
			.hasMessageNotContaining("synthetic").hasCause(null);
		assertThatThrownBy(retrievedMaterial.get()::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
	}
	@Test void authenticationFailuresAreSanitizedWithoutRetry() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		when(provider.getOrders(any())).thenThrow(new ExternalProviderAuthenticationException(new IllegalStateException("synthetic-test-only")));
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> { assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.AUTHENTICATION_FAILED); assertThat(e.getCause()).isNull(); });
		verify(provider, times(1)).getOrders(any()); assertThat(retrievals.get()).isEqualTo(1);
	}
	@Test void nullProviderOutputIsRejectedAsUnvalidatedResponse() throws Exception {
		var owner = login(); long id = integration(owner, true, true);
		assertThatThrownBy(() -> service().getOrders(id)).isInstanceOfSatisfying(ProviderExecutionException.class,
			e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.EXECUTION_FAILED));
	}
	@Test void allFiveOperationsUseSameAuthorizationAndCleanupBoundary() throws Exception {
		var owner = login(); long id = integration(owner, false, true);
		var order = new ExternalOrderId("test-order"); var shipment = Optional.<ExternalShipmentId>empty();
		var service = service();
		for (Runnable operation : List.<Runnable>of(() -> service.getOrders(id),
			() -> service.getOrderDetails(id, order), () -> service.getTrackingHistory(id, order, shipment),
			() -> service.getCurrentShipmentStatus(id, order, shipment), () -> service.getCurrentPackageLocation(id, order, shipment))) {
			assertThatThrownBy(operation::run).isInstanceOfSatisfying(ProviderExecutionException.class,
				e -> assertThat(e.reason()).isEqualTo(ProviderExecutionException.Reason.DISABLED));
		}
		assertThat(retrievals.get()).isZero();
	}
}
