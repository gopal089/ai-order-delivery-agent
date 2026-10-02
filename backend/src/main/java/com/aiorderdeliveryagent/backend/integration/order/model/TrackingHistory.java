package com.aiorderdeliveryagent.backend.integration.order.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TrackingHistory(
		ExternalOrderId orderId,
		Optional<ExternalShipmentId> shipmentId,
		List<TrackingEvent> events) {

	public TrackingHistory {
		Objects.requireNonNull(orderId, "orderId must not be null");
		shipmentId = ContractValidation.requireOptional(shipmentId, "shipmentId");
		events = List.copyOf(events);
	}
}
