package com.aiorderdeliveryagent.backend.auth;

class AuthenticationRateLimitUnavailableException extends RuntimeException {

	AuthenticationRateLimitUnavailableException() {
		super("Authentication rate limiting is unavailable");
	}
}
