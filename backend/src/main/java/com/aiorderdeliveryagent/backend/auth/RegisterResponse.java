package com.aiorderdeliveryagent.backend.auth;

import java.time.Instant;

public record RegisterResponse(long id, String email, Instant createdAt) {
}
