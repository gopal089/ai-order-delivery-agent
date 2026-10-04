package com.aiorderdeliveryagent.backend.integration.credential;

import java.util.Arrays;

/**
 * Short-lived credential material for trusted backend code only.
 * Instances are closed at the end of the provider operation, including deadline cancellation.
 * Trusted backend code must separately zeroize any char-array copies it makes.
 */
public final class CredentialMaterial implements AutoCloseable {

	private char[] value;

	private CredentialMaterial(char[] value) {
		if (value == null || value.length == 0) {
			throw new IllegalArgumentException("Credential material must not be empty");
		}
		this.value = Arrays.copyOf(value, value.length);
	}

	public static CredentialMaterial copyOf(char[] value) {
		return new CredentialMaterial(value);
	}

	public synchronized char[] copyForTrustedBackend() {
		if (value == null) {
			throw new IllegalStateException("Credential material has been closed");
		}
		return Arrays.copyOf(value, value.length);
	}

	@Override
	public synchronized void close() {
		if (value != null) {
			Arrays.fill(value, '\0');
			value = null;
		}
	}

	@Override
	public String toString() {
		return "CredentialMaterial[REDACTED]";
	}
}
