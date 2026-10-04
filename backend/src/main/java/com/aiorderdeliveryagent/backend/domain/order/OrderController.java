package com.aiorderdeliveryagent.backend.domain.order;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {
	private final OrderDomainService orders;
	OrderController(OrderDomainService orders) { this.orders = orders; }
	@GetMapping OrderDomainData.OrderPage list(@RequestParam(defaultValue="0") long afterId,
			@RequestParam(defaultValue="100") int limit, HttpServletRequest request) {
		DomainRequestPolicy.check(request, "afterId", "limit");
		return orders.listCustomerOrders(afterId, limit);
	}
	@GetMapping("/{orderId}") OrderDomainData.Order get(@PathVariable long orderId, HttpServletRequest request) {
		DomainRequestPolicy.check(request); DomainRequestPolicy.id(orderId); return orders.getOrder(orderId);
	}
}
