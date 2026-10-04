package com.aiorderdeliveryagent.backend.integration.transport;

/** Untrusted provider bytes, for backend adapter validation only. Never a prompt or API DTO. */
public final class TransportResponse {
	private final int status;
	private final byte[] body;
	TransportResponse(int status, byte[] body) { this.status = status; this.body = body.clone(); }
	public int status() { return status; }
	public byte[] body() { return body.clone(); }
	@Override public String toString() { return "TransportResponse[status=" + status + ", body=REDACTED]"; }
}
