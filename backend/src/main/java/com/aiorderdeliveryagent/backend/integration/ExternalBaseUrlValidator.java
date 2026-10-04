package com.aiorderdeliveryagent.backend.integration;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
class ExternalBaseUrlValidator {

	private final boolean allowHttp;

	@Autowired
	ExternalBaseUrlValidator(Environment environment) {
		this(environment.acceptsProfiles(Profiles.of("local")));
	}

	ExternalBaseUrlValidator(boolean allowHttp) {
		this.allowHttp = allowHttp;
	}

	String validateAndNormalize(String candidate) {
		try {
			if (candidate == null) {
				throw new InvalidIntegrationUrlException();
			}
			URI parsed = new URI(candidate.strip());
			if (!parsed.isAbsolute() || parsed.isOpaque()) {
				throw new InvalidIntegrationUrlException();
			}

			String scheme = parsed.getScheme().toLowerCase(Locale.ROOT);
			if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) {
				throw new InvalidIntegrationUrlException();
			}
			if (parsed.getRawUserInfo() != null
					|| parsed.getRawQuery() != null
					|| parsed.getRawFragment() != null) {
				throw new InvalidIntegrationUrlException();
			}

			String host = normalizeHost(parsed.getHost());
			validateHost(host);
			if (parsed.getPort() == 0 || parsed.getPort() > 65535) {
				throw new InvalidIntegrationUrlException();
			}

			String rawPath = parsed.getRawPath();
			if (rawPath == null) {
				rawPath = "";
			}
			String lowerPath = rawPath.toLowerCase(Locale.ROOT);
			if (rawPath.contains("\\") || lowerPath.contains("%5c") || lowerPath.contains("%2e")) {
				throw new InvalidIntegrationUrlException();
			}

			String authorityHost = host.contains(":") ? "[" + host + "]" : host;
			String port = parsed.getPort() == -1 ? "" : ":" + parsed.getPort();
			URI normalized = new URI(scheme + "://" + authorityHost + port + rawPath).normalize();
			String normalizedValue = normalized.toASCIIString();
			if (normalizedValue.endsWith("/") && !normalized.getRawPath().equals("/")) {
				normalizedValue = normalizedValue.substring(0, normalizedValue.length() - 1);
			}
			return normalizedValue;
		}
		catch (URISyntaxException | IllegalArgumentException exception) {
			if (exception instanceof InvalidIntegrationUrlException invalidUrl) {
				throw invalidUrl;
			}
			throw new InvalidIntegrationUrlException();
		}
	}

	private String normalizeHost(String host) {
		if (host == null || host.isBlank() || host.contains("%")) {
			throw new InvalidIntegrationUrlException();
		}
		String normalized = host.toLowerCase(Locale.ROOT);
		if (normalized.startsWith("[") && normalized.endsWith("]")) {
			normalized = normalized.substring(1, normalized.length() - 1);
		}
		if (normalized.endsWith(".")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.isBlank()) {
			throw new InvalidIntegrationUrlException();
		}
		return normalized.contains(":") ? normalized : IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES);
	}

	private void validateHost(String host) {
		if (host.equals("localhost")
				|| host.endsWith(".localhost")
				|| host.endsWith(".local")
				|| host.endsWith(".internal")
				|| (!host.contains(".") && !host.contains(":"))) {
			throw new InvalidIntegrationUrlException();
		}

		if (host.contains(":")) {
			validateIpv6Literal(host);
			return;
		}

		if (host.matches("[0-9.]+")) {
			validateIpv4Literal(host);
			return;
		}

		if (host.startsWith("0x") || host.matches(".*\\.0x[0-9a-f]+.*")) {
			throw new InvalidIntegrationUrlException();
		}
	}

	private void validateIpv4Literal(String host) {
		String[] parts = host.split("\\.", -1);
		if (parts.length != 4) {
			throw new InvalidIntegrationUrlException();
		}
		int[] octets = new int[4];
		for (int index = 0; index < parts.length; index++) {
			if (parts[index].isEmpty() || (parts[index].length() > 1 && parts[index].startsWith("0"))) {
				throw new InvalidIntegrationUrlException();
			}
			octets[index] = Integer.parseInt(parts[index]);
			if (octets[index] > 255) {
				throw new InvalidIntegrationUrlException();
			}
		}

		int first = octets[0];
		int second = octets[1];
		boolean forbidden = first == 0
				|| first == 10
				|| first == 127
				|| (first == 100 && second >= 64 && second <= 127)
				|| (first == 169 && second == 254)
				|| (first == 172 && second >= 16 && second <= 31)
				|| (first == 192 && second == 168)
				|| first >= 224;
		if (forbidden) {
			throw new InvalidIntegrationUrlException();
		}
	}

	private void validateIpv6Literal(String host) {
		try {
			InetAddress address = InetAddress.getByName(host);
			byte[] bytes = address.getAddress();
			boolean uniqueLocal = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
			if (!(address instanceof Inet6Address)
					|| address.isAnyLocalAddress()
					|| address.isLoopbackAddress()
					|| address.isLinkLocalAddress()
					|| address.isSiteLocalAddress()
					|| address.isMulticastAddress()
					|| uniqueLocal) {
				throw new InvalidIntegrationUrlException();
			}
		}
		catch (Exception exception) {
			if (exception instanceof InvalidIntegrationUrlException invalidUrl) {
				throw invalidUrl;
			}
			throw new InvalidIntegrationUrlException();
		}
	}
}
