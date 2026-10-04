package com.aiorderdeliveryagent.backend.integration.transport;

/** Deliberately excludes target URLs, headers, response bodies, and underlying exceptions. */
public final class TransportFailureException extends RuntimeException {
	public enum Reason { POLICY_REJECTED, DNS_REJECTED, TIMEOUT, CONNECTION_FAILED, REDIRECT_REJECTED,
		RESPONSE_TOO_LARGE, ENCODING_REJECTED }
	private final Reason reason;
	public TransportFailureException(Reason reason) {
		super("External transport failed: " + reason.name());
		this.reason = reason;
	}
	public Reason reason() { return reason; }
}
