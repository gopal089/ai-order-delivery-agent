package com.aiorderdeliveryagent.backend.integration.order.exception;

public final class ExternalApiException extends ExternalOrderProviderException {

	private final boolean retryable;

	public ExternalApiException(boolean retryable) {
		super("External provider request failed");
		this.retryable = retryable;
	}

	public ExternalApiException(boolean retryable, Throwable cause) {
		super("External provider request failed", cause);
		this.retryable = retryable;
	}

	public boolean isRetryable() {
		return retryable;
	}
}
