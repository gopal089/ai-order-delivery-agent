package com.aiorderdeliveryagent.backend.integration.order.exception;

public final class UnsupportedProviderOperationException extends ExternalOrderProviderException {

	public UnsupportedProviderOperationException() {
		super("The external provider does not support this operation");
	}
}
