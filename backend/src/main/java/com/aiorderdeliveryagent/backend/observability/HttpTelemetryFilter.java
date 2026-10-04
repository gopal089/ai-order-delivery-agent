package com.aiorderdeliveryagent.backend.observability;

import java.io.IOException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import io.opentelemetry.context.Context;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class HttpTelemetryFilter extends OncePerRequestFilter {
	private final SafeTelemetry telemetry;
	private final SecuritySignals signals;
	public HttpTelemetryFilter(SafeTelemetry telemetry, SecuritySignals signals) { this.telemetry=telemetry;this.signals=signals; }
	@Override protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/actuator/health");
	}
	@Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		// Public ingress cannot inject a parent trace, baggage or sampling decisions.
		try (var root=Context.root().makeCurrent(); var span=telemetry.start(SafeTelemetry.Operation.HTTP)) {
			boolean failed=false;
			try { chain.doFilter(request,response); }
			catch (ServletException | IOException | RuntimeException | Error failure) { failed=true;span.failed();throw failure; }
			finally {
				span.http(request.getMethod(),request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE),failed ? 500 : response.getStatus());
				signals.http(failed ? 500 : response.getStatus());
			}
		}
	}
}
