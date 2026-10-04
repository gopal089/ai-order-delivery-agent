package com.aiorderdeliveryagent.backend.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);
	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(RequestContext.REQUEST_ID_HEADER));
		long startedAt = System.nanoTime();
		MDC.put(RequestContext.REQUEST_ID_MDC_KEY, requestId);
		response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId);

		try {
			filterChain.doFilter(request, response);
		}
		finally {
			if (!request.getRequestURI().startsWith("/actuator/health")) {
				restoreContext(request, "userId", RequestContext.USER_ID_ATTRIBUTE);
				restoreContext(request, "tenantId", RequestContext.TENANT_ID_ATTRIBUTE);
				restoreContext(request, "sessionId", RequestContext.SESSION_ID_ATTRIBUTE);
				var event = LOGGER.atInfo()
						.addKeyValue("method", request.getMethod())
						.addKeyValue("endpoint", request.getRequestURI())
						.addKeyValue("status", response.getStatus())
						.addKeyValue("durationMs", (System.nanoTime() - startedAt) / 1_000_000L);
				event.log("http_request_completed");
			}
			MDC.remove("userId");
			MDC.remove("tenantId");
			MDC.remove("sessionId");
			MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
		}
	}

	private String resolveRequestId(String suppliedRequestId) {
		if (suppliedRequestId != null && SAFE_REQUEST_ID.matcher(suppliedRequestId).matches()) {
			return suppliedRequestId;
		}
		return UUID.randomUUID().toString();
	}

	private void restoreContext(HttpServletRequest request, String mdcKey, String attributeKey) {
		Object value = request.getAttribute(attributeKey);
		if (value != null) {
			MDC.put(mdcKey, value.toString());
		}
	}

}
