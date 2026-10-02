package com.aiorderdeliveryagent.backend.auth;

final class EmailAlreadyRegisteredException extends RuntimeException {

	EmailAlreadyRegisteredException() {
		super("An account with this email is already registered");
	}
}
