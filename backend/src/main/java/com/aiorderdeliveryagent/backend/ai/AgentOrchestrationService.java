package com.aiorderdeliveryagent.backend.ai;

import java.time.Duration;
import java.util.*;
import com.aiorderdeliveryagent.backend.auth.AuthenticatedUserContextProvider;
import com.aiorderdeliveryagent.backend.integration.ControlledOrderToolFactory;
import com.aiorderdeliveryagent.backend.integration.ProviderExecutionException;
import com.aiorderdeliveryagent.backend.integration.order.tool.ControlledOrderTools;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import static com.aiorderdeliveryagent.backend.ai.AiModelContract.*;

/** Backend-only read orchestration, no HTTP endpoint, production model, history writes or AI SDK. */
@Service
public class AgentOrchestrationService {
	private com.aiorderdeliveryagent.backend.observability.SafeTelemetry telemetry =
		new com.aiorderdeliveryagent.backend.observability.SafeTelemetry(io.opentelemetry.api.OpenTelemetry.noop());
	@Autowired
	public void setTelemetry(com.aiorderdeliveryagent.backend.observability.SafeTelemetry telemetry) { this.telemetry=telemetry; }
	static final String INSTRUCTIONS=SystemInstructions.TEXT;
	private final AuthenticatedUserContextProvider contexts;
	private final ConversationContextService conversations;
	private final ControlledOrderToolFactory tools;
	private final ObjectProvider<AiModelProvider> models;
	private final ObjectMapper json;
	private final Duration modelTimeout,toolTimeout,totalTimeout;
	@Autowired
	public AgentOrchestrationService(AuthenticatedUserContextProvider contexts,ConversationContextService conversations,
			ControlledOrderToolFactory tools,ObjectProvider<AiModelProvider> models,ObjectMapper json) {
		this(contexts,conversations,tools,models,json,Duration.ofSeconds(5),Duration.ofSeconds(20),Duration.ofSeconds(30));
	}
	AgentOrchestrationService(AuthenticatedUserContextProvider contexts,ConversationContextService conversations,
			ControlledOrderToolFactory tools,ObjectProvider<AiModelProvider> models,ObjectMapper json,Duration modelTimeout,Duration toolTimeout,Duration totalTimeout) {
		this.contexts=contexts;this.conversations=conversations;this.tools=tools;this.models=models;this.json=json;
		this.modelTimeout=modelTimeout;this.toolTimeout=toolTimeout;this.totalTimeout=totalTimeout;
	}
	public Result execute(long conversationId,long integrationId,String currentMessage) {
		contexts.getCurrentUser();
		var history=conversations.getHistory(conversationId);
		var bound=tools.bind(integrationId); // Caller selects owned integration; model never sees/changes it.
		AiModelProvider model;
		try { model=models.getIfAvailable(); } catch(RuntimeException failure) { throw new AiBoundaryException(AiBoundaryException.Reason.MODEL_UNAVAILABLE); }
		if(model==null) throw new AiBoundaryException(AiBoundaryException.Reason.MODEL_UNAVAILABLE);
		long deadline=System.nanoTime()+totalTimeout.toNanos();
		var evidence=new ArrayList<ToolResult>();var provenance=new ArrayList<RetrievalEvidence>();var facts=new ArrayList<ControlledFact>();int count=0;
		while(true) {
			var request=new Request(INSTRUCTIONS,currentMessage,history,List.of(Tool.values()),evidence);
			var response=timed(com.aiorderdeliveryagent.backend.observability.SafeTelemetry.Operation.MODEL_WAIT,
				()->model.generate(request),remaining(deadline,modelTimeout),false);
			if(response==null || (response.text().isPresent()==!response.toolCalls().isEmpty())
					|| response.text().filter(String::isBlank).isPresent()) throw new AiBoundaryException(AiBoundaryException.Reason.MALFORMED_RESPONSE);
			if(response.toolCalls().isEmpty()) return new Result(response.text().orElseThrow(),evidence,response.metadata(),response.usage(),Trust.MODEL_GENERATED_UNVERIFIED,provenance,facts);
			for(var call:response.toolCalls()) {
				if(++count>3) throw new AiBoundaryException(AiBoundaryException.Reason.TOOL_LIMIT);
				Tool tool;
				try {tool=Tool.valueOf(call.name());}catch(IllegalArgumentException failure){throw new AiBoundaryException(AiBoundaryException.Reason.UNKNOWN_TOOL);}
				long id=arguments(tool,call.arguments());
				try {
					Object data=timed(com.aiorderdeliveryagent.backend.observability.SafeTelemetry.Operation.TOOL_WAIT,
						()->invoke(bound,tool,id),remaining(deadline,toolTimeout),true);
					var retrieval=RetrievalEvidence.success(tool,integrationId,id,data);
					var projected=ControlledFact.project(provenance.size(),tool,data);
					evidence.add(new ToolResult(tool.name(),true,json.writeValueAsString(data),Optional.empty(),Trust.EXTERNAL_UNTRUSTED));
					provenance.add(retrieval);
					facts.addAll(projected);
				}
				catch(AccessDeniedException failure) {evidence.add(failed(tool,"ACCESS_DENIED"));}
				catch(ProviderExecutionException failure) {evidence.add(failed(tool,"PROVIDER_UNAVAILABLE"));}
				catch(AiBoundaryException failure) {
					if(failure.reason()==AiBoundaryException.Reason.EXECUTION_TIMEOUT) evidence.add(failed(tool,"TOOL_TIMEOUT"));
					else if(failure.reason()==AiBoundaryException.Reason.TOOL_FAILURE) evidence.add(failed(tool,"TOOL_FAILED"));
					else if(failure.reason()==AiBoundaryException.Reason.CONTEXT_TOO_LARGE) evidence.add(failed(tool,"TOOL_RESULT_TOO_LARGE"));else throw failure;
				}
				catch(RuntimeException failure) {evidence.add(failed(tool,"TOOL_FAILED"));}
				if(provenance.size()<evidence.size()) provenance.add(RetrievalEvidence.failure(tool,integrationId,evidence.getLast().errorCode().orElse("TOOL_FAILED")));
			}
		}
	}
	private <T> T timed(com.aiorderdeliveryagent.backend.observability.SafeTelemetry.Operation operation,
			java.util.concurrent.Callable<T> task, Duration timeout, boolean tool) {
		try(var span=telemetry.start(operation)) {
			try { return AiExecutionGuard.call(task,timeout,tool); }
			catch(RuntimeException failure) { span.failed();throw failure; }
		}
	}
	private Duration remaining(long deadline,Duration max) {
		long remaining=deadline-System.nanoTime();if(remaining<=0) throw new AiBoundaryException(AiBoundaryException.Reason.EXECUTION_TIMEOUT);
		return Duration.ofNanos(Math.min(remaining,max.toNanos()));
	}
	private ToolResult failed(Tool tool,String code) {return new ToolResult(tool.name(),false,"",Optional.of(code),Trust.EXTERNAL_UNTRUSTED);}
	private long arguments(Tool tool,Map<String,Object> arguments) {
		if(tool==Tool.getCustomerOrders) {if(!arguments.isEmpty()) throw new AiBoundaryException(AiBoundaryException.Reason.INVALID_ARGUMENTS);return 0;}
		if(!arguments.keySet().equals(Set.of("orderId"))) throw new AiBoundaryException(AiBoundaryException.Reason.INVALID_ARGUMENTS);
		Object value=arguments.get("orderId");if(!(value instanceof Long||value instanceof Integer)||((Number)value).longValue()<=0) throw new AiBoundaryException(AiBoundaryException.Reason.INVALID_ARGUMENTS);
		return ((Number)value).longValue();
	}
	private Object invoke(ControlledOrderTools bound,Tool tool,long id) {
		return switch(tool) {
			case getCustomerOrders->bound.getCustomerOrders();case getOrderDetails->bound.getOrderDetails(id);
			case getTrackingHistory->bound.getTrackingHistory(id);case getCurrentShipmentStatus->bound.getCurrentShipmentStatus(id);
			case getCurrentPackageLocation->bound.getCurrentPackageLocation(id);
		};
	}
}
