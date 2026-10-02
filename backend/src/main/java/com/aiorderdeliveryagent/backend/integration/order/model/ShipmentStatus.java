package com.aiorderdeliveryagent.backend.integration.order.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ShipmentStatus(
		ExternalOrderId orderId,
		Optional<ExternalShipmentId> shipmentId,
		String status,
		Instant observedAt) {

	public ShipmentStatus {
		Objects.requireNonNull(orderId, "orderId must not be null");
		shipmentId = ContractValidation.requireOptional(shipmentId, "shipmentId");
		status = ContractValidation.requireText(status, "status");
		Objects.requireNonNull(observedAt, "observedAt must not be null");
	}
}
