package com.aiorderdeliveryagent.backend.integration.order.tool;

import com.aiorderdeliveryagent.backend.integration.order.model.*;

/**
 * Future model-facing method surface only; not registered with any AI framework or HTTP API.
 * Order IDs are internal numeric IDs from the existing orders table, never URLs or provider selectors.
 * Integration selection and all identity/credential information are bound by the backend factory.
 */
public interface ControlledOrderTools {
	UntrustedProviderData<OrdersResult> getCustomerOrders();
	UntrustedProviderData<ExternalOrderDetails> getOrderDetails(long orderId);
	UntrustedProviderData<TrackingHistory> getTrackingHistory(long orderId);
	UntrustedProviderData<ShipmentStatus> getCurrentShipmentStatus(long orderId);
	UntrustedProviderData<PackageLocation> getCurrentPackageLocation(long orderId);
}
