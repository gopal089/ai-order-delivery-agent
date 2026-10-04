package com.aiorderdeliveryagent.backend.observability;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class SensitiveDataRedactor {

	public static final String REDACTED = "[REDACTED]";
	private static final Set<String> SENSITIVE_KEY_PARTS = Set.of(
			"password",
			"token",
			"authorization",
			"apikey",
			"secret",
			"credential",
			"cookie");

	public Map<String, Object> redact(Map<String, ?> fields) {
		Map<String, Object> safeFields = new LinkedHashMap<>();
		fields.forEach((key, value) -> safeFields.put(key, redactValue(key, value)));
		return Map.copyOf(safeFields);
	}

	private Object redactValue(String key, Object value) {
		String normalizedKey = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
		if (SENSITIVE_KEY_PARTS.stream().anyMatch(normalizedKey::contains)) {
			return REDACTED;
		}
		if (value instanceof Map<?, ?> nested) {
			Map<String, Object> stringKeyed = new LinkedHashMap<>();
			nested.forEach((nestedKey, nestedValue) ->
					stringKeyed.put(String.valueOf(nestedKey), nestedValue));
			return redact(stringKeyed);
		}
		return value;
	}
}
