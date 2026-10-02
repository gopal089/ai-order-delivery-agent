package com.aiorderdeliveryagent.backend.integration.order.exception;

public final class TrackingUnavailableException extends ExternalOrderProviderException {

	public TrackingUnavailableException() {
		super("Tracking information is unavailable");
	}
}
