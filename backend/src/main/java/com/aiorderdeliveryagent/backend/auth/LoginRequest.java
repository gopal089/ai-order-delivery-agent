package com.aiorderdeliveryagent.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Email must be valid")
		@Size(max = 320, message = "Email must be at most 320 characters")
		String email,

		@NotBlank(message = "Password is required")
		@Size(max = 128, message = "Password must be at most 128 characters")
		String password) {
}
