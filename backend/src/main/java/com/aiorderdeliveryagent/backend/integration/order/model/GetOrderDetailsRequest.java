package com.aiorderdeliveryagent.backend.integration.order.model;

import java.util.Objects;

public record GetOrderDetailsRequest(
		ExternalOrderProviderContext context,
		ExternalOrderId orderId) {

	public GetOrderDetailsRequest {
		Objects.requireNonNull(context, "context must not be null");
		Objects.requireNonNull(orderId, "orderId must not be null");
	}
}
