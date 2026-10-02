package com.aiorderdeliveryagent.backend.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class TenantOwnedResourceRepository {

	private final JdbcTemplate jdbcTemplate;

	TenantOwnedResourceRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	Optional<ResourceOwnership> findIntegrationOwnership(long resourceId) {
		return findOwnership("""
				SELECT tenant_id, user_id
				FROM public.integrations
				WHERE id = ?
				""", resourceId);
	}

	Optional<ResourceOwnership> findOrderOwnership(long resourceId) {
		return findOwnership("""
				SELECT tenant_id, user_id
				FROM public.orders
				WHERE id = ?
				""", resourceId);
	}

	Optional<ResourceOwnership> findConversationOwnership(long resourceId) {
		return findOwnership("""
				SELECT tenant_id, user_id
				FROM public.conversations
				WHERE id = ?
				""", resourceId);
	}

	Optional<ResourceOwnership> findMessageOwnership(long resourceId) {
		return findOwnership("""
				SELECT tenant_id, user_id
				FROM public.messages
				WHERE id = ?
				""", resourceId);
	}

	private Optional<ResourceOwnership> findOwnership(String sql, long resourceId) {
		return jdbcTemplate.query(
				sql,
				(resultSet, rowNumber) -> new ResourceOwnership(
						resultSet.getObject("tenant_id", UUID.class),
						resultSet.getLong("user_id")),
				resourceId).stream().findFirst();
	}

	record ResourceOwnership(UUID tenantId, long userId) {
	}
}
