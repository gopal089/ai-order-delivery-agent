package com.aiorderdeliveryagent.backend.domain.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Application projections only: no raw payloads, credentials, URLs or authoritative identity inputs. */
public final class OrderDomainData {
	private OrderDomainData() { }
	public enum Authority { PERSISTED_APPLICATION, EXTERNAL_PROVIDER_RESPONSE, UNAVAILABLE }
	public record Provenance(Authority authority, Optional<Instant> retrievedAt, Optional<Instant> sourceObservedAt) { }
	public record Order(long id, long integrationId, Optional<String> status, Instant fetchedAt, Provenance provenance) { }
	public record OrderPage(List<Order> orders, Optional<Long> nextCursor) {
		public OrderPage { orders = List.copyOf(orders); }
	}
	public record Shipment(long id, long orderId, Optional<String> carrier, Optional<String> trackingNumber,
		Optional<String> status, Instant fetchedAt, Provenance provenance) { }
	public record Event(long id, long shipmentId, String status, Optional<String> description,
		Optional<String> location, Instant occurredAt, Provenance provenance) { }
	public record Tracking(long shipmentId, List<TrackingPoint> events, Provenance provenance) {
		public Tracking { events = List.copyOf(events); }
	}
	public record TrackingPoint(Instant occurredAt, String status, Optional<String> description, Optional<String> location) { }
	public record CurrentStatus(long shipmentId, Optional<String> status, Provenance provenance) { }
	public record CurrentLocation(long shipmentId, Optional<String> location, Provenance provenance) { }
}
