package com.aiorderdeliveryagent.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "Email is required")
		@Email(message = "Email must be valid")
		@Size(max = 320, message = "Email must be at most 320 characters")
		String email,

		@NotBlank(message = "Password is required")
		@Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
		@Pattern(
				regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).+$",
				message = "Password must include uppercase, lowercase, number, and special characters")
		String password) {
}
