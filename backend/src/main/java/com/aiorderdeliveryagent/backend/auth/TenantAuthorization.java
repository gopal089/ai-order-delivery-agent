package com.aiorderdeliveryagent.backend.auth;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class TenantAuthorization {

	private final AuthenticatedUserContextProvider contextProvider;

	TenantAuthorization(AuthenticatedUserContextProvider contextProvider) {
		this.contextProvider = contextProvider;
	}

	public void requireTenant(UUID resourceTenantId) {
		AuthenticatedUserContext context = contextProvider.getCurrentUser();
		if (!context.tenantId().equals(resourceTenantId)) {
			throw new AccessDeniedException("Access is denied");
		}
	}

	public void requireOwner(UUID resourceTenantId, long resourceUserId) {
		AuthenticatedUserContext context = contextProvider.getCurrentUser();
		if (!context.tenantId().equals(resourceTenantId) || context.userId() != resourceUserId) {
			throw new AccessDeniedException("Access is denied");
		}
	}
}
