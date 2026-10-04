package com.aiorderdeliveryagent.backend.integration;

final class IntegrationConflictException extends RuntimeException {

	IntegrationConflictException() {
		super("An integration with this provider and display name already exists");
	}
}
