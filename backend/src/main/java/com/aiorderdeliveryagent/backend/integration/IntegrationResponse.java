package com.aiorderdeliveryagent.backend.integration;

import java.time.Instant;

record IntegrationResponse(
		long id,
		String providerKey,
		String displayName,
		String baseUrl,
		boolean enabled,
		boolean credentialConfigured,
		String credentialType,
		Instant createdAt,
		Instant updatedAt) {

	static IntegrationResponse from(ExternalIntegration integration) {
		return new IntegrationResponse(
				integration.getId(),
				integration.getProviderKey(),
				integration.getDisplayName(),
				integration.getBaseUrl(),
				integration.isEnabled(),
				integration.hasCredential(),
				integration.getCredentialType(),
				integration.getCreatedAt(),
				integration.getUpdatedAt());
	}
}
