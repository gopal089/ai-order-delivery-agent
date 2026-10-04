package com.aiorderdeliveryagent.backend.ai;

public final class AiBoundaryException extends RuntimeException {
	public enum Reason { MODEL_UNAVAILABLE, MODEL_FAILURE, MODEL_TIMEOUT, EXECUTION_TIMEOUT, EXECUTION_BUSY,
		UNKNOWN_TOOL, INVALID_ARGUMENTS, MALFORMED_RESPONSE, TOOL_LIMIT, CONTEXT_TOO_LARGE, TOOL_FAILURE }
	private final Reason reason;
	public AiBoundaryException(Reason reason) { super("AI execution unavailable: " + reason.name()); this.reason = reason; }
	public Reason reason() { return reason; }
}
