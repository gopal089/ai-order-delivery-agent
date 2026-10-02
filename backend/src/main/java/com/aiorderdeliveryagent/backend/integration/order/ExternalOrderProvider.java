package com.aiorderdeliveryagent.backend.integration.order;

import com.aiorderdeliveryagent.backend.integration.order.model.ExternalOrderDetails;
import com.aiorderdeliveryagent.backend.integration.order.model.GetOrderDetailsRequest;
import com.aiorderdeliveryagent.backend.integration.order.model.GetOrdersRequest;
import com.aiorderdeliveryagent.backend.integration.order.model.GetTrackingRequest;
import com.aiorderdeliveryagent.backend.integration.order.model.OrdersResult;
import com.aiorderdeliveryagent.backend.integration.order.model.PackageLocation;
import com.aiorderdeliveryagent.backend.integration.order.model.ShipmentStatus;
import com.aiorderdeliveryagent.backend.integration.order.model.TrackingHistory;

/**
 * Provider-neutral boundary for reading authoritative order and shipment data.
 *
 * <p>Implementations are responsible for provider-specific transport and credential
 * handling. Credentials deliberately do not form part of this contract.</p>
 */
public interface ExternalOrderProvider {

	OrdersResult getOrders(GetOrdersRequest request);

	ExternalOrderDetails getOrderDetails(GetOrderDetailsRequest request);

	TrackingHistory getTrackingHistory(GetTrackingRequest request);

	ShipmentStatus getCurrentShipmentStatus(GetTrackingRequest request);

	PackageLocation getCurrentPackageLocation(GetTrackingRequest request);
}
