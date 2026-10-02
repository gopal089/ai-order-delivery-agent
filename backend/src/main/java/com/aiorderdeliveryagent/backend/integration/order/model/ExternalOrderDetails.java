package com.aiorderdeliveryagent.backend.integration.order.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal normalized order detail contract. Provider-specific payloads are not exposed.
 */
public record ExternalOrderDetails(
		ExternalOrderId orderId,
		Optional<String> status,
		Optional<Instant> sourceUpdatedAt) {

	public ExternalOrderDetails {
		Objects.requireNonNull(orderId, "orderId must not be null");
		status = ContractValidation.requireOptional(status, "status");
		sourceUpdatedAt = ContractValidation.requireOptional(sourceUpdatedAt, "sourceUpdatedAt");
		status.ifPresent(value -> ContractValidation.requireText(value, "status value"));
	}
}
