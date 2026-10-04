package com.aiorderdeliveryagent.backend.integration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record CreateIntegrationRequest(
		@NotBlank(message = "Provider is required")
		@Size(max = 100, message = "Provider must be at most 100 characters")
		@Pattern(
				regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
				message = "Provider contains unsupported characters")
		String providerKey,

		@NotBlank(message = "Display name is required")
		@Size(max = 200, message = "Display name must be at most 200 characters")
		String displayName,

		@NotBlank(message = "Base URL is required")
		@Size(max = 2048, message = "Base URL must be at most 2048 characters")
		String baseUrl,

		boolean enabled) {
}
