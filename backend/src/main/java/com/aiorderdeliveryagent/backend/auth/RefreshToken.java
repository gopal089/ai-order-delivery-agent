package com.aiorderdeliveryagent.backend.auth;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "tenant_id", nullable = false, updatable = false)
	private UUID tenantId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	RefreshToken(
			UUID tenantId,
			long userId,
			UUID sessionId,
			String tokenHash,
			Instant expiresAt,
			Instant createdAt) {
		this.tenantId = tenantId;
		this.userId = userId;
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	UUID getTenantId() {
		return tenantId;
	}

	long getUserId() {
		return userId;
	}

	UUID getSessionId() {
		return sessionId;
	}

	String getTokenHash() {
		return tokenHash;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}

	boolean isRevoked() {
		return revokedAt != null;
	}

	void revoke(Instant now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}
}
