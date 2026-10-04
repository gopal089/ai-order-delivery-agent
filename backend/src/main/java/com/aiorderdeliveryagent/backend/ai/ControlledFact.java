package com.aiorderdeliveryagent.backend.ai;

import java.time.Instant;
import java.util.*;
import com.aiorderdeliveryagent.backend.integration.order.model.*;
import com.aiorderdeliveryagent.backend.integration.order.tool.UntrustedProviderData;

/** Whitelisted normalized fields from an executed tool, never parsed from model prose/JSON. */
public record ControlledFact(int toolSequence, AiModelContract.Tool tool, Field field,
		Optional<String> value, Optional<Instant> sourceTimestamp,AiModelContract.Trust valueTrust) {
	public enum Field { ORDER_STATUS, SHIPMENT_STATUS, TRACKING_STATUS, TRACKING_LOCATION, PACKAGE_LOCATION }
	public ControlledFact(int sequence,AiModelContract.Tool tool,Field field,Optional<String> value,Optional<Instant> timestamp) {
		this(sequence,tool,field,value,timestamp,AiModelContract.Trust.EXTERNAL_UNTRUSTED);
	}
	public ControlledFact {
		Objects.requireNonNull(tool);Objects.requireNonNull(field);Objects.requireNonNull(value);Objects.requireNonNull(sourceTimestamp);
		if(valueTrust!=AiModelContract.Trust.EXTERNAL_UNTRUSTED) throw new IllegalArgumentException("Invalid provider value trust");
		if(toolSequence<0||value.filter(v->v.length()>1000).isPresent()) throw new AiBoundaryException(AiBoundaryException.Reason.CONTEXT_TOO_LARGE);
	}
	@Override public String toString() {return "ControlledFact[EXTERNAL_UNTRUSTED value omitted]";}
	static List<ControlledFact> project(int sequence,AiModelContract.Tool tool,Object result) {
		if(!(result instanceof UntrustedProviderData<?> data)) throw new AiBoundaryException(AiBoundaryException.Reason.TOOL_FAILURE);
		var facts=new ArrayList<ControlledFact>();
		switch(tool) {
			case getCustomerOrders -> {
				var orders=((OrdersResult)data.data()).orders();
				if(orders.size()>100) throw new AiBoundaryException(AiBoundaryException.Reason.CONTEXT_TOO_LARGE);
				for(var order:orders) facts.add(new ControlledFact(sequence,tool,Field.ORDER_STATUS,order.status(),order.sourceUpdatedAt()));
			}
			case getOrderDetails -> {var order=(ExternalOrderDetails)data.data();facts.add(new ControlledFact(sequence,tool,Field.ORDER_STATUS,order.status(),order.sourceUpdatedAt()));}
			case getTrackingHistory -> {
				var events=((TrackingHistory)data.data()).events();
				if(events.size()>50) throw new AiBoundaryException(AiBoundaryException.Reason.CONTEXT_TOO_LARGE);
				for(var event:events) {
					facts.add(new ControlledFact(sequence,tool,Field.TRACKING_STATUS,Optional.of(event.status()),Optional.of(event.occurredAt())));
					facts.add(new ControlledFact(sequence,tool,Field.TRACKING_LOCATION,event.location(),Optional.of(event.occurredAt())));
				}
			}
			case getCurrentShipmentStatus -> {var status=(ShipmentStatus)data.data();facts.add(new ControlledFact(sequence,tool,Field.SHIPMENT_STATUS,Optional.of(status.status()),Optional.of(status.observedAt())));}
			case getCurrentPackageLocation -> {var location=(PackageLocation)data.data();facts.add(new ControlledFact(sequence,tool,Field.PACKAGE_LOCATION,Optional.of(location.location()),Optional.of(location.observedAt())));}
		}
		return List.copyOf(facts);
	}
}
