package com.aiorderdeliveryagent.backend.observability;

import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelemetryConfiguration {
	// No exporter, global registration, environment autoconfiguration or network traffic.
	@Bean
	@ConditionalOnMissingBean(OpenTelemetry.class)
	OpenTelemetry openTelemetry() { return OpenTelemetry.noop(); }
}
