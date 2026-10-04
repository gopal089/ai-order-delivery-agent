package com.aiorderdeliveryagent.backend.integration.credential;

public record CredentialReference(String value) {

	public CredentialReference {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Credential reference must not be blank");
		}
	}

	@Override
	public String toString() {
		return "CredentialReference[REDACTED]";
	}
}
