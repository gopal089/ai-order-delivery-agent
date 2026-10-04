package com.aiorderdeliveryagent.backend.observability;

import java.util.Set;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Component;

/** Typed allowlist, not a generic attribute/payload or exception recorder. */
@Component
public class SafeTelemetry {
	public enum Operation { HTTP, PROVIDER, INTEGRATION_URL_POLICY, DATABASE_HISTORY, REDIS_RATE_LIMIT, AI_ORCHESTRATION, MODEL_WAIT, TOOL_WAIT, AUTHORIZATION, AUDIT }
	private static final Set<String> METHODS = Set.of("GET","POST","PATCH","DELETE","PUT","HEAD","OPTIONS");
	private static final Set<String> ROUTES = Set.of("/api/v1/auth/register","/api/v1/auth/login",
		"/api/v1/auth/refresh","/api/v1/auth/logout","/api/v1/integrations","/api/v1/integrations/{integrationId}",
		"/api/v1/orders","/api/v1/orders/{orderId}","/api/v1/shipments/{shipmentId}","/api/v1/conversations","/api/v1/conversations/{conversationId}/messages",
		"/api/v1/shipments/{shipmentId}/tracking-history","/api/v1/shipments/{shipmentId}/current-status",
		"/api/v1/shipments/{shipmentId}/current-location");
	private final Tracer tracer;
	public SafeTelemetry(OpenTelemetry telemetry) { tracer = telemetry.getTracer("ai-order-delivery-agent.safe-boundaries"); }
	public Activity start(Operation operation) {
		return new Activity(tracer.spanBuilder("application." + operation.name().toLowerCase(java.util.Locale.ROOT))
			.setSpanKind(operation == Operation.HTTP ? SpanKind.SERVER : SpanKind.INTERNAL).startSpan());
	}
	public final class Activity implements AutoCloseable {
		private final Span span;
		private final Scope scope;
		private Activity(Span span) { this.span=span; scope=span.makeCurrent(); }
		public void http(String method, Object route, int status) {
			span.setAttribute("http.request.method", METHODS.contains(method) ? method : "OTHER");
			span.setAttribute("http.route", route instanceof String text && ROUTES.contains(text) ? text : "UNMATCHED");
			span.setAttribute("http.response.status_code", status >= 100 && status <= 599 ? status : 0);
			if (status >= 500) failed();
		}
		public void failed() { span.setStatus(StatusCode.ERROR); } // No description, recordException or stack trace.
		@Override public void close() { try { scope.close(); } finally { span.end(); } }
	}
}
