package com.aiorderdeliveryagent.backend.domain.order;

import java.time.Instant;
import java.util.Map;
import com.aiorderdeliveryagent.backend.integration.ProviderExecutionException;
import com.aiorderdeliveryagent.backend.observability.RequestContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={OrderController.class, ShipmentController.class})
class DomainApiExceptionHandler {
	record Error(String code, String message, Map<String,String> fieldErrors, Instant timestamp, String requestId) { }
	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
	ResponseEntity<Error> invalid() { return response(400, "INVALID_REQUEST", "Domain request is invalid"); }
	@ExceptionHandler(ProviderExecutionException.class)
	ResponseEntity<Error> provider(ProviderExecutionException failure) {
		int status = switch (failure.reason()) {
			case TIMEOUT -> 504;
			case DISABLED, UNCONFIGURED, PROVIDER_UNAVAILABLE, CREDENTIAL_STORE_UNAVAILABLE, EXECUTION_BUSY -> 503;
			default -> 502;
		};
		return response(status, "PROVIDER_UNAVAILABLE", "External data is unavailable");
	}
	private ResponseEntity<Error> response(int status, String code, String message) {
		return ResponseEntity.status(status).body(new Error(code, message, Map.of(), Instant.now(), RequestContext.currentRequestId()));
	}
}
