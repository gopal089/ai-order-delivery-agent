package com.aiorderdeliveryagent.backend.integration.order.model;

import java.util.Objects;
import java.util.Optional;

final class ContractValidation {

	private ContractValidation() {
	}

	static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value;
	}

	static <T> Optional<T> requireOptional(Optional<T> value, String fieldName) {
		return Objects.requireNonNull(value, fieldName + " must not be null");
	}
}
