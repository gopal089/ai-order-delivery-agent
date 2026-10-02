package com.aiorderdeliveryagent.backend.integration.order.exception;

public abstract class ExternalOrderProviderException extends RuntimeException {

	protected ExternalOrderProviderException(String message) {
		super(message);
	}

	protected ExternalOrderProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}
