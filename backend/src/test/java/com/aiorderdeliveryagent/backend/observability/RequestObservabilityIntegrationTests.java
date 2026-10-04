package com.aiorderdeliveryagent.backend.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import com.aiorderdeliveryagent.backend.TestAuthProperties;
import com.jayway.jsonpath.JsonPath;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "DATABASE_PASSWORD", matches = ".+")
@Transactional
class RequestObservabilityIntegrationTests {

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		TestAuthProperties.register(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	private ch.qos.logback.classic.Logger requestLogger;
	private ListAppender<ILoggingEvent> appender;

	@BeforeEach
	void captureRequestLogs() {
		requestLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RequestIdFilter.class);
		appender = new ListAppender<>();
		appender.start();
		requestLogger.addAppender(appender);
	}

	@AfterEach
	void stopCapturingRequestLogs() {
		requestLogger.detachAppender(appender);
		appender.stop();
		MDC.clear();
	}

	@Test
	void requestWithoutIdReceivesGeneratedUuid() throws Exception {
		String requestId = mockMvc.perform(get("/actuator/health/liveness"))
				.andExpect(status().isOk())
				.andExpect(header().exists(RequestContext.REQUEST_ID_HEADER))
				.andReturn().getResponse().getHeader(RequestContext.REQUEST_ID_HEADER);

		assertThat(requestId).isNotNull();
		assertThatCode(() -> UUID.fromString(requestId)).doesNotThrowAnyException();
	}

	@Test
	void validCallerSuppliedRequestIdIsEchoed() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness")
				.header(RequestContext.REQUEST_ID_HEADER, "test-request-id"))
				.andExpect(status().isOk())
				.andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "test-request-id"));
	}

	@Test
	void invalidOrOversizedRequestIdIsReplaced() throws Exception {
		String supplied = "invalid request id " + "x".repeat(80);
		String actual = mockMvc.perform(get("/actuator/health/liveness")
				.header(RequestContext.REQUEST_ID_HEADER, supplied))
				.andExpect(status().isOk())
				.andReturn().getResponse().getHeader(RequestContext.REQUEST_ID_HEADER);

		assertThat(actual).isNotEqualTo(supplied);
		assertThatCode(() -> UUID.fromString(actual)).doesNotThrowAnyException();
	}

	@Test
	void apiErrorContainsTheFinalRequestId() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.header(RequestContext.REQUEST_ID_HEADER, "error-request-id")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"invalid\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string(RequestContext.REQUEST_ID_HEADER, "error-request-id"))
				.andExpect(jsonPath("$.requestId").value("error-request-id"));
	}

	@Test
	void requestIdIsAvailableToRequestLoggingContext() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
				.header(RequestContext.REQUEST_ID_HEADER, "logging-request-id")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"invalid\"}"))
				.andExpect(status().isBadRequest());

		assertThat(appender.list).anySatisfy(event -> {
			assertThat(event.getFormattedMessage()).isEqualTo("http_request_completed");
			assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "logging-request-id");
		});
	}

	@Test
	void mdcIsClearedAfterRequestCompletion() throws Exception {
		mockMvc.perform(get("/actuator/health/liveness")
				.header(RequestContext.REQUEST_ID_HEADER, "clear-request-id"))
				.andExpect(status().isOk());

		assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
	}

	@Test
	void authenticatedRequestLoggingUsesOnlyServerEstablishedIdentityContext() throws Exception {
		String email = "logging.context." + UUID.randomUUID() + "@example.com";
		String password = "SecurePass!234";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isCreated());
		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"%s","password":"%s"}
						""".formatted(email, password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String accessToken = JsonPath.read(loginBody, "$.accessToken");
		appender.list.clear();

		mockMvc.perform(get("/api/v1/integrations")
				.header("Authorization", "Bearer " + accessToken)
				.header(RequestContext.REQUEST_ID_HEADER, "authenticated-log-context"))
				.andExpect(status().isOk());

		assertThat(appender.list).anySatisfy(event -> {
			Map<String, String> fields = new java.util.LinkedHashMap<>();
			event.getKeyValuePairs().forEach(pair ->
					fields.put(pair.key, String.valueOf(pair.value)));
			assertThat(fields)
					.containsEntry("method", "GET")
					.containsEntry("endpoint", "/api/v1/integrations")
					.containsKeys("status", "durationMs");
			assertThat(event.getMDCPropertyMap())
					.containsEntry("requestId", "authenticated-log-context")
					.containsKeys("userId", "tenantId", "sessionId");
		});
	}
}
