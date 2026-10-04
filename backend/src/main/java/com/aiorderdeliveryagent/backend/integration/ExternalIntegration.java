package com.aiorderdeliveryagent.backend.integration;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "integrations")
class ExternalIntegration {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "provider_key", nullable = false)
	private String providerKey;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "base_url")
	private String baseUrl;

	@Column(name = "credential_reference")
	private String credentialReference;

	@Column(name = "credential_type")
	private String credentialType;

	@Column(name = "is_enabled", nullable = false)
	private boolean enabled;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ExternalIntegration() {
	}

	ExternalIntegration(
			UUID tenantId,
			long userId,
			String providerKey,
			String displayName,
			String baseUrl,
			boolean enabled,
			Instant now) {
		this.tenantId = tenantId;
		this.userId = userId;
		this.providerKey = providerKey;
		this.displayName = displayName;
		this.baseUrl = baseUrl;
		this.enabled = enabled;
		this.createdAt = now;
		this.updatedAt = now;
	}

	void update(String providerKey, String displayName, String baseUrl, Boolean enabled, Instant now) {
		if (providerKey != null) {
			this.providerKey = providerKey;
		}
		if (displayName != null) {
			this.displayName = displayName;
		}
		if (baseUrl != null) {
			this.baseUrl = baseUrl;
		}
		if (enabled != null) {
			this.enabled = enabled;
		}
		this.updatedAt = now;
	}

	Long getId() {
		return id;
	}

	UUID getTenantId() {
		return tenantId;
	}

	Long getUserId() {
		return userId;
	}

	String getProviderKey() {
		return providerKey;
	}

	String getDisplayName() {
		return displayName;
	}

	String getBaseUrl() {
		return baseUrl;
	}

	boolean hasCredential() {
		return credentialReference != null;
	}

	// Backend execution only; deliberately absent from API response DTOs.
	String getCredentialReference() {
		return credentialReference;
	}

	String getCredentialType() {
		return credentialType;
	}

	boolean isEnabled() {
		return enabled;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}
}
