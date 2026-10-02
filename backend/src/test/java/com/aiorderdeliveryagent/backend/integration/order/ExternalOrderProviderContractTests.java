package com.aiorderdeliveryagent.backend.integration.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.aiorderdeliveryagent.backend.integration.order.exception.ExternalApiException;
import com.aiorderdeliveryagent.backend.integration.order.exception.ExternalOrderNotFoundException;
import com.aiorderdeliveryagent.backend.integration.order.exception.ExternalOrderProviderException;
import com.aiorderdeliveryagent.backend.integration.order.exception.ExternalProviderAuthenticationException;
import com.aiorderdeliveryagent.backend.integration.order.exception.TrackingUnavailableException;
import com.aiorderdeliveryagent.backend.integration.order.exception.UnsupportedProviderOperationException;
import com.aiorderdeliveryagent.backend.integration.order.model.ExternalOrderDetails;
import com.aiorderdeliveryagent.backend.integration.order.model.ExternalOrderId;
import com.aiorderdeliveryagent.backend.integration.order.model.ExternalOrderProviderContext;
import com.aiorderdeliveryagent.backend.integration.order.model.ExternalOrderSummary;
import com.aiorderdeliveryagent.backend.integration.order.model.GetOrderDetailsRequest;
import com.aiorderdeliveryagent.backend.integration.order.model.GetOrdersRequest;
import com.aiorderdeliveryagent.backend.integration.order.model.GetTrackingRequest;
import com.aiorderdeliveryagent.backend.integration.order.model.OrdersResult;
import com.aiorderdeliveryagent.backend.integration.order.model.PackageLocation;
import com.aiorderdeliveryagent.backend.integration.order.model.ShipmentStatus;
import com.aiorderdeliveryagent.backend.integration.order.model.TrackingEvent;
import com.aiorderdeliveryagent.backend.integration.order.model.TrackingHistory;

import org.junit.jupiter.api.Test;

class ExternalOrderProviderContractTests {

	private static final Instant OBSERVED_AT = Instant.parse("2026-01-01T00:00:00Z");
	private static final ExternalOrderId ORDER_ID = new ExternalOrderId("test-order");

