package com.aiorderdeliveryagent.backend.ai;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import com.aiorderdeliveryagent.backend.integration.order.model.*;
import com.aiorderdeliveryagent.backend.integration.order.tool.UntrustedProviderData;

/** Backend execution metadata, not part of AiModelContract.Response or model request data. */
public record RetrievalEvidence(AiModelContract.Tool tool, long integrationId, OptionalLong accessedOrderId,
		boolean retrieved, Instant completedAt, List<Instant> sourceTimestamps, Optional<String> errorCode) {
	public RetrievalEvidence { sourceTimestamps=List.copyOf(sourceTimestamps); }
	static RetrievalEvidence success(AiModelContract.Tool tool,long integrationId,long orderId,Object result) {
		if(!(result instanceof UntrustedProviderData<?> data)) throw new AiBoundaryException(AiBoundaryException.Reason.TOOL_FAILURE);
		List<Instant> times=switch(tool) {
			case getCustomerOrders -> ((OrdersResult)data.data()).orders().stream().flatMap(o->o.sourceUpdatedAt().stream()).distinct().limit(100).toList();
			case getOrderDetails -> ((ExternalOrderDetails)data.data()).sourceUpdatedAt().stream().toList();
			case getTrackingHistory -> ((TrackingHistory)data.data()).events().stream().map(TrackingEvent::occurredAt).distinct().limit(100).toList();
			case getCurrentShipmentStatus -> List.of(((ShipmentStatus)data.data()).observedAt());
			case getCurrentPackageLocation -> List.of(((PackageLocation)data.data()).observedAt());
		};
		return new RetrievalEvidence(tool,integrationId,orderId==0?OptionalLong.empty():OptionalLong.of(orderId),true,Instant.now(),times,Optional.empty());
	}
	static RetrievalEvidence failure(AiModelContract.Tool tool,long integrationId,String error) {
		// A denied/failed requested ID is not claimed as a successfully accessed order.
		return new RetrievalEvidence(tool,integrationId,OptionalLong.empty(),false,Instant.now(),List.of(),Optional.of(error));
	}
}
