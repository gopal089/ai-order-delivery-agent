package com.aiorderdeliveryagent.backend.auth;

import java.util.Optional;

import com.aiorderdeliveryagent.backend.auth.TenantOwnedResourceRepository.ResourceOwnership;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantDataAuthorizationService {

	private final TenantOwnedResourceRepository resourceRepository;
	private final TenantAuthorization tenantAuthorization;

	TenantDataAuthorizationService(
			TenantOwnedResourceRepository resourceRepository,
			TenantAuthorization tenantAuthorization) {
		this.resourceRepository = resourceRepository;
		this.tenantAuthorization = tenantAuthorization;
	}

	@Transactional(readOnly = true)
	public void requireIntegrationAccess(long integrationId) {
		requireOwner(resourceRepository.findIntegrationOwnership(integrationId));
	}

	@Transactional(readOnly = true)
	public void requireOrderAccess(long orderId) {
		requireOwner(resourceRepository.findOrderOwnership(orderId));
	}

	@Transactional(readOnly = true)
	public void requireShipmentAccess(long shipmentId) {
		requireOwner(resourceRepository.findShipmentOwnership(shipmentId));
	}

	@Transactional(readOnly = true)
	public void requireTrackingEventAccess(long eventId) {
		requireOwner(resourceRepository.findTrackingEventOwnership(eventId));
	}

	@Transactional(readOnly = true)
	public void requireConversationAccess(long conversationId) {
		requireOwner(resourceRepository.findConversationOwnership(conversationId));
	}

	@Transactional(readOnly = true)
	public void requireMessageAccess(long messageId) {
		requireOwner(resourceRepository.findMessageOwnership(messageId));
	}

	@Transactional(readOnly = true)
	public void requireCredentialAccess(long integrationId) {
		// Provider credentials are intentionally not stored in the application schema.
		// Future secret-store access must first prove ownership of the parent integration.
		requireOwner(resourceRepository.findIntegrationOwnership(integrationId));
	}

	private void requireOwner(Optional<ResourceOwnership> ownership) {
		ResourceOwnership resourceOwnership = ownership.orElseThrow(
				() -> new AccessDeniedException("Access is denied"));
		tenantAuthorization.requireOwner(
				resourceOwnership.tenantId(),
				resourceOwnership.userId());
	}
}
