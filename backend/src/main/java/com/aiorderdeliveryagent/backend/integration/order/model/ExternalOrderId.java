package com.aiorderdeliveryagent.backend.integration.order.model;

/** An opaque provider-issued order identifier. */
public record ExternalOrderId(String value) {

	public ExternalOrderId {
		value = ContractValidation.requireText(value, "value");
	}
}
