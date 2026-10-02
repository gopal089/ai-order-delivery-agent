package com.aiorderdeliveryagent.backend.integration.order.model;

import java.util.Objects;

public record GetOrdersRequest(ExternalOrderProviderContext context) {

	public GetOrdersRequest {
		Objects.requireNonNull(context, "context must not be null");
	}
}
