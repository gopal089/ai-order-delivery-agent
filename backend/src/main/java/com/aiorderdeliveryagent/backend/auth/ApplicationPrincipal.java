package com.aiorderdeliveryagent.backend.auth;

import java.util.UUID;

public record ApplicationPrincipal(long userId, UUID tenantId, UUID sessionId) {
}
