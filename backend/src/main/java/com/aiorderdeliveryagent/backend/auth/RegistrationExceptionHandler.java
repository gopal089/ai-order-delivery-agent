package com.aiorderdeliveryagent.backend.auth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserRegistrationController.class)
class RegistrationExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		return ResponseEntity.badRequest().body(new ApiError(
				"VALIDATION_ERROR",
				"Registration request is invalid",
				fieldErrors,
				Instant.now()));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadableRequest() {
		return ResponseEntity.badRequest().body(new ApiError(
				"INVALID_REQUEST",
				"A valid JSON request body is required",
				Map.of(),
				Instant.now()));
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ResponseEntity<ApiError> handleDuplicateEmail(EmailAlreadyRegisteredException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(
				"EMAIL_ALREADY_REGISTERED",
				exception.getMessage(),
				Map.of(),
				Instant.now()));
	}
}
