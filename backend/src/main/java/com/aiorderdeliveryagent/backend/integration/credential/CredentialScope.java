package com.aiorderdeliveryagent.backend.integration.credential;

import java.util.Objects;
import java.util.UUID;

public record CredentialScope(UUID tenantId, long userId, long integrationId) {

	public CredentialScope {
		Objects.requireNonNull(tenantId, "tenantId must not be null");
		if (userId <= 0 || integrationId <= 0) {
			throw new IllegalArgumentException("User and integration identifiers must be positive");
		}
	}
}
