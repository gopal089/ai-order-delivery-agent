package com.aiorderdeliveryagent.backend.observability;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerMapping;
import org.slf4j.LoggerFactory;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;

class SafeTelemetryTests {
	private final InMemorySpanExporter exporter=InMemorySpanExporter.create();
	private final SdkTracerProvider provider=SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
	private final OpenTelemetry sdk=OpenTelemetrySdk.builder().setTracerProvider(provider).build();
	private final SafeTelemetry telemetry=new SafeTelemetry(sdk);
	private final SecuritySignals signals=new SecuritySignals(new SafeSecurityLogger(new SensitiveDataRedactor()));
	@AfterEach void close() { provider.close(); }
	private String emitted() { return exporter.getFinishedSpanItems().toString(); }
	@Test void authorizationRefreshCredentialsApiKeysProviderPayloadAndPromptsAreNotExported() throws Exception {
		var request=new MockHttpServletRequest("POST","/api/v1/auth/login");
		request.addHeader("Authorization","Bearer synthetic-auth-secret");
		request.addHeader("X-API-Key","synthetic-api-secret");
		request.addHeader("X-Request-ID","synthetic-id-secret");
		request.setQueryString("refreshToken=synthetic-refresh-secret");
		request.setContent("password=synthetic-password-secret&prompt=synthetic-prompt-secret&provider=synthetic-provider-secret".getBytes());
		var response=new MockHttpServletResponse();
		new HttpTelemetryFilter(telemetry,signals).doFilter(request,response,(req,res)->{
			req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/v1/auth/login");
			res.getWriter().write("synthetic-model-secret");
		});
		assertThat(emitted()).doesNotContain("synthetic-","password","Authorization","prompt","provider=");
		var span=exporter.getFinishedSpanItems().getFirst();
		assertThat(span.getAttributes().size()).isEqualTo(3);
		assertThat(span.getName()).isEqualTo("application.http");
	}
	@Test void rawPathQueryAndUnknownMethodsCannotBecomeAttributes() throws Exception {
		var request=new MockHttpServletRequest("synthetic-secret","/synthetic-secret/123");
		new HttpTelemetryFilter(telemetry,signals).doFilter(request,new MockHttpServletResponse(),(req,res)->
			req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/synthetic-secret/{id}"));
		assertThat(emitted()).contains("OTHER","UNMATCHED").doesNotContain("synthetic-secret");
	}
	@Test void exceptionMessageAndStackAreNotExportedAndScopeIsRestored() {
		var request=new MockHttpServletRequest("GET","/private");
		assertThatThrownBy(()->new HttpTelemetryFilter(telemetry,signals).doFilter(request,new MockHttpServletResponse(),
			(req,res)->{throw new IOException("synthetic-exception-secret");})).isInstanceOf(IOException.class);
		assertThat(emitted()).doesNotContain("synthetic-exception-secret","exception.message","exception.stacktrace");
		assertThat(exporter.getFinishedSpanItems().getFirst().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
		assertThat(exporter.getFinishedSpanItems().getFirst().getEvents()).isEmpty();
		assertThat(Span.current().getSpanContext().isValid()).isFalse();
	}
	@Test void publicIngressCannotInheritParentOrBaggage() throws Exception {
		var external=Span.wrap(SpanContext.create("11111111111111111111111111111111","2222222222222222",TraceFlags.getSampled(),TraceState.getDefault()));
		try(var ignored=Context.root().with(external).makeCurrent()) {
			new HttpTelemetryFilter(telemetry,signals).doFilter(new MockHttpServletRequest("GET","/private"),new MockHttpServletResponse(),(req,res)->{});
			assertThat(Span.current().getSpanContext()).isEqualTo(external.getSpanContext());
		}
		assertThat(exporter.getFinishedSpanItems().getFirst().getParentSpanContext().isValid()).isFalse();
	}
	@Test void healthProbesAreExcluded() throws Exception {
		new HttpTelemetryFilter(telemetry,signals).doFilter(new MockHttpServletRequest("GET","/actuator/health/readiness"),new MockHttpServletResponse(),(req,res)->{});
		assertThat(exporter.getFinishedSpanItems()).isEmpty();
	}
	@Test void internalSpansNestAndContainNoCustomerLabels() {
		try(var outer=telemetry.start(SafeTelemetry.Operation.AI_ORCHESTRATION)) {
			try(var inner=telemetry.start(SafeTelemetry.Operation.PROVIDER)) { inner.failed(); }
		}
		var spans=exporter.getFinishedSpanItems();
		assertThat(spans).hasSize(2);
		assertThat(spans.get(0).getParentSpanId()).isEqualTo(spans.get(1).getSpanId());
		for(var span:spans) { assertThat(span.getAttributes().isEmpty()).isTrue();assertThat(span.getEvents()).isEmpty(); }
	}
	@Test void defaultConfigurationIsNoopAndCannotExport() {
		var disabled=new SafeTelemetry(new TelemetryConfiguration().openTelemetry());
		try(var activity=disabled.start(SafeTelemetry.Operation.PROVIDER)) { activity.failed(); }
		assertThat(exporter.getFinishedSpanItems()).isEmpty();
	}
	@Test void securitySignalsHaveBoundedTypesAndNoPayloadFields() {
		var logger=(ch.qos.logback.classic.Logger)LoggerFactory.getLogger(SecuritySignals.class);
		var appender=new ListAppender<ILoggingEvent>() {
			@Override protected void append(ILoggingEvent event) { event.prepareForDeferredProcessing();super.append(event); }
		};appender.start();logger.addAppender(appender);
		try {
			org.slf4j.MDC.put("Authorization","synthetic-mdc-secret");
			for(int status:new int[]{200,401,403,429,503}) signals.http(status);
			assertThat(appender.list).hasSize(4);
			for(var event:appender.list) {
				assertThat(event.getFormattedMessage()).isEqualTo("security_signal_observed");
				assertThat(event.getKeyValuePairs()).hasSize(2);
				assertThat(event.getThrowableProxy()).isNull();
				assertThat(event.getMDCPropertyMap()).isEmpty();
			}
			assertThat(org.slf4j.MDC.get("Authorization")).isEqualTo("synthetic-mdc-secret");
		} finally { org.slf4j.MDC.clear();logger.detachAppender(appender);appender.stop(); }
	}
	@Test void aspectPreservesResultAndDoesNotInspectArgumentsOrResults() throws Throwable {
		var point=mock(org.aspectj.lang.ProceedingJoinPoint.class);
		Object result=new Object();when(point.proceed()).thenReturn(result);
		var aspect=new BoundaryTelemetryAspect(telemetry,signals);
		assertThat(aspect.provider(point)).isSameAs(result);
		assertThat(exporter.getFinishedSpanItems().getFirst().getAttributes().isEmpty()).isTrue();
		verify(point,never()).getArgs();verify(point,never()).getSignature();
	}
	@Test void aspectRecordsFailureWithoutChangingTheThrownException() throws Throwable {
		var point=mock(org.aspectj.lang.ProceedingJoinPoint.class);
		var failure=new org.springframework.security.access.AccessDeniedException("synthetic-credential-secret");
		when(point.proceed()).thenThrow(failure);
		assertThatThrownBy(()->new BoundaryTelemetryAspect(telemetry,signals).authorization(point)).isSameAs(failure);
		assertThat(emitted()).doesNotContain("synthetic-credential-secret");
		assertThat(exporter.getFinishedSpanItems().getFirst().getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
		assertThat(exporter.getFinishedSpanItems().getFirst().getEvents()).isEmpty();
	}
	@Test @SuppressWarnings("unchecked") void actualModelAndToolWaitsExportNoPromptResponseOrFailurePayload() {
		var contexts=mock(com.aiorderdeliveryagent.backend.auth.AuthenticatedUserContextProvider.class);
		var history=mock(com.aiorderdeliveryagent.backend.ai.ConversationContextService.class);
		var factory=mock(com.aiorderdeliveryagent.backend.integration.ControlledOrderToolFactory.class);
		var tools=mock(com.aiorderdeliveryagent.backend.integration.order.tool.ControlledOrderTools.class);
		var models=(org.springframework.beans.factory.ObjectProvider<com.aiorderdeliveryagent.backend.ai.AiModelProvider>)mock(org.springframework.beans.factory.ObjectProvider.class);
		when(history.getHistory(1)).thenReturn(new com.aiorderdeliveryagent.backend.ai.AiModelContract.History(java.util.List.of(),false));
		when(factory.bind(1)).thenReturn(tools);
		when(tools.getCustomerOrders()).thenThrow(new RuntimeException("synthetic-provider-secret"));
		var calls=new java.util.concurrent.atomic.AtomicInteger();
		com.aiorderdeliveryagent.backend.ai.AiModelProvider model=request->calls.getAndIncrement()==0
			? new com.aiorderdeliveryagent.backend.ai.AiModelContract.Response(java.util.Optional.empty(),
				java.util.List.of(new com.aiorderdeliveryagent.backend.ai.AiModelContract.ToolCall("getCustomerOrders",Map.of())),java.util.Optional.empty(),java.util.Optional.empty())
			: new com.aiorderdeliveryagent.backend.ai.AiModelContract.Response(java.util.Optional.of("synthetic-model-secret"),java.util.List.of(),java.util.Optional.empty(),java.util.Optional.empty());
		when(models.getIfAvailable()).thenReturn(model);
		var agent=new com.aiorderdeliveryagent.backend.ai.AgentOrchestrationService(contexts,history,factory,models,JsonMapper.builder().build());
		agent.setTelemetry(telemetry);
		assertThat(agent.execute(1,1,"synthetic-prompt-secret").text()).isEqualTo("synthetic-model-secret");
		assertThat(exporter.getFinishedSpanItems()).extracting(s->s.getName()).contains("application.model_wait","application.tool_wait");
		assertThat(emitted()).doesNotContain("synthetic-prompt-secret","synthetic-model-secret","synthetic-provider-secret");
	}
}
