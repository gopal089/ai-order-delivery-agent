package com.aiorderdeliveryagent.backend.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RefreshTokenRequest(
		@NotBlank(message = "Refresh token is required")
		@Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "Refresh token is invalid")
		String refreshToken) {
}
