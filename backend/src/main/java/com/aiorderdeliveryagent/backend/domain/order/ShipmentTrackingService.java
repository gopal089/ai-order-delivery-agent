package com.aiorderdeliveryagent.backend.domain.order;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.aiorderdeliveryagent.backend.auth.*;
import com.aiorderdeliveryagent.backend.integration.*;
import com.aiorderdeliveryagent.backend.integration.order.model.ExternalShipmentId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import static com.aiorderdeliveryagent.backend.domain.order.OrderDomainData.*;
import static com.aiorderdeliveryagent.backend.domain.order.OrderDomainService.*;

/** Current data always requests the provider; no cache/conversation fallback and no raw response output. */
@Service
public class ShipmentTrackingService {
	private final AuthenticatedUserContextProvider contexts;
	private final TenantDataAuthorizationService authorization;
	private final OrderDomainService orders;
	private final ExternalOrderExecutionService execution;
	private final JdbcTemplate jdbc;
	private final Clock clock;
	public ShipmentTrackingService(AuthenticatedUserContextProvider contexts, TenantDataAuthorizationService authorization,
			OrderDomainService orders, ExternalOrderExecutionService execution, JdbcTemplate jdbc, Clock clock) {
		this.contexts = contexts; this.authorization = authorization; this.orders = orders;
		this.execution = execution; this.jdbc = jdbc; this.clock = clock;
	}
	public Shipment getShipment(long shipmentId) {
		var user = contexts.getCurrentUser(); authorization.requireShipmentAccess(shipmentId);
		var result = jdbc.query("""
			SELECT id,order_id,carrier,tracking_number,shipment_status,fetched_at,source_updated_at FROM public.shipments
			WHERE id=? AND tenant_id=? AND user_id=?
			""", (row, index) -> new Shipment(row.getLong("id"), row.getLong("order_id"),
			Optional.ofNullable(row.getString("carrier")), Optional.ofNullable(row.getString("tracking_number")),
			Optional.ofNullable(row.getString("shipment_status")), row.getTimestamp("fetched_at").toInstant(),
			persisted(optionalInstant(row, "source_updated_at"))), shipmentId, user.tenantId(), user.userId())
			.stream().findFirst().orElseThrow(OrderDomainService::denied);
		orders.getOrder(result.orderId()); return result;
	}
	public Event getPersistedTrackingEvent(long eventId) {
		var user = contexts.getCurrentUser(); authorization.requireTrackingEventAccess(eventId);
		var event = jdbc.query("""
			SELECT id,shipment_id,event_status,event_description,event_location,occurred_at FROM public.tracking_events
			WHERE id=? AND tenant_id=? AND user_id=?
			""", (row, index) -> new Event(row.getLong("id"), row.getLong("shipment_id"), row.getString("event_status"),
			Optional.ofNullable(row.getString("event_description")), Optional.ofNullable(row.getString("event_location")),
			row.getTimestamp("occurred_at").toInstant(), persisted(Optional.of(row.getTimestamp("occurred_at").toInstant()))),
			eventId, user.tenantId(), user.userId()).stream().findFirst().orElseThrow(OrderDomainService::denied);
		getShipment(event.shipmentId()); return event;
	}
	private record Target(ProviderOrderReference order, ExternalShipmentId shipment) { }
	private Target target(long shipmentId) {
		var shipment = getShipment(shipmentId); var user = contexts.getCurrentUser();
		var order = orders.resolveForProvider(shipment.orderId());
		var externalId = jdbc.query("""
			SELECT external_shipment_id FROM public.shipments WHERE id=? AND order_id=? AND tenant_id=? AND user_id=?
			""", (row, index) -> Optional.ofNullable(row.getString(1)), shipmentId, shipment.orderId(), user.tenantId(), user.userId())
			.stream().findFirst().orElseThrow(OrderDomainService::denied);
		// Never guess an order-wide aggregate for a specific shipment with no provider ID.
		if (externalId.isEmpty()) throw new ProviderExecutionException(ProviderExecutionException.Reason.TRACKING_UNAVAILABLE);
		return new Target(order, new ExternalShipmentId(externalId.get()));
	}
	private Provenance provider(Instant retrieved, Optional<Instant> observed) {
		return new Provenance(Authority.EXTERNAL_PROVIDER_RESPONSE, Optional.of(retrieved), observed);
	}
	private Provenance unavailable() { return new Provenance(Authority.UNAVAILABLE, Optional.of(clock.instant()), Optional.empty()); }
	private <T> T available(Supplier<T> operation, Supplier<T> missing) {
		try { return operation.get(); }
		catch (ProviderExecutionException failure) {
			if (failure.reason() == ProviderExecutionException.Reason.TRACKING_UNAVAILABLE
					|| failure.reason() == ProviderExecutionException.Reason.UNSUPPORTED) return missing.get();
			throw failure; // Existing fixed reason/message, no underlying cause or raw provider text.
		}
	}
	public Tracking getTrackingHistory(long shipmentId) {
		return available(() -> {
			var target = target(shipmentId);
			var result = execution.getTrackingHistory(target.order.integrationId(), target.order.externalOrderId(), Optional.of(target.shipment));
			return new Tracking(shipmentId, result.events().stream().map(e -> new TrackingPoint(e.occurredAt(), e.status(), e.description(), e.location())).toList(),
				provider(clock.instant(), Optional.empty()));
		}, () -> new Tracking(shipmentId, List.of(), unavailable()));
	}
	public CurrentStatus getCurrentShipmentStatus(long shipmentId) {
		return available(() -> {
			var target = target(shipmentId);
			var result = execution.getCurrentShipmentStatus(target.order.integrationId(), target.order.externalOrderId(), Optional.of(target.shipment));
			return new CurrentStatus(shipmentId, Optional.of(result.status()), provider(clock.instant(), Optional.of(result.observedAt())));
		}, () -> new CurrentStatus(shipmentId, Optional.empty(), unavailable()));
	}
	public CurrentLocation getCurrentPackageLocation(long shipmentId) {
		return available(() -> {
			var target = target(shipmentId);
			var result = execution.getCurrentPackageLocation(target.order.integrationId(), target.order.externalOrderId(), Optional.of(target.shipment));
			return new CurrentLocation(shipmentId, Optional.of(result.location()), provider(clock.instant(), Optional.of(result.observedAt())));
		}, () -> new CurrentLocation(shipmentId, Optional.empty(), unavailable()));
	}
}
