package com.aiorderdeliveryagent.backend.integration;

import java.time.Instant;
import java.util.Map;

import com.aiorderdeliveryagent.backend.observability.RequestContext;

record IntegrationApiError(
		String code,
		String message,
		Map<String, String> fieldErrors,
		Instant timestamp,
		String requestId) {

	IntegrationApiError(String code, String message, Map<String, String> fieldErrors, Instant timestamp) {
		this(code, message, fieldErrors, timestamp, RequestContext.currentRequestId());
	}
}
