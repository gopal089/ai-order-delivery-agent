package com.aiorderdeliveryagent.backend.ai;

import java.time.Instant;
import java.util.Map;
import com.aiorderdeliveryagent.backend.observability.RequestContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes=ConversationController.class)
class ConversationExceptionHandler {
	record Error(String code,String message,Map<String,String> fieldErrors,Instant timestamp,String requestId) { }
	@ExceptionHandler({IllegalArgumentException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
	ResponseEntity<Error> invalid() {return error(400,"INVALID_REQUEST","Conversation request is invalid");}
	@ExceptionHandler(AiBoundaryException.class)
	ResponseEntity<Error> ai(AiBoundaryException failure) {
		return switch(failure.reason()) {
			case MODEL_UNAVAILABLE,EXECUTION_BUSY -> error(503,"AI_UNAVAILABLE","AI execution is unavailable");
			case MODEL_TIMEOUT,EXECUTION_TIMEOUT -> error(504,"AI_TIMEOUT","AI execution timed out");
			case CONTEXT_TOO_LARGE -> error(400,"CONTEXT_TOO_LARGE","Conversation context is too large");
			default -> error(502,"AI_FAILURE","AI execution failed");
		};
	}
	@ExceptionHandler(DataAccessException.class)
	ResponseEntity<Error> database() {return error(503,"CONVERSATION_UNAVAILABLE","Conversation persistence is unavailable");}
	@ExceptionHandler(Exception.class)
	ResponseEntity<Error> unexpected(Exception failure) {
		// Let existing Spring Security retain established authentication/ownership responses.
		if(failure instanceof org.springframework.security.access.AccessDeniedException denied) throw denied;
		if(failure instanceof org.springframework.security.core.AuthenticationException authentication) throw authentication;
		return error(503,"CONVERSATION_UNAVAILABLE","Conversation execution is unavailable");
	}
	private ResponseEntity<Error> error(int status,String code,String message) {
		return ResponseEntity.status(status).body(new Error(code,message,Map.of(),Instant.now(),RequestContext.currentRequestId()));
	}
}
