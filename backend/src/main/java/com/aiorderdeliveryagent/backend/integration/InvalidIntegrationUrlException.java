package com.aiorderdeliveryagent.backend.integration;

final class InvalidIntegrationUrlException extends RuntimeException {

	InvalidIntegrationUrlException() {
		super("Integration base URL is not allowed");
	}
}
