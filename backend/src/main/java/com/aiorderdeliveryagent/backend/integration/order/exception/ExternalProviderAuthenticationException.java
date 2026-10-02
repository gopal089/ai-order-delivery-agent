package com.aiorderdeliveryagent.backend.integration.order.exception;

public final class ExternalProviderAuthenticationException extends ExternalOrderProviderException {

	public ExternalProviderAuthenticationException() {
		super("External provider authentication failed");
	}

	public ExternalProviderAuthenticationException(Throwable cause) {
		super("External provider authentication failed", cause);
	}
}
