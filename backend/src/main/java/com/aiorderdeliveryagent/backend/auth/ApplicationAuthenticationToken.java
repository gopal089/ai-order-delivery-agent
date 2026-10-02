package com.aiorderdeliveryagent.backend.auth;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

final class ApplicationAuthenticationToken extends AbstractAuthenticationToken {

	private final ApplicationPrincipal principal;

	ApplicationAuthenticationToken(ApplicationPrincipal principal) {
		super(List.of());
		this.principal = principal;
		super.setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return "";
	}

	@Override
	public ApplicationPrincipal getPrincipal() {
		return principal;
	}

	@Override
	public void setAuthenticated(boolean authenticated) {
		if (authenticated) {
			throw new IllegalArgumentException("Use the trusted authentication constructor");
		}
		super.setAuthenticated(false);
	}
}
