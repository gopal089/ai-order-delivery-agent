package com.aiorderdeliveryagent.backend.domain.order;

import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;

final class DomainRequestPolicy {
	private DomainRequestPolicy() { }
	static void id(long id) { if (id <= 0) throw new IllegalArgumentException("Invalid resource ID"); }
	static void check(HttpServletRequest request, String... allowedParameters) {
		if (!Set.of(allowedParameters).containsAll(request.getParameterMap().keySet())
				|| request.getParameterMap().values().stream().anyMatch(values -> values.length != 1)
				|| request.getContentLengthLong() > 0 || request.getHeader("Transfer-Encoding") != null)
			throw new IllegalArgumentException("Invalid domain request");
	}
}
