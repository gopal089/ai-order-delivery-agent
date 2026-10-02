package com.aiorderdeliveryagent.backend.integration.order.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Trusted server-side identity and integration selection for one provider call.
 * This context must be constructed only after authentication and ownership checks.
 */
public record ExternalOrderProviderContext(
		UUID tenantId,
		long userId,
		UUID sessionId,
		long integrationId) {

	public ExternalOrderProviderContext {
		Objects.requireNonNull(tenantId, "tenantId must not be null");
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		if (userId <= 0) {
			throw new IllegalArgumentException("userId must be positive");
		}
		if (integrationId <= 0) {
			throw new IllegalArgumentException("integrationId must be positive");
		}
	}
}
