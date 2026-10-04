package com.aiorderdeliveryagent.backend.integration.credential;

/**
 * Backend-only boundary for a future approved secret store.
 * Implementations must never persist raw material in the application database.
 * Every operation must verify the complete scope against stored ownership, not merely trust a
 * reference. Inputs remain caller-owned; retrieved material is owned and closed by the caller.
 * Lifecycle orchestration must authorize through TenantDataAuthorizationService before calling
 * this boundary. Secret values/references must never be logged or placed in audit metadata.
 */
public interface CredentialStore {

	CredentialReference store(CredentialScope scope, CredentialMaterial material);

	CredentialMaterial retrieve(CredentialScope scope, CredentialReference reference);

	/**
	 * Replace a credential within the same ownership scope. On success the old reference must
	 * no longer retrieve material; on failure the old credential must remain usable. Return an
	 * opaque replacement reference. Implementations lacking safe rotation fail closed.
	 * Cross-store/database coordination and audit recovery belong to future lifecycle orchestration.
	 */
	default CredentialReference rotate(CredentialScope scope, CredentialReference previous, CredentialMaterial replacement) {
		throw new UnsupportedOperationException("Credential rotation is unavailable");
	}

	void delete(CredentialScope scope, CredentialReference reference);
}
