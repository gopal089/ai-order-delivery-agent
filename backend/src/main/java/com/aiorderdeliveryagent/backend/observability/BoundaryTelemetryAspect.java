package com.aiorderdeliveryagent.backend.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.security.access.AccessDeniedException;
import com.aiorderdeliveryagent.backend.ai.AiBoundaryException;
import com.aiorderdeliveryagent.backend.integration.ProviderExecutionException;

/** Narrow existing service boundaries only. Never inspect arguments, results or exception text. */
@Aspect
@Component
public class BoundaryTelemetryAspect {
	private final SafeTelemetry telemetry;
	private final SecuritySignals signals;
	public BoundaryTelemetryAspect(SafeTelemetry telemetry, SecuritySignals signals) { this.telemetry=telemetry;this.signals=signals; }
	@Around("execution(public * com.aiorderdeliveryagent.backend.ai.AgentOrchestrationService.execute(..))")
	public Object ai(ProceedingJoinPoint point) throws Throwable { return execute(point, SafeTelemetry.Operation.AI_ORCHESTRATION); }
	@Around("execution(public * com.aiorderdeliveryagent.backend.integration.ExternalOrderExecutionService.*(..))")
	public Object provider(ProceedingJoinPoint point) throws Throwable { return execute(point, SafeTelemetry.Operation.PROVIDER); }
	@Around("execution(public * com.aiorderdeliveryagent.backend.ai.ConversationContextService.getHistory(..))")
	public Object database(ProceedingJoinPoint point) throws Throwable { return execute(point, SafeTelemetry.Operation.DATABASE_HISTORY); }
	@Around("execution(* com.aiorderdeliveryagent.backend.auth.AuthenticationRateLimiter.check*(..)) || execution(* com.aiorderdeliveryagent.backend.auth.AuthenticationRateLimiter.loginSucceeded(..))")
	public Object redis(ProceedingJoinPoint point) throws Throwable { return execute(point, SafeTelemetry.Operation.REDIS_RATE_LIMIT); }
	@Around("execution(public * com.aiorderdeliveryagent.backend.auth.TenantDataAuthorizationService.require*(..))")
	public Object authorization(ProceedingJoinPoint point) throws Throwable { return execute(point, SafeTelemetry.Operation.AUTHORIZATION); }
	@Around("execution(public * com.aiorderdeliveryagent.backend.observability.AuditEventService.record(..))")
	public Object audit(ProceedingJoinPoint point) throws Throwable { return execute(point, SafeTelemetry.Operation.AUDIT); }
	@Around("execution(* com.aiorderdeliveryagent.backend.integration.ExternalBaseUrlValidator.validateAndNormalize(..))")
	public Object urlPolicy(ProceedingJoinPoint point) throws Throwable { return execute(point, SafeTelemetry.Operation.INTEGRATION_URL_POLICY); }
	private Object execute(ProceedingJoinPoint point, SafeTelemetry.Operation operation) throws Throwable {
		try (var span=telemetry.start(operation)) {
			try { return point.proceed(); }
			catch (Throwable failure) {
				span.failed();
				if (operation == SafeTelemetry.Operation.INTEGRATION_URL_POLICY) signals.observed(SecuritySignals.Event.INTEGRATION_URL_REJECTED);
				else if (failure instanceof AccessDeniedException) signals.observed(SecuritySignals.Event.AUTHORIZATION_DENIED);
				else if (failure instanceof ProviderExecutionException provider) signals.observed(provider.reason()==ProviderExecutionException.Reason.AUTHENTICATION_FAILED
					? SecuritySignals.Event.PROVIDER_AUTHENTICATION_FAILURE : SecuritySignals.Event.PROVIDER_FAILURE);
				else if (failure instanceof AiBoundaryException ai) signals.observed(switch(ai.reason()) {
					case TOOL_LIMIT -> SecuritySignals.Event.AI_TOOL_LIMIT;
					case UNKNOWN_TOOL -> SecuritySignals.Event.AI_UNKNOWN_TOOL;
					case MODEL_TIMEOUT -> SecuritySignals.Event.AI_MODEL_TIMEOUT;
					case EXECUTION_TIMEOUT -> SecuritySignals.Event.AI_EXECUTION_TIMEOUT;
					default -> SecuritySignals.Event.AI_BOUNDARY_FAILURE;
				});
				else if (failure instanceof AuditEventWriteException) signals.observed(SecuritySignals.Event.AUDIT_WRITE_FAILURE);
				throw failure;
			}
		}
	}
}
