package com.aiorderdeliveryagent.backend.observability;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

@Component
public class SafeSecurityLogger {

	private final SensitiveDataRedactor redactor;

	public SafeSecurityLogger(SensitiveDataRedactor redactor) {
		this.redactor = redactor;
	}

	public void info(Logger logger, String message, Map<String, ?> fields) {
		log(logger.atInfo(), message, fields);
	}

	public void warn(Logger logger, String message, Map<String, ?> fields) {
		log(logger.atWarn(), message, fields);
	}

	public void error(Logger logger, String message, Map<String, ?> fields) {
		log(logger.atError(), message, fields);
	}

	private void log(LoggingEventBuilder event, String message, Map<String, ?> fields) {
		redactor.redact(fields).forEach((key, value) -> {
			if (MDC.get(key) == null) {
				event.addKeyValue(key, value);
			}
		});
		event.log(message);
	}
}
