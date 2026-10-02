package com.aiorderdeliveryagent.backend.integration.order.model;

import java.util.List;

public record OrdersResult(List<ExternalOrderSummary> orders) {

	public OrdersResult {
		orders = List.copyOf(orders);
	}
}
