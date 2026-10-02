package com.aiorderdeliveryagent.backend.integration.order.model;

import java.util.Objects;
import java.util.Optional;

public record GetTrackingRequest(
		ExternalOrderProviderContext context,
		ExternalOrderId orderId,
		Optional<ExternalShipmentId> shipmentId) {

	public GetTrackingRequest {
		Objects.requireNonNull(context, "context must not be null");
		Objects.requireNonNull(orderId, "orderId must not be null");
		shipmentId = ContractValidation.requireOptional(shipmentId, "shipmentId");
	}
}
