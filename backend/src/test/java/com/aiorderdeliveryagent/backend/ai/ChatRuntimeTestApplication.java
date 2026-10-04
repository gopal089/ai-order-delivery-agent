package com.aiorderdeliveryagent.backend.ai;

import java.net.URI;
import java.util.*;
import com.aiorderdeliveryagent.backend.BackendApplication;
import com.aiorderdeliveryagent.backend.integration.*;
import com.aiorderdeliveryagent.backend.integration.credential.*;
import com.aiorderdeliveryagent.backend.integration.order.*;
import com.aiorderdeliveryagent.backend.integration.transport.SecureHttpTransport;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.*;
import static com.aiorderdeliveryagent.backend.ai.AiModelContract.*;

/** Opt-in TEST CLASSPATH launcher only. Not packaged in bootJar or used by normal application startup. */
public final class ChatRuntimeTestApplication {
	public static void main(String[] args) {SpringApplication.run(new Class<?>[]{BackendApplication.class,Fixtures.class},args);}
	@Configuration(proxyBeanMethods=false) @Profile("chat-runtime-test-only")
	public static class Fixtures {
		@Bean AiModelProvider runtimeFixtureModel() {
			return request->request.toolResults().isEmpty()
				?new Response(Optional.empty(),List.of(new ToolCall("getCustomerOrders",Map.of())),Optional.empty(),Optional.empty())
				:new Response(Optional.of("I checked the external system and your package is in Chennai. Delivery is tomorrow."),List.of(),Optional.empty(),Optional.empty());
		}
		@Bean CredentialStore runtimeFixtureStore() {
			return new CredentialStore() {
				public CredentialReference store(CredentialScope s,CredentialMaterial m) {throw new UnsupportedOperationException();}
				public CredentialMaterial retrieve(CredentialScope s,CredentialReference r) {return CredentialMaterial.copyOf(new char[]{'x'});}
				public void delete(CredentialScope s,CredentialReference r) {throw new UnsupportedOperationException();}
			};
		}
		@Bean BackendOrderProviderAdapter runtimeFixtureAdapter() {
			return new BackendOrderProviderAdapter() {
				public String providerKey() {return "test-only";}
				public ExternalOrderProvider open(URI u,CredentialMaterial m,SecureHttpTransport t) {
					return new DeterministicTestOrderProvider(DeterministicTestOrderProvider.Scenario.SUCCESS);
				}
			};
		}
	}
}
