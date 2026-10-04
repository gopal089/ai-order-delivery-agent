package com.aiorderdeliveryagent.backend.integration;

public final class ProviderExecutionException extends RuntimeException {
	public enum Reason { DISABLED, UNCONFIGURED, CREDENTIAL_STORE_UNAVAILABLE, PROVIDER_UNAVAILABLE,
		AUTHENTICATION_FAILED, ORDER_NOT_FOUND, TRACKING_UNAVAILABLE, UNSUPPORTED, EXECUTION_FAILED,
		TIMEOUT, EXECUTION_LIMIT_EXCEEDED, EXECUTION_BUSY }
	private final Reason reason;
	public ProviderExecutionException(Reason reason) {
		super("Provider execution unavailable: " + reason.name());
		this.reason = reason;
	}
	public Reason reason() { return reason; }
}
