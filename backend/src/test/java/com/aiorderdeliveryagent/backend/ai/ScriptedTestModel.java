package com.aiorderdeliveryagent.backend.ai;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static com.aiorderdeliveryagent.backend.ai.AiModelContract.*;

/** Deterministic TEST ONLY, not a Spring bean, SDK, commercial model, or production fallback. */
public final class ScriptedTestModel implements AiModelProvider {
	public enum Scenario { PLAIN, ORDERS, DETAILS, HISTORY, STATUS, LOCATION, REPEATED, MALFORMED, FAILURE, TIMEOUT }
	private final Scenario scenario;
	private final long orderId;
	public final List<Request> requests=new CopyOnWriteArrayList<>();
	public ScriptedTestModel(Scenario scenario,long orderId) {this.scenario=scenario;this.orderId=orderId;}
	public Response generate(Request request) {
		requests.add(request);
		if(scenario==Scenario.FAILURE) throw new IllegalStateException("synthetic-sensitive-model-marker");
		if(scenario==Scenario.TIMEOUT) {
			try{Thread.sleep(10000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
		}
		if(scenario==Scenario.PLAIN||scenario==Scenario.TIMEOUT||(requests.size()>1&&scenario!=Scenario.REPEATED))
			return new Response(Optional.of("test-only scripted answer"),List.of(),Optional.of(new Metadata("test-only","scripted")),Optional.of(new Usage(1,1)));
		Tool tool=switch(scenario) {
			case ORDERS->Tool.getCustomerOrders;case DETAILS,MALFORMED->Tool.getOrderDetails;
			case HISTORY->Tool.getTrackingHistory;case STATUS->Tool.getCurrentShipmentStatus;
			default->Tool.getCurrentPackageLocation;
		};
		Map<String,Object> args=scenario==Scenario.MALFORMED?Map.of("orderId","https://invalid.example/"):
			tool==Tool.getCustomerOrders?Map.of():Map.of("orderId",orderId);
		return new Response(Optional.empty(),List.of(new ToolCall(tool.name(),args)),Optional.empty(),Optional.empty());
	}
}
