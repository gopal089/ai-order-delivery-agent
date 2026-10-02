package com.aiorderdeliveryagent.backend.integration.order.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ExternalOrderSummary(
		ExternalOrderId orderId,
		Optional<String> status,
		Optional<Instant> sourceUpdatedAt) {

	public ExternalOrderSummary {
		Objects.requireNonNull(orderId, "orderId must not be null");
		status = ContractValidation.requireOptional(status, "status");
		sourceUpdatedAt = ContractValidation.requireOptional(sourceUpdatedAt, "sourceUpdatedAt");
		status.ifPresent(value -> ContractValidation.requireText(value, "status value"));
	}
}
