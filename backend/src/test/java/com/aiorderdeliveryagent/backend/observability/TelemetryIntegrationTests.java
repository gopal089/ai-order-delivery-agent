package com.aiorderdeliveryagent.backend.observability;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import com.aiorderdeliveryagent.backend.TestAuthProperties;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="DATABASE_PASSWORD",matches=".+")
@Import(TelemetryIntegrationTests.Configuration.class)
class TelemetryIntegrationTests {
	@TestConfiguration static class Configuration {
		@Bean InMemorySpanExporter spanExporter() { return InMemorySpanExporter.create(); }
		@Bean(destroyMethod="close") SdkTracerProvider tracerProvider(InMemorySpanExporter exporter) {
			return SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
		}
		@Bean @Primary OpenTelemetry testTelemetry(SdkTracerProvider provider) { return OpenTelemetrySdk.builder().setTracerProvider(provider).build(); }
	}
	@DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { TestAuthProperties.register(registry); }
	@Autowired MockMvc mvc;
	@Autowired InMemorySpanExporter exporter;
	@BeforeEach void reset() { exporter.reset(); }
	@Test void missingAuthenticationIsObservedWithoutExportingHeadersOrIdentity() throws Exception {
		mvc.perform(get("/api/v1/integrations").header("X-Request-ID","safe-fixture-request")
			.header("Authorization","Bearer synthetic-invalid-token").queryParam("tenant_id","synthetic-tenant-secret"))
			.andExpect(status().isUnauthorized()).andExpect(header().string("X-Request-ID","safe-fixture-request"));
		assertThat(exporter.getFinishedSpanItems()).hasSize(1);
		assertThat(exporter.getFinishedSpanItems().toString()).doesNotContain("synthetic-","safe-fixture-request","tenant_id","Authorization");
	}
	@Test void realRedisAndAuditBoundariesAreInstrumentedWithoutLoginInputs() throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType("application/json")
			.content("{\"email\":\"telemetry-"+java.util.UUID.randomUUID()+"@example.test\",\"password\":\"synthetic-password-secret\"}"))
			.andExpect(status().isUnauthorized());
		assertThat(exporter.getFinishedSpanItems()).extracting(s->s.getName())
			.contains("application.http","application.redis_rate_limit","application.audit");
		assertThat(exporter.getFinishedSpanItems().toString()).doesNotContain("synthetic-password-secret","@example.test","tenantId","userId","sessionId");
	}
}
