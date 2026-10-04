package com.aiorderdeliveryagent.backend.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class SensitiveDataRedactorTests {

	@Test
	void sensitiveFieldsAreRedactedBeforeLogging() {
		SensitiveDataRedactor redactor = new SensitiveDataRedactor();
		SafeSecurityLogger safeLogger = new SafeSecurityLogger(redactor);
		ch.qos.logback.classic.Logger logger =
				(ch.qos.logback.classic.Logger) LoggerFactory.getLogger("security-redaction-test");
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("password", "synthetic-password-value");
		fields.put("accessToken", "synthetic-access-token");
		fields.put("refreshToken", "synthetic-refresh-token");
		fields.put("Authorization", "Bearer synthetic-authorization");
		fields.put("apiKey", "synthetic-api-key");
		fields.put("secret", "synthetic-secret");
		fields.put("outcome", "SAFE");

		try {
			safeLogger.info(logger, "redaction_test", fields);
			ILoggingEvent event = appender.list.getFirst();
			Map<String, String> loggedFields = new LinkedHashMap<>();
			event.getKeyValuePairs().forEach(pair ->
					loggedFields.put(pair.key, String.valueOf(pair.value)));

			assertThat(loggedFields)
					.containsEntry("password", SensitiveDataRedactor.REDACTED)
					.containsEntry("accessToken", SensitiveDataRedactor.REDACTED)
					.containsEntry("refreshToken", SensitiveDataRedactor.REDACTED)
					.containsEntry("Authorization", SensitiveDataRedactor.REDACTED)
					.containsEntry("apiKey", SensitiveDataRedactor.REDACTED)
					.containsEntry("secret", SensitiveDataRedactor.REDACTED)
					.containsEntry("outcome", "SAFE");
			assertThat(loggedFields.values()).noneMatch(value -> value.startsWith("synthetic-"));
		}
		finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	@Test
	void mdcContextFieldsAreNotDuplicatedAsStructuredKeyValues() {
		SafeSecurityLogger safeLogger = new SafeSecurityLogger(new SensitiveDataRedactor());
		ch.qos.logback.classic.Logger logger =
				(ch.qos.logback.classic.Logger) LoggerFactory.getLogger("security-context-test");
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		MDC.put("requestId", "mdc-request-id");
		MDC.put("tenantId", "trusted-tenant");

		try {
			safeLogger.info(logger, "context_test", Map.of(
					"tenantId", "duplicate-tenant",
					"outcome", "SAFE"));
			ILoggingEvent event = appender.list.getFirst();
			assertThat(event.getMDCPropertyMap())
					.containsEntry("requestId", "mdc-request-id")
					.containsEntry("tenantId", "trusted-tenant");
			assertThat(event.getKeyValuePairs())
					.extracting(pair -> pair.key)
					.contains("outcome")
					.doesNotContain("tenantId", "requestId");
		}
		finally {
			MDC.clear();
			logger.detachAppender(appender);
			appender.stop();
		}
	}
}
