package com.aiorderdeliveryagent.backend.integration.order.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record TrackingEvent(
		Instant occurredAt,
		String status,
		Optional<String> description,
		Optional<String> location) {

	public TrackingEvent {
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		status = ContractValidation.requireText(status, "status");
		description = ContractValidation.requireOptional(description, "description");
		location = ContractValidation.requireOptional(location, "location");
		description.ifPresent(value -> ContractValidation.requireText(value, "description value"));
		location.ifPresent(value -> ContractValidation.requireText(value, "location value"));
	}
}
