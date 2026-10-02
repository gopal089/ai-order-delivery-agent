package com.aiorderdeliveryagent.backend.integration.order.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Provider-reported location text; no geocoding or location inference is performed. */
public record PackageLocation(
		ExternalOrderId orderId,
		Optional<ExternalShipmentId> shipmentId,
		String location,
		Instant observedAt) {

	public PackageLocation {
		Objects.requireNonNull(orderId, "orderId must not be null");
		shipmentId = ContractValidation.requireOptional(shipmentId, "shipmentId");
		location = ContractValidation.requireText(location, "location");
		Objects.requireNonNull(observedAt, "observedAt must not be null");
	}
}
