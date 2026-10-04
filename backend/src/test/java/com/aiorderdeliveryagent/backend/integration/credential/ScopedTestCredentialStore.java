package com.aiorderdeliveryagent.backend.integration.credential;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;

/** In-memory lifecycle CONTRACT FIXTURE ONLY. Never registered as a production store. */
public final class ScopedTestCredentialStore implements CredentialStore, AutoCloseable {
	private record Entry(CredentialScope scope, char[] value) { }
	private final Map<CredentialReference, Entry> entries = new HashMap<>();
	private int sequence;
	public synchronized CredentialReference store(CredentialScope scope, CredentialMaterial material) {
		var reference = new CredentialReference("test-reference-" + ++sequence);
		entries.put(reference, new Entry(scope, material.copyForTrustedBackend()));
		return reference;
	}
	public synchronized CredentialMaterial retrieve(CredentialScope scope, CredentialReference reference) {
		return CredentialMaterial.copyOf(requireOwned(scope, reference).value());
	}
	public synchronized CredentialReference rotate(CredentialScope scope, CredentialReference previous, CredentialMaterial replacement) {
		var old = requireOwned(scope, previous);
		var next = store(scope, replacement);
		entries.remove(previous); Arrays.fill(old.value(), '\0');
		return next;
	}
	public synchronized void delete(CredentialScope scope, CredentialReference reference) {
		var old = requireOwned(scope, reference);
		entries.remove(reference); Arrays.fill(old.value(), '\0');
	}
	private Entry requireOwned(CredentialScope scope, CredentialReference reference) {
		var entry = entries.get(reference);
		if (entry == null || !entry.scope().equals(scope)) throw new AccessDeniedException("Access is denied");
		return entry;
	}
	@Override public synchronized void close() {
		entries.values().forEach(e -> Arrays.fill(e.value(), '\0')); entries.clear();
	}
}
