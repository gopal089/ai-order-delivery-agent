package com.aiorderdeliveryagent.backend.observability;

public class AuditEventWriteException extends RuntimeException {

	AuditEventWriteException(Throwable cause) {
		super("Required audit event could not be recorded", cause);
	}
}
