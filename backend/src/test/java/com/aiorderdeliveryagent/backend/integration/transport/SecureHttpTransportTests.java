package com.aiorderdeliveryagent.backend.integration.transport;

import static org.assertj.core.api.Assertions.*;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.SocketFactory;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import okhttp3.tls.*;
import org.junit.jupiter.api.*;

class SecureHttpTransportTests {
	private MockWebServer server;
	private final InetAddress publicIp = address("93.184.216.34");
	private AtomicInteger connections;
	@BeforeEach void start() throws Exception {
		server = new MockWebServer();
		connections = new AtomicInteger();
		server.start(InetAddress.getLoopbackAddress(), 0);
	}
	@AfterEach void stop() throws Exception { server.shutdown(); }

	private SecureHttpTransport transport(Dns dns, boolean local) {
		return new SecureHttpTransport(local, dns,
			() -> new OkHttpClient.Builder().socketFactory(mappedSockets()));
	}
	private SecureHttpTransport transport() { return transport(host -> List.of(publicIp), true); }
	private URI target() { return URI.create("http://example.com:" + server.getPort() + "/test-only"); }

	/** Test-only socket routing: verifies the validated IP then routes to a local fixture. */
	private SocketFactory mappedSockets() {
		return new SocketFactory() {
			@Override public Socket createSocket() {
				return new Socket() {
					@Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
						InetSocketAddress selected = (InetSocketAddress) endpoint;
						assertThat(selected.isUnresolved()).isFalse();
						assertThat(selected.getAddress()).isEqualTo(publicIp);
						assertThat(timeout).isEqualTo(3000);
						connections.incrementAndGet();
						super.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), server.getPort()), timeout);
					}
				};
			}
			@Override public Socket createSocket(String h, int p) { throw new UnsupportedOperationException(); }
			@Override public Socket createSocket(String h, int p, InetAddress l, int lp) { throw new UnsupportedOperationException(); }
			@Override public Socket createSocket(InetAddress h, int p) { throw new UnsupportedOperationException(); }
			@Override public Socket createSocket(InetAddress h, int p, InetAddress l, int lp) { throw new UnsupportedOperationException(); }
		};
	}
	private static InetAddress address(String ip) {
		try { return InetAddress.getByName(ip); } catch (Exception e) { throw new AssertionError(e); }
	}
	private void rejects(SecureHttpTransport transport, URI uri, TransportFailureException.Reason reason) {
		assertThatThrownBy(() -> transport.execute(SecureHttpTransport.Method.GET, uri, Map.of()))
			.isInstanceOfSatisfying(TransportFailureException.class, e -> {
				assertThat(e.reason()).isEqualTo(reason);
				assertThat(e.getCause()).isNull();
				assertThat(e.getMessage()).doesNotContain(uri.toString(), "synthetic");
			});
	}
	@Test void blocksLoopbackPrivateLinkLocalMetadataAndReservedLiterals() {
		for (String ip : List.of("127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1",
				"169.254.169.254", "0.0.0.0", "100.64.1.1", "224.0.0.1", "240.0.0.1",
				"192.0.0.1", "198.18.0.1", "192.0.2.1", "198.51.100.1", "203.0.113.1",
				"[::]", "[::1]", "[fc00::1]", "[fe80::1]", "[ff02::1]", "[::ffff:127.0.0.1]",
				"[2002:a00:1::1]", "[64:ff9b::a00:1]", "[2001:db8::1]", "[3fff::1]")) {
			rejects(transport(), URI.create("https://" + ip + "/"), TransportFailureException.Reason.POLICY_REJECTED);
		}
		assertThat(connections.get()).isZero();
	}
	@Test void deniesHttpOutsideLocalAndUrlCredentialLocations() {
		rejects(transport(host -> List.of(publicIp), false), target(), TransportFailureException.Reason.POLICY_REJECTED);
		for (String url : List.of("https://user:synthetic@example.com/", "https://example.com/?apiKey=synthetic",
				"https://example.com/#synthetic", "https://localhost/", "https://service.internal/",
				"file:///tmp/test", "https://127.1/", "https://2130706433/", "https://0x7f000001/")) {
			rejects(transport(), URI.create(url), TransportFailureException.Reason.POLICY_REJECTED);
		}
	}
	@Test void blocksPrivateDnsAndMixedPublicPrivateAnswersBeforeConnection() {
		for (List<InetAddress> answers : List.of(List.of(address("10.0.0.1")),
				List.of(publicIp, address("169.254.169.254")), List.of(address("fc00::1")),
				List.<InetAddress>of())) {
			rejects(transport(host -> answers, true), target(), TransportFailureException.Reason.DNS_REJECTED);
		}
		assertThat(connections.get()).isZero();
	}
	@Test void dnsAnswerIsPinnedAndFreshlyValidatedOnEveryCall() {
		AtomicInteger resolutions = new AtomicInteger();
		var transport = transport(host -> resolutions.getAndIncrement() == 0
			? List.of(publicIp) : List.of(address("127.0.0.1")), true);
		server.enqueue(new MockResponse().setBody("test-data"));
		assertThat(transport.execute(SecureHttpTransport.Method.GET, target(), Map.of()).body()).isEqualTo("test-data".getBytes());
		rejects(transport, target(), TransportFailureException.Reason.DNS_REJECTED);
		assertThat(resolutions.get()).isEqualTo(2);
		assertThat(connections.get()).isEqualTo(1);
	}
	@Test void literalPublicAddressIsValidatedEvenThoughOkHttpSkipsCustomDns() {
		server.enqueue(new MockResponse().setBody("literal"));
		var result = transport(host -> { throw new AssertionError("Literal must not need DNS"); }, true)
			.execute(SecureHttpTransport.Method.GET, URI.create("http://93.184.216.34:" + server.getPort()), Map.of());
		assertThat(result.status()).isEqualTo(200);
	}
	@Test void dnsWaitIsBounded() {
		long started = System.nanoTime();
		rejects(transport(host -> {
			try { Thread.sleep(10000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
			return List.of(publicIp);
		}, true), target(), TransportFailureException.Reason.DNS_REJECTED);
		assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isBetween(1800L, 4000L);
		assertThat(connections.get()).isZero();
	}
	@Test void overallProviderDeadlineCancelsAnActiveHttpCall() throws Exception {
		server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
		long started = System.nanoTime();
		assertThatThrownBy(() -> ProviderExecutionBudget.execute(java.time.Duration.ofMillis(300), 5,
			budget -> transport().execute(SecureHttpTransport.Method.GET, target(), Map.of())))
			.isInstanceOfAny(com.aiorderdeliveryagent.backend.integration.ProviderExecutionException.class, TransportFailureException.class);
		assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
		assertThat(server.takeRequest(1, TimeUnit.SECONDS)).isNotNull();
	}
	@Test void redirectsAreNeverFollowed() {
		server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "http://169.254.169.254/"));
		rejects(transport(), target(), TransportFailureException.Reason.REDIRECT_REJECTED);
		assertThat(server.getRequestCount()).isEqualTo(1);
	}
	@Test void failureStatusesAreReturnedWithoutRetryOrBodyInToString() {
		for (int status : List.of(401, 408, 429, 503)) {
			server.enqueue(new MockResponse().setResponseCode(status).setHeader("Retry-After", "0").setBody("synthetic-private-response"));
			var result = transport().execute(SecureHttpTransport.Method.GET, target(), Map.of());
			assertThat(result.status()).isEqualTo(status);
			assertThat(result.toString()).doesNotContain("synthetic");
		}
		assertThat(server.getRequestCount()).isEqualTo(4);
	}
	@Test void announcedAndChunkedResponseSizesAreBounded() {
		server.enqueue(new MockResponse().setHeader("Content-Length", SecureHttpTransport.MAX_RESPONSE_BYTES + 1));
		rejects(transport(), target(), TransportFailureException.Reason.RESPONSE_TOO_LARGE);
		server.enqueue(new MockResponse().setChunkedBody("a".repeat(SecureHttpTransport.MAX_RESPONSE_BYTES + 1), 4096));
		rejects(transport(), target(), TransportFailureException.Reason.RESPONSE_TOO_LARGE);
	}
	@Test void compressedResponsesAreRejectedBeforeDecompression() throws Exception {
		server.enqueue(new MockResponse().setHeader("Content-Encoding", "gzip").setBody("compressed-test-data"));
		rejects(transport(), target(), TransportFailureException.Reason.ENCODING_REJECTED);
		assertThat(server.takeRequest().getHeader("Accept-Encoding")).isEqualTo("identity");
	}
	@Test void readTimeoutIsEnforced() {
		server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
		long started = System.nanoTime();
		rejects(transport(), target(), TransportFailureException.Reason.TIMEOUT);
		assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isBetween(4500L, 8000L);
		assertThat(server.getRequestCount()).isEqualTo(1);
	}
	@Test void totalCallTimeoutBoundsSlowStreaming() {
		server.enqueue(new MockResponse().setBody("a".repeat(100)).throttleBody(1, 1, TimeUnit.SECONDS));
		long started = System.nanoTime();
		rejects(transport(), target(), TransportFailureException.Reason.TIMEOUT);
		assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isBetween(14000L, 18000L);
	}
	@Test void headersCannotOverrideHostProxyEncodingOrInjectLines() {
		for (Map<String, String> headers : List.of(Map.of("Host", "127.0.0.1"),
				Map.of("Accept-Encoding", "gzip"), Map.of("Proxy-Authorization", "synthetic"),
				Map.of("Authorization", "synthetic\r\nHost: localhost"))) {
			assertThatThrownBy(() -> transport().execute(SecureHttpTransport.Method.GET, target(), headers))
				.isInstanceOfSatisfying(TransportFailureException.class,
					e -> assertThat(e.reason()).isEqualTo(TransportFailureException.Reason.POLICY_REJECTED));
		}
		assertThat(connections.get()).isZero();
	}
	@Test void authorizationStaysInBackendHeaderAndIsNotInException() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/ignored"));
		assertThatThrownBy(() -> transport().execute(SecureHttpTransport.Method.GET, target(),
				Map.of("Authorization", "Bearer synthetic-backend-only")))
			.hasMessageNotContaining("synthetic").hasCause(null);
		assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer synthetic-backend-only");
	}
	@Test void fixedClientPolicyIncludesWriteTimeoutAndNoProxyCookiesOrRetry() {
		var builder = new OkHttpClient.Builder().socketFactory(mappedSockets());
		var transport = new SecureHttpTransport(true, host -> List.of(publicIp), () -> builder);
		server.enqueue(new MockResponse().setBody("test"));
		transport.execute(SecureHttpTransport.Method.GET, target(), Map.of());
		var client = builder.build();
		assertThat(client.connectTimeoutMillis()).isEqualTo(3000);
		assertThat(client.readTimeoutMillis()).isEqualTo(5000);
		assertThat(client.writeTimeoutMillis()).isEqualTo(5000);
		assertThat(client.callTimeoutMillis()).isEqualTo(15000);
		assertThat(client.proxy()).isEqualTo(Proxy.NO_PROXY);
		assertThat(client.followRedirects()).isFalse();
		assertThat(client.followSslRedirects()).isFalse();
		assertThat(client.retryOnConnectionFailure()).isFalse();
		assertThat(client.cookieJar()).isEqualTo(CookieJar.NO_COOKIES);
		assertThat(client.cache()).isNull();
	}
	@Test void transportDoesNotEmitCredentialHeadersOrResponseBodiesToApplicationLogs() {
		var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
		appender.start(); logger.addAppender(appender);
		try {
			server.enqueue(new MockResponse().setBody("synthetic-response-secret"));
			transport().execute(SecureHttpTransport.Method.GET, target(), Map.of("Authorization", "synthetic-header-secret"));
			assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
				.doesNotContain("synthetic-header-secret", "synthetic-response-secret"));
		}
		finally { logger.detachAppender(appender); appender.stop(); }
	}
	@Test void tlsHostnameVerificationRejectsWrongHostAndAcceptsCorrectHost() throws Exception {
		server.shutdown();
		server = new MockWebServer();
		var certificate = new HeldCertificate.Builder().addSubjectAlternativeName("example.com").build();
		var serverTls = new HandshakeCertificates.Builder().heldCertificate(certificate).build();
		var clientTls = new HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate()).build();
		server.useHttps(serverTls.sslSocketFactory(), false);
		server.start(InetAddress.getLoopbackAddress(), 0);
		var transport = new SecureHttpTransport(false, host -> List.of(publicIp),
			() -> new OkHttpClient.Builder().socketFactory(mappedSockets())
				.sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager()));
		server.enqueue(new MockResponse().setBody("validated TLS"));
		assertThat(transport.execute(SecureHttpTransport.Method.GET,
			URI.create("https://example.com:" + server.getPort()), Map.of()).status()).isEqualTo(200);
		rejects(transport, URI.create("https://wrong.example.com:" + server.getPort()), TransportFailureException.Reason.CONNECTION_FAILED);
	}
}
