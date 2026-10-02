package com.aiorderdeliveryagent.backend.integration.order.exception;

public final class ExternalOrderNotFoundException extends ExternalOrderProviderException {

	public ExternalOrderNotFoundException() {
		super("External order was not found");
	}
}
