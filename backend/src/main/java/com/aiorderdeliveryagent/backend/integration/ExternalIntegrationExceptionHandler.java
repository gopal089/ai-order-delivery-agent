package com.aiorderdeliveryagent.backend.integration;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ExternalIntegrationController.class)
class ExternalIntegrationExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<IntegrationApiError> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(error(
				"VALIDATION_ERROR", "Integration request is invalid", fieldErrors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<IntegrationApiError> handleUnreadableRequest() {
		return ResponseEntity.badRequest().body(error(
				"INVALID_REQUEST", "A valid JSON request body is required", Map.of()));
	}

	@ExceptionHandler(InvalidIntegrationUrlException.class)
	ResponseEntity<IntegrationApiError> handleInvalidUrl() {
		return ResponseEntity.badRequest().body(error(
				"INVALID_BASE_URL", "Integration base URL is not allowed", Map.of()));
	}

	@ExceptionHandler(IntegrationConflictException.class)
	ResponseEntity<IntegrationApiError> handleConflict(IntegrationConflictException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(error(
				"INTEGRATION_CONFLICT", exception.getMessage(), Map.of()));
	}

	private IntegrationApiError error(String code, String message, Map<String, String> fieldErrors) {
		return new IntegrationApiError(code, message, fieldErrors, Instant.now());
	}
}
