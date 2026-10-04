package com.aiorderdeliveryagent.backend.observability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditEventServiceTests {

	@Test
	void auditWriteFailureIsPropagatedInsteadOfSilentlySwallowed() {
		SensitiveDataRedactor redactor = new SensitiveDataRedactor();
		SafeSecurityLogger safeLogger = new SafeSecurityLogger(redactor);
		JdbcTemplate failingJdbcTemplate = new JdbcTemplate() {
			@Override
			public int update(String sql, Object... args) {
				throw new DataAccessResourceFailureException("synthetic audit database failure");
			}
		};
		AuditEventService service = new AuditEventService(
				failingJdbcTemplate,
				new ObjectMapper(),
				redactor,
				safeLogger);

		assertThatThrownBy(() -> service.record(
				AuditEventType.AUTH_LOGIN_FAILURE,
				null,
				null,
				null,
				null,
				AuditOutcome.FAILURE,
				Map.of("reason", "AUTHENTICATION_FAILED")))
				.isInstanceOf(AuditEventWriteException.class)
				.hasMessage("Required audit event could not be recorded");
	}
}
