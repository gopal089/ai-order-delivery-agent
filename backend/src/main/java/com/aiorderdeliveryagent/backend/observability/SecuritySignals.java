package com.aiorderdeliveryagent.backend.observability;

import java.util.Map;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Bounded observations only; no aggregation, alert delivery or incident assertion. */
@Component
public class SecuritySignals {
	public enum Event { HTTP_UNAUTHENTICATED, HTTP_FORBIDDEN, HTTP_RATE_LIMITED, HTTP_SERVER_ERROR,
		AUTHORIZATION_DENIED, PROVIDER_FAILURE, PROVIDER_AUTHENTICATION_FAILURE, AI_BOUNDARY_FAILURE,
		AI_TOOL_LIMIT, AI_UNKNOWN_TOOL, AI_MODEL_TIMEOUT, AI_EXECUTION_TIMEOUT, INTEGRATION_URL_REJECTED, AUDIT_WRITE_FAILURE }
	private final SafeSecurityLogger logger;
	public SecuritySignals(SafeSecurityLogger logger) { this.logger=logger; }
	public void observed(Event event) {
		var previous=MDC.getCopyOfContextMap();
		try {
			MDC.clear(); // These aggregatable signals contain no high-cardinality/restricted MDC identity.
			logger.warn(LoggerFactory.getLogger(SecuritySignals.class), "security_signal_observed",
				Map.of("eventType", event.name(), "signalKind", "OBSERVATION"));
		} finally { if(previous==null) MDC.clear(); else MDC.setContextMap(previous); }
	}
	public void http(int status) {
		if (status == 401) observed(Event.HTTP_UNAUTHENTICATED);
		else if (status == 403) observed(Event.HTTP_FORBIDDEN);
		else if (status == 429) observed(Event.HTTP_RATE_LIMITED);
		else if (status >= 500) observed(Event.HTTP_SERVER_ERROR);
	}
}
