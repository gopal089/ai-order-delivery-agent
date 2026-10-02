package com.aiorderdeliveryagent.backend.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select token from RefreshToken token where token.tokenHash = :tokenHash")
	Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update RefreshToken token
			set token.revokedAt = :revokedAt
			where token.tenantId = :tenantId
			  and token.userId = :userId
			  and token.sessionId = :sessionId
			  and token.revokedAt is null
			""")
	int revokeSession(
			@Param("tenantId") UUID tenantId,
			@Param("userId") long userId,
			@Param("sessionId") UUID sessionId,
			@Param("revokedAt") Instant revokedAt);
}
