package com.aiorderdeliveryagent.backend.integration.credential;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;

class CredentialLifecycleContractTests {
	private final CredentialScope scope = new CredentialScope(UUID.randomUUID(), 1, 1);
	private final ScopedTestCredentialStore store = new ScopedTestCredentialStore();
	private CredentialMaterial material() { return CredentialMaterial.copyOf(new char[] {'x'}); }
	@AfterEach void close() { store.close(); }
	@Test void storeRetrieveAreScopedAndRetrievedLeaseIsIndependent() {
		CredentialReference reference;
		try (var input = material()) { reference = store.store(scope, input); }
		try (var result = store.retrieve(scope, reference)) { assertThat(result.copyForTrustedBackend().length).isEqualTo(1); }
		try (var second = store.retrieve(scope, reference)) { assertThat(second.copyForTrustedBackend().length).isEqualTo(1); }
	}
	@Test void tenantUserAndIntegrationMustAllMatchForEveryOperation() {
		try (var input = material()) {
			var reference = store.store(scope, input);
			for (var other : new CredentialScope[] {new CredentialScope(UUID.randomUUID(), 1, 1),
				new CredentialScope(scope.tenantId(), 2, 1), new CredentialScope(scope.tenantId(), 1, 2)}) {
				assertThatThrownBy(() -> store.retrieve(other, reference)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
				assertThatThrownBy(() -> store.rotate(other, reference, input)).isInstanceOf(AccessDeniedException.class);
				assertThatThrownBy(() -> store.delete(other, reference)).isInstanceOf(AccessDeniedException.class);
			}
		}
	}
	@Test void successfulRotationInvalidatesPreviousReference() {
		try (var input = material()) {
			var previous = store.store(scope, input);
			var replacement = store.rotate(scope, previous, input);
			assertThat(replacement.equals(previous)).isFalse();
			assertThatThrownBy(() -> store.retrieve(scope, previous)).isInstanceOf(AccessDeniedException.class);
			try (var result = store.retrieve(scope, replacement)) { assertThat(result.copyForTrustedBackend().length).isEqualTo(1); }
		}
	}
	@Test void failedRotationPreservesPreviousCredential() {
		try (var input = material(); var closed = material()) {
			var previous = store.store(scope, input); closed.close();
			assertThatThrownBy(() -> store.rotate(scope, previous, closed)).isInstanceOf(IllegalStateException.class);
			try (var result = store.retrieve(scope, previous)) { assertThat(result.copyForTrustedBackend().length).isEqualTo(1); }
		}
	}
	@Test void deletionInvalidatesReferenceWithoutRevealingItsValue() {
		try (var input = material()) {
			var reference = store.store(scope, input); store.delete(scope, reference);
			assertThatThrownBy(() -> store.retrieve(scope, reference)).isInstanceOf(AccessDeniedException.class).hasMessage("Access is denied");
			assertThat(reference.toString()).doesNotContain(reference.value());
			assertThat(input.toString()).contains("REDACTED");
		}
	}
	@Test void existingStoresWithoutRotationFailClosed() {
		CredentialStore legacy = new CredentialStore() {
			public CredentialReference store(CredentialScope s, CredentialMaterial m) { throw new UnsupportedOperationException(); }
			public CredentialMaterial retrieve(CredentialScope s, CredentialReference r) { throw new UnsupportedOperationException(); }
			public void delete(CredentialScope s, CredentialReference r) { throw new UnsupportedOperationException(); }
		};
		try (var input = material()) {
			assertThatThrownBy(() -> legacy.rotate(scope, new CredentialReference("test-only"), input))
				.isInstanceOf(UnsupportedOperationException.class).hasMessage("Credential rotation is unavailable");
		}
	}
}
