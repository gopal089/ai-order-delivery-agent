package com.aiorderdeliveryagent.backend.auth;

import java.time.Instant;

public record TokenResponse(
		String tokenType,
		String accessToken,
		long accessTokenExpiresInSeconds,
		String refreshToken,
		Instant refreshTokenExpiresAt) {
}
