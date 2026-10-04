package com.aiorderdeliveryagent.backend.integration;

import java.util.Map;

import com.aiorderdeliveryagent.backend.auth.AuthenticatedUserContext;
import com.aiorderdeliveryagent.backend.observability.AuditEventService;
import com.aiorderdeliveryagent.backend.observability.AuditEventType;
import com.aiorderdeliveryagent.backend.observability.AuditOutcome;
import com.aiorderdeliveryagent.backend.observability.SafeSecurityLogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class IntegrationAuditService {

	private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationAuditService.class);

	private final AuditEventService auditEventService;
	private final SafeSecurityLogger safeLogger;

	IntegrationAuditService(AuditEventService auditEventService, SafeSecurityLogger safeLogger) {
		this.auditEventService = auditEventService;
		this.safeLogger = safeLogger;
	}

	void record(AuditEventType eventType, AuthenticatedUserContext context, long integrationId) {
		auditEventService.record(
				eventType,
				context.tenantId(),
				context.userId(),
				"integration",
				Long.toString(integrationId),
				AuditOutcome.SUCCESS,
				Map.of());
		safeLogger.info(LOGGER, "integration_security_event", Map.of(
				"eventType", eventType.name(),
				"operation", eventType.name(),
				"outcome", AuditOutcome.SUCCESS.name(),
				"tenantId", context.tenantId(),
				"userId", context.userId(),
				"integrationId", integrationId));
	}
}
