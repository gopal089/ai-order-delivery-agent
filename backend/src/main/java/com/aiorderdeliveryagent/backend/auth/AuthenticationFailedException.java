package com.aiorderdeliveryagent.backend.auth;

final class AuthenticationFailedException extends RuntimeException {

	AuthenticationFailedException() {
		super("Authentication failed");
	}
}
