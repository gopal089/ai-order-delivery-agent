package com.aiorderdeliveryagent.backend.observability;

import org.slf4j.MDC;

public final class RequestContext {

	public static final String REQUEST_ID_HEADER = "X-Request-ID";
	public static final String REQUEST_ID_MDC_KEY = "requestId";
	static final String USER_ID_ATTRIBUTE = RequestContext.class.getName() + ".userId";
	static final String TENANT_ID_ATTRIBUTE = RequestContext.class.getName() + ".tenantId";
	static final String SESSION_ID_ATTRIBUTE = RequestContext.class.getName() + ".sessionId";

	private RequestContext() {
	}

	public static String currentRequestId() {
		return MDC.get(REQUEST_ID_MDC_KEY);
	}
}