	@Test
	void interfaceDefinesOnlyTheFiveProviderOperations() {
		Set<String> operationNames = Arrays.stream(ExternalOrderProvider.class.getDeclaredMethods())
				.map(Method::getName)
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				"getOrders",
				"getOrderDetails",
				"getTrackingHistory",
				"getCurrentShipmentStatus",
				"getCurrentPackageLocation"), operationNames);
	}

	@Test
	void publicContractDoesNotContainCredentialBearingFields() {
		List<Class<?>> contractRecords = List.of(
				ExternalOrderProviderContext.class,
				GetOrdersRequest.class,
				GetOrderDetailsRequest.class,
				GetTrackingRequest.class,
				ExternalOrderSummary.class,
				ExternalOrderDetails.class,
				OrdersResult.class,
				TrackingEvent.class,
				TrackingHistory.class,
				ShipmentStatus.class,
				PackageLocation.class);

		Set<String> componentNames = contractRecords.stream()
				.flatMap(type -> Arrays.stream(type.getRecordComponents()))
				.map(RecordComponent::getName)
				.map(String::toLowerCase)
				.collect(Collectors.toSet());

		assertTrue(componentNames.stream().noneMatch(name ->
				name.contains("credential")
						|| name.contains("secret")
						|| name.contains("password")
						|| name.contains("token")
						|| name.contains("apikey")));
	}

	@Test
	void trustedContextPreservesTenantUserSessionAndIntegrationIdentity() {
		UUID tenantId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		ExternalOrderProviderContext context = new ExternalOrderProviderContext(tenantId, 42, sessionId, 84);

		assertEquals(tenantId, context.tenantId());
		assertEquals(42, context.userId());
		assertEquals(sessionId, context.sessionId());
		assertEquals(84, context.integrationId());
		assertThrows(NullPointerException.class,
				() -> new ExternalOrderProviderContext(null, 42, sessionId, 84));
		assertThrows(IllegalArgumentException.class,
				() -> new ExternalOrderProviderContext(tenantId, 0, sessionId, 84));
		assertThrows(NullPointerException.class,
				() -> new ExternalOrderProviderContext(tenantId, 42, null, 84));
		assertThrows(IllegalArgumentException.class,
				() -> new ExternalOrderProviderContext(tenantId, 42, sessionId, 0));
	}

	@Test
	void requestContractsRequireTrustedContextAndTypedIdentifiers() {
		ExternalOrderProviderContext context = context();

		assertThrows(NullPointerException.class, () -> new GetOrdersRequest(null));
		assertThrows(NullPointerException.class, () -> new GetOrderDetailsRequest(null, ORDER_ID));
		assertThrows(NullPointerException.class, () -> new GetOrderDetailsRequest(context, null));
		assertThrows(NullPointerException.class,
				() -> new GetTrackingRequest(context, ORDER_ID, null));
		assertThrows(IllegalArgumentException.class, () -> new ExternalOrderId("  "));
	}

	@Test
	void responseCollectionsAreImmutableSnapshots() {
		List<ExternalOrderSummary> mutableOrders = new ArrayList<>();
		mutableOrders.add(summary());
		OrdersResult result = new OrdersResult(mutableOrders);
		mutableOrders.clear();

		assertEquals(1, result.orders().size());
		assertThrows(UnsupportedOperationException.class, () -> result.orders().clear());

		List<TrackingEvent> mutableEvents = new ArrayList<>();
		mutableEvents.add(new TrackingEvent(
				OBSERVED_AT, "test-status", Optional.empty(), Optional.empty()));
		TrackingHistory history = new TrackingHistory(ORDER_ID, Optional.empty(), mutableEvents);
		mutableEvents.clear();

		assertEquals(1, history.events().size());
		assertThrows(UnsupportedOperationException.class, () -> history.events().clear());
	}

	@Test
	void testOnlyFakeDemonstratesTypedContractWithoutCredentialsOrTransport() {
		ExternalOrderProviderContext context = context();
		ExternalOrderProvider provider = new TestOnlyExternalOrderProvider();

		assertEquals(ORDER_ID,
				provider.getOrders(new GetOrdersRequest(context)).orders().getFirst().orderId());
		assertEquals(ORDER_ID,
				provider.getOrderDetails(new GetOrderDetailsRequest(context, ORDER_ID)).orderId());
		assertEquals(ORDER_ID,
				provider.getTrackingHistory(trackingRequest(context)).orderId());
		assertEquals("test-status",
				provider.getCurrentShipmentStatus(trackingRequest(context)).status());
		assertEquals("test-location",
				provider.getCurrentPackageLocation(trackingRequest(context)).location());
	}

	@Test
	void providerFailuresHaveDistinctSafeTypes() {
		List<ExternalOrderProviderException> failures = List.of(
				new ExternalProviderAuthenticationException(),
				new ExternalApiException(true),
				new ExternalOrderNotFoundException(),
				new TrackingUnavailableException(),
				new UnsupportedProviderOperationException());

		failures.forEach(failure -> {
			assertInstanceOf(ExternalOrderProviderException.class, failure);
			assertFalse(failure.getMessage().isBlank());
		});
		assertTrue(((ExternalApiException) failures.get(1)).isRetryable());
	}

	private static ExternalOrderProviderContext context() {
		return new ExternalOrderProviderContext(UUID.randomUUID(), 10, UUID.randomUUID(), 20);
	}

	private static GetTrackingRequest trackingRequest(ExternalOrderProviderContext context) {
		return new GetTrackingRequest(context, ORDER_ID, Optional.empty());
	}

	private static ExternalOrderSummary summary() {
		return new ExternalOrderSummary(ORDER_ID, Optional.of("test-status"), Optional.of(OBSERVED_AT));
	}

	/** Test-only deterministic implementation; it does not model any real provider. */
	private static final class TestOnlyExternalOrderProvider implements ExternalOrderProvider {

		@Override
		public OrdersResult getOrders(GetOrdersRequest request) {
			return new OrdersResult(List.of(summary()));
		}

		@Override
		public ExternalOrderDetails getOrderDetails(GetOrderDetailsRequest request) {
			return new ExternalOrderDetails(
					request.orderId(), Optional.of("test-status"), Optional.of(OBSERVED_AT));
		}

		@Override
		public TrackingHistory getTrackingHistory(GetTrackingRequest request) {
			return new TrackingHistory(
					request.orderId(),
					request.shipmentId(),
					List.of(new TrackingEvent(
							OBSERVED_AT, "test-status", Optional.empty(), Optional.empty())));
		}

		@Override
		public ShipmentStatus getCurrentShipmentStatus(GetTrackingRequest request) {
			return new ShipmentStatus(
					request.orderId(), request.shipmentId(), "test-status", OBSERVED_AT);
		}

		@Override
		public PackageLocation getCurrentPackageLocation(GetTrackingRequest request) {
			return new PackageLocation(
					request.orderId(), request.shipmentId(), "test-location", OBSERVED_AT);
		}
	}
}
