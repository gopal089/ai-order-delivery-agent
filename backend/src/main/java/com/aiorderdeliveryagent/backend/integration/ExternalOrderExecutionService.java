package com.aiorderdeliveryagent.backend.integration;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

import com.aiorderdeliveryagent.backend.auth.AuthenticatedUserContextProvider;
import com.aiorderdeliveryagent.backend.auth.TenantDataAuthorizationService;
import com.aiorderdeliveryagent.backend.integration.credential.*;
import com.aiorderdeliveryagent.backend.integration.order.ExternalOrderProvider;
import com.aiorderdeliveryagent.backend.integration.order.exception.*;
import com.aiorderdeliveryagent.backend.integration.order.model.*;
import com.aiorderdeliveryagent.backend.integration.transport.SecureHttpTransport;
import com.aiorderdeliveryagent.backend.integration.transport.ProviderExecutionBudget;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Five controlled backend operations; no new HTTP/AI tool surface or real adapter is registered. */
@Service
public class ExternalOrderExecutionService {
	private final AuthenticatedUserContextProvider contexts;
	private final TenantDataAuthorizationService authorization;
	private final ExternalIntegrationRepository integrations;
	private final ExternalBaseUrlValidator urls;
	private final ObjectProvider<CredentialStore> stores;
	private final List<BackendOrderProviderAdapter> adapters;
	private final SecureHttpTransport transport;

	ExternalOrderExecutionService(AuthenticatedUserContextProvider contexts,
			TenantDataAuthorizationService authorization, ExternalIntegrationRepository integrations,
			ExternalBaseUrlValidator urls, ObjectProvider<CredentialStore> stores,
			List<BackendOrderProviderAdapter> adapters, SecureHttpTransport transport) {
		this.contexts = contexts;
		this.authorization = authorization;
		this.integrations = integrations;
		this.urls = urls;
		this.stores = stores;
		this.adapters = List.copyOf(adapters);
		this.transport = transport;
	}

	public OrdersResult getOrders(long integrationId) {
		return execute(integrationId, (provider, context) -> provider.getOrders(new GetOrdersRequest(context)));
	}
	public ExternalOrderDetails getOrderDetails(long integrationId, ExternalOrderId orderId) {
		return execute(integrationId, (provider, context) -> {
			var result = Objects.requireNonNull(provider.getOrderDetails(new GetOrderDetailsRequest(context, orderId)));
			requireMatchingOrder(orderId, result.orderId()); return result;
		});
	}
	public TrackingHistory getTrackingHistory(long integrationId, ExternalOrderId orderId, Optional<ExternalShipmentId> shipmentId) {
		return execute(integrationId, (provider, context) -> {
			var result = Objects.requireNonNull(provider.getTrackingHistory(new GetTrackingRequest(context, orderId, shipmentId)));
			requireMatchingTracking(orderId, shipmentId, result.orderId(), result.shipmentId()); return result;
		});
	}
	public ShipmentStatus getCurrentShipmentStatus(long integrationId, ExternalOrderId orderId, Optional<ExternalShipmentId> shipmentId) {
		return execute(integrationId, (provider, context) -> {
			var result = Objects.requireNonNull(provider.getCurrentShipmentStatus(new GetTrackingRequest(context, orderId, shipmentId)));
			requireMatchingTracking(orderId, shipmentId, result.orderId(), result.shipmentId()); return result;
		});
	}
	public PackageLocation getCurrentPackageLocation(long integrationId, ExternalOrderId orderId, Optional<ExternalShipmentId> shipmentId) {
		return execute(integrationId, (provider, context) -> {
			var result = Objects.requireNonNull(provider.getCurrentPackageLocation(new GetTrackingRequest(context, orderId, shipmentId)));
			requireMatchingTracking(orderId, shipmentId, result.orderId(), result.shipmentId()); return result;
		});
	}

	private <T> T execute(long integrationId, BiFunction<ExternalOrderProvider, ExternalOrderProviderContext, T> operation) {
		var user = contexts.getCurrentUser();
		authorization.requireIntegrationAccess(integrationId);
		ExternalIntegration integration = integrations.findByIdAndTenantIdAndUserId(
				integrationId, user.tenantId(), user.userId())
			.orElseThrow(() -> new AccessDeniedException("Access is denied"));
		if (!integration.isEnabled()) throw failure(ProviderExecutionException.Reason.DISABLED);
		if (integration.getBaseUrl() == null || !integration.hasCredential())
			throw failure(ProviderExecutionException.Reason.UNCONFIGURED);
		authorization.requireCredentialAccess(integrationId);
		var matching = adapters.stream().filter(a -> a.providerKey().equals(integration.getProviderKey())).toList();
		if (matching.size() != 1) throw failure(ProviderExecutionException.Reason.PROVIDER_UNAVAILABLE);
		CredentialStore store = stores.getIfAvailable();
		if (store == null) throw failure(ProviderExecutionException.Reason.CREDENTIAL_STORE_UNAVAILABLE);
		var context = new ExternalOrderProviderContext(user.tenantId(), user.userId(), user.sessionId(), integrationId);
		var scope = new CredentialScope(user.tenantId(), user.userId(), integrationId);
		return ProviderExecutionBudget.execute(budget -> executeProvider(budget, store, scope,
			integration, matching.getFirst(), operation, context));
	}

	private <T> T executeProvider(ProviderExecutionBudget budget, CredentialStore store, CredentialScope scope,
			ExternalIntegration integration, BackendOrderProviderAdapter adapter,
			BiFunction<ExternalOrderProvider, ExternalOrderProviderContext, T> operation, ExternalOrderProviderContext context) {
		try (CredentialMaterial material = store.retrieve(scope, new CredentialReference(integration.getCredentialReference()))) {
			if (material == null) throw failure(ProviderExecutionException.Reason.CREDENTIAL_STORE_UNAVAILABLE);
			budget.track(material);
			var provider = adapter.open(
				URI.create(urls.validateAndNormalize(integration.getBaseUrl())), material, transport);
			budget.check();
			T result = Objects.requireNonNull(operation.apply(provider, context));
			budget.check();
			return result;
		}
		catch (ProviderExecutionException exception) { throw exception; }
		catch (ExternalProviderAuthenticationException exception) { throw failure(ProviderExecutionException.Reason.AUTHENTICATION_FAILED); }
		catch (ExternalOrderNotFoundException exception) { throw failure(ProviderExecutionException.Reason.ORDER_NOT_FOUND); }
		catch (TrackingUnavailableException exception) { throw failure(ProviderExecutionException.Reason.TRACKING_UNAVAILABLE); }
		catch (UnsupportedProviderOperationException exception) { throw failure(ProviderExecutionException.Reason.UNSUPPORTED); }
		catch (RuntimeException exception) { throw failure(ProviderExecutionException.Reason.EXECUTION_FAILED); }
	}
	private void requireMatchingOrder(ExternalOrderId requested, ExternalOrderId returned) {
		if (!requested.equals(returned)) throw failure(ProviderExecutionException.Reason.EXECUTION_FAILED);
	}
	private void requireMatchingTracking(ExternalOrderId order, Optional<ExternalShipmentId> shipment,
			ExternalOrderId returnedOrder, Optional<ExternalShipmentId> returnedShipment) {
		requireMatchingOrder(order, returnedOrder);
		if (shipment.isPresent() && !shipment.equals(returnedShipment))
			throw failure(ProviderExecutionException.Reason.EXECUTION_FAILED);
	}
	private ProviderExecutionException failure(ProviderExecutionException.Reason reason) {
		return new ProviderExecutionException(reason);
	}
}
