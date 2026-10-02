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

@RestControllerAdvice(assignableTypes = UserAuthenticationController.class)
class AuthenticationExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		return ResponseEntity.badRequest().body(new ApiError(
				"VALIDATION_ERROR",
				"Authentication request is invalid",
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

	@ExceptionHandler(AuthenticationFailedException.class)
	ResponseEntity<ApiError> handleAuthenticationFailure() {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(
				"AUTHENTICATION_FAILED",
				"Authentication failed",
				Map.of(),
				Instant.now()));
	}
}
