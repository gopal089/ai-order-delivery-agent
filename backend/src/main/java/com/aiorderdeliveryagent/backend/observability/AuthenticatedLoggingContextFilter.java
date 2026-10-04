package com.aiorderdeliveryagent.backend.observability;

import java.io.IOException;

import com.aiorderdeliveryagent.backend.auth.ApplicationPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthenticatedLoggingContextFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof ApplicationPrincipal principal)) {
			filterChain.doFilter(request, response);
			return;
		}

		putContext(request, "userId", RequestContext.USER_ID_ATTRIBUTE, principal.userId());
		putContext(request, "tenantId", RequestContext.TENANT_ID_ATTRIBUTE, principal.tenantId());
		putContext(request, "sessionId", RequestContext.SESSION_ID_ATTRIBUTE, principal.sessionId());
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			MDC.remove("userId");
			MDC.remove("tenantId");
			MDC.remove("sessionId");
		}
	}

	private void putContext(HttpServletRequest request, String mdcKey, String attributeKey, Object value) {
		String safeValue = value.toString();
		MDC.put(mdcKey, safeValue);
		request.setAttribute(attributeKey, safeValue);
	}
}
