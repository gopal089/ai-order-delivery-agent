package com.aiorderdeliveryagent.backend.auth;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserContextProvider {

	public AuthenticatedUserContext getCurrentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof ApplicationPrincipal principal)) {
			throw new InsufficientAuthenticationException("Authentication is required");
		}

		return new AuthenticatedUserContext(
				principal.userId(),
				principal.tenantId(),
				principal.sessionId(),
				true);
	}
}
