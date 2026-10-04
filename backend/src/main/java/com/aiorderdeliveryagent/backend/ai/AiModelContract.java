package com.aiorderdeliveryagent.backend.ai;

import java.time.Instant;
import java.util.*;

/** Structural separation, not a prompt-injection detector or a claim of model factual correctness. */
public final class AiModelContract {
	private AiModelContract() { }
	public enum Tool { getCustomerOrders, getOrderDetails, getTrackingHistory, getCurrentShipmentStatus, getCurrentPackageLocation }
	public enum ContextRole { USER, ASSISTANT, OTHER }
	public enum Trust { UNTRUSTED_HISTORY, EXTERNAL_UNTRUSTED, MODEL_GENERATED_UNVERIFIED }
	public record HistoryText(ContextRole role, String text, Instant createdAt, Trust trust) {
		public HistoryText {
			Objects.requireNonNull(role); Objects.requireNonNull(createdAt);
			text = bounded(text, role==ContextRole.ASSISTANT?8192:4000); if (trust != Trust.UNTRUSTED_HISTORY) throw new IllegalArgumentException("Invalid history trust");
		}
		@Override public String toString() { return "HistoryText[UNTRUSTED_HISTORY, content omitted]"; }
	}
	public record History(List<HistoryText> messages, boolean truncated) {
		public History { messages = List.copyOf(messages); if (messages.size()>20) throw new AiBoundaryException(AiBoundaryException.Reason.CONTEXT_TOO_LARGE); }
	}
	public record ToolResult(String tool, boolean success, String data, Optional<String> errorCode, Trust trust) {
		public ToolResult { data = bounded(data, 32768); errorCode = Objects.requireNonNull(errorCode); if(trust != Trust.EXTERNAL_UNTRUSTED) throw new IllegalArgumentException("Invalid tool trust"); }
		@Override public String toString() { return "ToolResult[EXTERNAL_UNTRUSTED, content omitted]"; }
	}
	public record Request(String systemInstructions, String currentUserMessage, History history,
			List<Tool> allowedTools, List<ToolResult> toolResults) {
		public Request {
			systemInstructions = bounded(systemInstructions, 4000); currentUserMessage = bounded(currentUserMessage, 4000);
			Objects.requireNonNull(history); allowedTools=List.copyOf(allowedTools); toolResults=List.copyOf(toolResults);
			int size = systemInstructions.length()+currentUserMessage.length();
			for(var m:history.messages()) size+=m.text().length(); for(var r:toolResults) size+=r.data().length();
			if(size>65536) throw new AiBoundaryException(AiBoundaryException.Reason.CONTEXT_TOO_LARGE);
		}
		@Override public String toString() { return "AiModelRequest[content omitted]"; }
	}
	public record ToolCall(String name, Map<String,Object> arguments) {
		public ToolCall { name=bounded(name,128); arguments=Map.copyOf(arguments); }
	}
	public record Metadata(String provider, String model) { public Metadata { provider=bounded(provider,128); model=bounded(model,128); } }
	public record Usage(long inputTokens, long outputTokens) { public Usage { if(inputTokens<0||outputTokens<0) throw new IllegalArgumentException("Invalid usage"); } }
	public record Response(Optional<String> text, List<ToolCall> toolCalls, Optional<Metadata> metadata, Optional<Usage> usage) {
		public Response {
			Objects.requireNonNull(text); text.ifPresent(value->bounded(value,8192)); toolCalls=List.copyOf(toolCalls);
			Objects.requireNonNull(metadata); Objects.requireNonNull(usage);
			if(toolCalls.size()>3) throw new AiBoundaryException(AiBoundaryException.Reason.TOOL_LIMIT);
		}
		@Override public String toString() { return "AiModelResponse[MODEL_GENERATED_UNVERIFIED, content omitted]"; }
	}
	public record Result(String text, List<ToolResult> evidence, Optional<Metadata> metadata, Optional<Usage> finalCallUsage, Trust trust,
			List<RetrievalEvidence> provenance, List<ControlledFact> facts) {
		public Result(String text,List<ToolResult> evidence,Optional<Metadata> metadata,Optional<Usage> usage,Trust trust,List<RetrievalEvidence> provenance) {
			this(text,evidence,metadata,usage,trust,provenance,List.of());
		}
		public Result { text=bounded(text,8192); evidence=List.copyOf(evidence); provenance=List.copyOf(provenance); facts=List.copyOf(facts); if(trust!=Trust.MODEL_GENERATED_UNVERIFIED) throw new IllegalArgumentException("Invalid answer trust"); }
		@Override public String toString() { return "AiAgentResult[MODEL_GENERATED_UNVERIFIED, content omitted]"; }
	}
	private static String bounded(String text, int max) {
		if(text==null||text.length()>max) throw new AiBoundaryException(AiBoundaryException.Reason.CONTEXT_TOO_LARGE); return text;
	}
}
