package com.aiorderdeliveryagent.backend.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuditEventService.class);

	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;
	private final SensitiveDataRedactor redactor;
	private final SafeSecurityLogger safeLogger;

	public AuditEventService(
			JdbcTemplate jdbcTemplate,
			ObjectMapper objectMapper,
			SensitiveDataRedactor redactor,
			SafeSecurityLogger safeLogger) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.redactor = redactor;
		this.safeLogger = safeLogger;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(
			AuditEventType eventType,
			UUID tenantId,
			Long actorUserId,
			String resourceType,
			String resourceId,
			AuditOutcome outcome,
			Map<String, ?> metadata) {
		if (actorUserId != null && tenantId == null) {
			throw new IllegalArgumentException("An authenticated audit actor requires a tenant");
		}

		Map<String, Object> safeMetadata = new LinkedHashMap<>();
		String requestId = RequestContext.currentRequestId();
		if (requestId != null) {
			safeMetadata.put("requestId", requestId);
		}
		safeMetadata.putAll(redactor.redact(metadata));

		try {
			jdbcTemplate.update("""
					INSERT INTO public.audit_events
					    (tenant_id, actor_user_id, event_type, resource_type, resource_id, outcome, metadata)
					VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
					""",
					tenantId,
					actorUserId,
					eventType.name(),
					resourceType,
					resourceId,
					outcome.name(),
					objectMapper.writeValueAsString(safeMetadata));
		}
		catch (DataAccessException | JacksonException exception) {
			safeLogger.error(LOGGER, "audit_event_write_failed", Map.of(
					"eventType", eventType.name(),
					"outcome", outcome.name(),
					"failureType", exception.getClass().getSimpleName()));
			throw new AuditEventWriteException(exception);
		}
	}
}
