package com.aiorderdeliveryagent.backend.integration;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record UpdateIntegrationRequest(
		@Size(min = 1, max = 100, message = "Provider must be between 1 and 100 characters")
		@Pattern(
				regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
				message = "Provider contains unsupported characters")
		String providerKey,

		@Size(min = 1, max = 200, message = "Display name must be between 1 and 200 characters")
		String displayName,

		@Size(min = 1, max = 2048, message = "Base URL must be between 1 and 2048 characters")
		String baseUrl,

		Boolean enabled) {
}
