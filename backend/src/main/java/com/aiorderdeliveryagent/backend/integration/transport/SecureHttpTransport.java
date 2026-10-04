package com.aiorderdeliveryagent.backend.integration.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Trusted backend-only HTTP reader. No endpoint construction, credentials, provider mapping,
 * per-request security overrides, retry, proxy, redirects, cookies, or transparent decompression.
 */
@Component
public final class SecureHttpTransport {
	public enum Method { GET, HEAD }
	static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
	static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
	static final Duration WRITE_TIMEOUT = Duration.ofSeconds(5);
	static final Duration CALL_TIMEOUT = Duration.ofSeconds(15);
	static final int MAX_RESPONSE_BYTES = 1024 * 1024;
	private static final Set<String> FORBIDDEN_HEADERS = Set.of("host", "connection", "content-length",
		"transfer-encoding", "accept-encoding", "proxy-authorization", "proxy-connection", "cookie");
	private static final ExecutorService DNS_WORKERS = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
		new SynchronousQueue<>(), runnable -> {
			Thread thread = new Thread(runnable, "external-dns");
			thread.setDaemon(true);
			return thread;
		}, new ThreadPoolExecutor.AbortPolicy());
	private final boolean allowHttp;
	private final Dns resolver;
	private final java.util.function.Supplier<OkHttpClient.Builder> builders;

	@Autowired
	public SecureHttpTransport(Environment environment) {
		this(environment.acceptsProfiles(Profiles.of("local")), Dns.SYSTEM, OkHttpClient.Builder::new);
	}
	// Package-private test seams; no public client/policy/timeout override.
	SecureHttpTransport(boolean allowHttp, Dns resolver,
			java.util.function.Supplier<OkHttpClient.Builder> builders) {
		this.allowHttp = allowHttp;
		this.resolver = resolver;
		this.builders = builders;
	}

	public TransportResponse execute(Method method, URI target, Map<String, String> headers) {
		var budget = ProviderExecutionBudget.current();
		Duration callTimeout = budget == null ? CALL_TIMEOUT : budget.consumeHttpCall(CALL_TIMEOUT);
		HttpUrl url = validate(target);
		if (method == null || headers == null || headers.size() > 32) throw failure(TransportFailureException.Reason.POLICY_REJECTED);
		OkHttpClient client = builders.get()
			.proxy(Proxy.NO_PROXY).dns(this::lookup)
			.followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
			.authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
			// OkHttp 4 can follow a 503 Retry-After: 0 despite retryOnConnectionFailure(false).
			// Headers are not part of our response contract, so suppress that follow-up signal.
			.addNetworkInterceptor(chain -> chain.proceed(chain.request()).newBuilder()
				.removeHeader("Retry-After").build())
			.cookieJar(CookieJar.NO_COOKIES).cache(null)
			.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT)
			.writeTimeout(WRITE_TIMEOUT).callTimeout(callTimeout)
			.connectionPool(new ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
			.protocols(List.of(Protocol.HTTP_1_1)).build();
		try {
			Request.Builder request = new Request.Builder().url(url).method(method.name(), null)
				.header("Accept-Encoding", "identity");
			for (var header : headers.entrySet()) {
				String key = header.getKey(), value = header.getValue();
				if (key == null || value == null || key.length() > 128 || value.length() > 8192
						|| !key.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+")
						|| FORBIDDEN_HEADERS.contains(key.toLowerCase(Locale.ROOT))
						|| value.chars().anyMatch(c -> c < 32 || c >= 127)) {
					throw failure(TransportFailureException.Reason.POLICY_REJECTED);
				}
				request.header(key, value);
			}
			Call call = client.newCall(request.build());
			try (AutoCloseable cancellation = budget == null ? () -> {} : budget.track(call::cancel);
					Response response = call.execute()) {
				if (response.code() >= 300 && response.code() < 400)
					throw failure(TransportFailureException.Reason.REDIRECT_REJECTED);
				String encoding = response.header("Content-Encoding", "identity");
				if (!encoding.equalsIgnoreCase("identity"))
					throw failure(TransportFailureException.Reason.ENCODING_REJECTED);
				ResponseBody body = response.body();
				if (body == null) return new TransportResponse(response.code(), new byte[0]);
				if (body.contentLength() > MAX_RESPONSE_BYTES)
					throw failure(TransportFailureException.Reason.RESPONSE_TOO_LARGE);
				ByteArrayOutputStream bytes = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				try (var stream = body.byteStream()) {
					int count;
					while ((count = stream.read(buffer)) != -1) {
						if (bytes.size() + count > MAX_RESPONSE_BYTES)
							throw failure(TransportFailureException.Reason.RESPONSE_TOO_LARGE);
						bytes.write(buffer, 0, count);
					}
				}
				return new TransportResponse(response.code(), bytes.toByteArray());
			}
		}
		catch (UnknownHostException exception) { throw failure(TransportFailureException.Reason.DNS_REJECTED); }
		catch (InterruptedIOException exception) { throw failure(TransportFailureException.Reason.TIMEOUT); }
		catch (IOException | IllegalArgumentException exception) { throw failure(TransportFailureException.Reason.CONNECTION_FAILED); }
		catch (com.aiorderdeliveryagent.backend.integration.ProviderExecutionException exception) { throw exception; }
		catch (TransportFailureException exception) { throw exception; }
		catch (Exception exception) { throw failure(TransportFailureException.Reason.CONNECTION_FAILED); }
		finally {
			client.connectionPool().evictAll();
			client.dispatcher().executorService().shutdown();
		}
	}

	private HttpUrl validate(URI uri) {
		if (uri == null || !uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null
				|| uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
				|| uri.toASCIIString().length() > 4096)
			throw failure(TransportFailureException.Reason.POLICY_REJECTED);
		HttpUrl url = HttpUrl.parse(uri.toASCIIString());
		if (url == null || (!url.isHttps() && !(allowHttp && url.scheme().equals("http"))))
			throw failure(TransportFailureException.Reason.POLICY_REJECTED);
		String host = url.host();
		if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")
				|| host.endsWith(".internal") || (!host.contains(".") && !host.contains(":")) || host.contains("%"))
			throw failure(TransportFailureException.Reason.POLICY_REJECTED);
		// OkHttp bypasses custom DNS for literals, so enforce the same policy here.
		if (host.matches("[0-9.]+") || host.contains(":")) {
			if (!host.contains(":") && !host.matches("(0|[1-9][0-9]{0,2})(\\.(0|[1-9][0-9]{0,2})){3}"))
				throw failure(TransportFailureException.Reason.POLICY_REJECTED);
			try { if (!PublicAddressPolicy.permits(InetAddress.getByName(host)))
				throw failure(TransportFailureException.Reason.POLICY_REJECTED); }
			catch (UnknownHostException exception) { throw failure(TransportFailureException.Reason.POLICY_REJECTED); }
		}
		return url;
	}

	private List<InetAddress> lookup(String host) throws UnknownHostException {
		Future<List<InetAddress>> pending = null;
		try {
			pending = DNS_WORKERS.submit(() -> resolver.lookup(host));
			List<InetAddress> addresses = pending.get(2, TimeUnit.SECONDS);
			if (addresses == null || addresses.isEmpty() || addresses.size() > 16
					|| addresses.stream().anyMatch(address -> address == null || !PublicAddressPolicy.permits(address)))
				throw new UnknownHostException("External DNS resolution rejected");
			return List.copyOf(addresses);
		}
		catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new UnknownHostException("External DNS resolution interrupted"); }
		catch (ExecutionException | TimeoutException | RejectedExecutionException exception) {
			throw new UnknownHostException("External DNS resolution unavailable");
		}
		finally { if (pending != null) pending.cancel(true); }
	}
	private TransportFailureException failure(TransportFailureException.Reason reason) { return new TransportFailureException(reason); }
}
