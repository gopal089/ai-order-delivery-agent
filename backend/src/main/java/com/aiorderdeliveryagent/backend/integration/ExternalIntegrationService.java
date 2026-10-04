package com.aiorderdeliveryagent.backend.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.aiorderdeliveryagent.backend.auth.AuthenticatedUserContext;
import com.aiorderdeliveryagent.backend.auth.AuthenticatedUserContextProvider;
import com.aiorderdeliveryagent.backend.auth.TenantDataAuthorizationService;
import com.aiorderdeliveryagent.backend.observability.AuditEventType;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ExternalIntegrationService {

	private static final AuditEventType CREATED_EVENT = AuditEventType.INTEGRATION_CREATED;
	private static final AuditEventType UPDATED_EVENT = AuditEventType.INTEGRATION_UPDATED;
	private static final AuditEventType DELETED_EVENT = AuditEventType.INTEGRATION_DELETED;

	private final ExternalIntegrationRepository repository;
	private final AuthenticatedUserContextProvider contextProvider;
	private final TenantDataAuthorizationService tenantAuthorization;
	private final ExternalBaseUrlValidator baseUrlValidator;
	private final IntegrationAuditService auditService;
	private final Clock clock;

	ExternalIntegrationService(
			ExternalIntegrationRepository repository,
			AuthenticatedUserContextProvider contextProvider,
			TenantDataAuthorizationService tenantAuthorization,
			ExternalBaseUrlValidator baseUrlValidator,
			IntegrationAuditService auditService,
			Clock clock) {
		this.repository = repository;
		this.contextProvider = contextProvider;
		this.tenantAuthorization = tenantAuthorization;
		this.baseUrlValidator = baseUrlValidator;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Transactional
	IntegrationResponse create(CreateIntegrationRequest request) {
		AuthenticatedUserContext context = contextProvider.getCurrentUser();
		Instant now = clock.instant();
		ExternalIntegration integration = new ExternalIntegration(
				context.tenantId(),
				context.userId(),
				normalizeProviderKey(request.providerKey()),
				request.displayName().strip(),
				baseUrlValidator.validateAndNormalize(request.baseUrl()),
				request.enabled(),
				now);

		try {
			ExternalIntegration saved = repository.saveAndFlush(integration);
			auditService.record(CREATED_EVENT, context, saved.getId());
			return IntegrationResponse.from(saved);
		}
		catch (DataIntegrityViolationException exception) {
			throw new IntegrationConflictException();
		}
	}

	@Transactional(readOnly = true)
	List<IntegrationResponse> list() {
		AuthenticatedUserContext context = contextProvider.getCurrentUser();
		return repository.findAllByTenantIdAndUserIdOrderByCreatedAtDescIdDesc(
				context.tenantId(), context.userId()).stream()
				.map(IntegrationResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	IntegrationResponse get(long integrationId) {
		return IntegrationResponse.from(requireOwnedIntegration(integrationId));
	}

	@Transactional
	IntegrationResponse update(long integrationId, UpdateIntegrationRequest request) {
		ExternalIntegration integration = requireOwnedIntegration(integrationId);
		AuthenticatedUserContext context = contextProvider.getCurrentUser();
		String providerKey = request.providerKey() == null
				? null
				: normalizeProviderKey(request.providerKey());
		String displayName = request.displayName() == null ? null : request.displayName().strip();
		String baseUrl = request.baseUrl() == null
				? null
				: baseUrlValidator.validateAndNormalize(request.baseUrl());
		integration.update(providerKey, displayName, baseUrl, request.enabled(), clock.instant());

		try {
			ExternalIntegration saved = repository.saveAndFlush(integration);
			auditService.record(UPDATED_EVENT, context, saved.getId());
			return IntegrationResponse.from(saved);
		}
		catch (DataIntegrityViolationException exception) {
			throw new IntegrationConflictException();
		}
	}

	@Transactional
	void delete(long integrationId) {
		ExternalIntegration integration = requireOwnedIntegration(integrationId);
		AuthenticatedUserContext context = contextProvider.getCurrentUser();
		repository.delete(integration);
		repository.flush();
		auditService.record(DELETED_EVENT, context, integrationId);
	}

	private ExternalIntegration requireOwnedIntegration(long integrationId) {
		tenantAuthorization.requireIntegrationAccess(integrationId);
		AuthenticatedUserContext context = contextProvider.getCurrentUser();
		return repository.findByIdAndTenantIdAndUserId(
				integrationId, context.tenantId(), context.userId())
				.orElseThrow(() -> new AccessDeniedException("Access is denied"));
	}

	private String normalizeProviderKey(String providerKey) {
		return providerKey.strip().toLowerCase(Locale.ROOT);
	}
}
