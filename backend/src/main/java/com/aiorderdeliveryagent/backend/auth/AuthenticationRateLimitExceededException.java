package com.aiorderdeliveryagent.backend.auth;

class AuthenticationRateLimitExceededException extends RuntimeException {

	private final long retryAfterSeconds;

	AuthenticationRateLimitExceededException(long retryAfterSeconds) {
		super("Authentication rate limit exceeded");
		this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
	}

	long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
