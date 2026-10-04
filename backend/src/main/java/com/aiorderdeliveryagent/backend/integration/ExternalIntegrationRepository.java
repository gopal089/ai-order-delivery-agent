package com.aiorderdeliveryagent.backend.integration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ExternalIntegrationRepository extends JpaRepository<ExternalIntegration, Long> {

	List<ExternalIntegration> findAllByTenantIdAndUserIdOrderByCreatedAtDescIdDesc(UUID tenantId, long userId);

	Optional<ExternalIntegration> findByIdAndTenantIdAndUserId(long id, UUID tenantId, long userId);
}
