package com.aiorderdeliveryagent.backend.ai;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import com.aiorderdeliveryagent.backend.integration.*;
import com.aiorderdeliveryagent.backend.integration.credential.*;
import com.aiorderdeliveryagent.backend.integration.order.*;
import com.aiorderdeliveryagent.backend.integration.transport.SecureHttpTransport;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Explicitly imported TEST ONLY fixtures; never registered in the production application. */
@TestConfiguration(proxyBeanMethods=false)
public class ChatTestConfiguration {
	public static final class FixtureModel implements AiModelProvider {
		public final AtomicReference<AiModelProvider> delegate=new AtomicReference<>(new ScriptedTestModel(ScriptedTestModel.Scenario.PLAIN,1));
		public AiModelContract.Response generate(AiModelContract.Request request) { return delegate.get().generate(request); }
	}
	@Bean FixtureModel chatFixtureModel() { return new FixtureModel(); }
	@Bean CredentialStore chatFixtureStore() {
		return new CredentialStore() {
			public CredentialReference store(CredentialScope scope,CredentialMaterial material) {throw new UnsupportedOperationException();}
			public CredentialMaterial retrieve(CredentialScope scope,CredentialReference ref) {return CredentialMaterial.copyOf(new char[]{'x'});}
			public void delete(CredentialScope scope,CredentialReference ref) {throw new UnsupportedOperationException();}
		};
	}
	public static final class FixtureAdapter implements BackendOrderProviderAdapter {
		public final AtomicReference<DeterministicTestOrderProvider.Scenario> scenario=new AtomicReference<>(DeterministicTestOrderProvider.Scenario.SUCCESS);
		public String providerKey() {return "test-only";}
		public ExternalOrderProvider open(URI url,CredentialMaterial material,SecureHttpTransport transport) {
			return new DeterministicTestOrderProvider(scenario.get());
		}
	}
	@Bean FixtureAdapter chatFixtureAdapter() { return new FixtureAdapter(); }
}
