package com.aiorderdeliveryagent.backend.auth;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;

@Component
class ClientAddressResolver {

	String resolve(HttpServletRequest request) {
		String remoteAddress = request.getRemoteAddr();
		if (remoteAddress == null || remoteAddress.isBlank()) {
			return "unknown";
		}
		return remoteAddress.strip().toLowerCase(Locale.ROOT);
	}
}
