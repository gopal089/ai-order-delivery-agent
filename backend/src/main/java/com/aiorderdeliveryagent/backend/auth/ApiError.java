package com.aiorderdeliveryagent.backend.auth;

import java.time.Instant;
import java.util.Map;

import com.aiorderdeliveryagent.backend.observability.RequestContext;

record ApiError(
		String code,
		String message,
		Map<String, String> fieldErrors,
		Instant timestamp,
		String requestId) {

	ApiError(String code, String message, Map<String, String> fieldErrors, Instant timestamp) {
		this(code, message, fieldErrors, timestamp, RequestContext.currentRequestId());
	}
}
