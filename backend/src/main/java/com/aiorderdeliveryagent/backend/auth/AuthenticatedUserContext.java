package com.aiorderdeliveryagent.backend.auth;

import java.util.UUID;

public record AuthenticatedUserContext(
		long userId,
		UUID tenantId,
		UUID sessionId,
		boolean authenticated) {
}
