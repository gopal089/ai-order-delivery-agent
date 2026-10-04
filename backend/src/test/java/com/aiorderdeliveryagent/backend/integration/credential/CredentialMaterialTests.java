package com.aiorderdeliveryagent.backend.integration.credential;
import static org.assertj.core.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class CredentialMaterialTests {
	@Test void closingZeroizesOwnedMemoryAndPreventsRetrieval() throws Exception {
		char[] input = "synthetic-only".toCharArray();
		CredentialMaterial material = CredentialMaterial.copyOf(input);
		Field field = CredentialMaterial.class.getDeclaredField("value");
		field.setAccessible(true);
		char[] owned = (char[]) field.get(material);
		input[0] = 'x';
		assertThat(material.copyForTrustedBackend()[0]).isEqualTo('s');
		assertThat(material.toString()).doesNotContain("synthetic");
		material.close();
		assertThat(owned).containsOnly('\0');
		assertThatThrownBy(material::copyForTrustedBackend).isInstanceOf(IllegalStateException.class);
		material.close();
	}
	@Test void referencesAreRedactedAndScopesRequireOwnershipIdentifiers() {
		assertThat(new CredentialReference("synthetic-reference").toString()).doesNotContain("synthetic-reference");
		assertThatThrownBy(() -> new CredentialScope(UUID.randomUUID(), 0, 1)).isInstanceOf(IllegalArgumentException.class);
	}
}
