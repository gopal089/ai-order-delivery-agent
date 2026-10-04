package com.aiorderdeliveryagent.backend.integration;

import java.util.Optional;

import com.aiorderdeliveryagent.backend.auth.*;
import com.aiorderdeliveryagent.backend.integration.order.model.*;
import com.aiorderdeliveryagent.backend.integration.order.tool.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.aiorderdeliveryagent.backend.domain.order.OrderDomainService;

/** Backend-only integration selection. Expose only ControlledOrderTools to a future model. */
@Service
public final class ControlledOrderToolFactory {
	private final AuthenticatedUserContextProvider contexts;
	private final TenantDataAuthorizationService authorization;
	private final ExternalOrderExecutionService execution;
	private final OrderDomainService orders;

	ControlledOrderToolFactory(AuthenticatedUserContextProvider contexts, TenantDataAuthorizationService authorization,
			ExternalOrderExecutionService execution, JdbcTemplate jdbc) {
		this(contexts, authorization, execution, new OrderDomainService(contexts, authorization, jdbc));
	}
	@Autowired
	ControlledOrderToolFactory(AuthenticatedUserContextProvider contexts, TenantDataAuthorizationService authorization,
			ExternalOrderExecutionService execution, OrderDomainService orders) {
		this.contexts = contexts;
		this.authorization = authorization;
		this.execution = execution;
		this.orders = orders;
	}

	public ControlledOrderTools bind(long integrationId) {
		var owner = contexts.getCurrentUser();
		authorization.requireIntegrationAccess(integrationId);
		return new BoundTools(owner, integrationId);
	}

	private final class BoundTools implements ControlledOrderTools {
		private final AuthenticatedUserContext owner;
		private final long integrationId;
		private BoundTools(AuthenticatedUserContext owner, long integrationId) {
			this.owner = owner;
			this.integrationId = integrationId;
		}
		private void requireBinding() {
			if (!owner.equals(contexts.getCurrentUser())) throw denied();
			authorization.requireIntegrationAccess(integrationId);
		}
		private ExternalOrderId requireOrder(long orderId) {
			requireBinding();
			var reference = orders.resolveForProvider(orderId);
			if (reference.integrationId() != integrationId) throw denied();
			return reference.externalOrderId();
		}
		public UntrustedProviderData<OrdersResult> getCustomerOrders() {
			requireBinding(); return new UntrustedProviderData<>(execution.getOrders(integrationId));
		}
		public UntrustedProviderData<ExternalOrderDetails> getOrderDetails(long orderId) {
			return new UntrustedProviderData<>(execution.getOrderDetails(integrationId, requireOrder(orderId)));
		}
		public UntrustedProviderData<TrackingHistory> getTrackingHistory(long orderId) {
			return new UntrustedProviderData<>(execution.getTrackingHistory(integrationId, requireOrder(orderId), Optional.empty()));
		}
		public UntrustedProviderData<ShipmentStatus> getCurrentShipmentStatus(long orderId) {
			return new UntrustedProviderData<>(execution.getCurrentShipmentStatus(integrationId, requireOrder(orderId), Optional.empty()));
		}
		public UntrustedProviderData<PackageLocation> getCurrentPackageLocation(long orderId) {
			return new UntrustedProviderData<>(execution.getCurrentPackageLocation(integrationId, requireOrder(orderId), Optional.empty()));
		}
	}
	private static AccessDeniedException denied() { return new AccessDeniedException("Access is denied"); }
}
