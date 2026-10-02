package com.aiorderdeliveryagent.backend.integration.order.model;

/** An opaque provider-issued shipment identifier. */
public record ExternalShipmentId(String value) {

	public ExternalShipmentId {
		value = ContractValidation.requireText(value, "value");
	}
}
