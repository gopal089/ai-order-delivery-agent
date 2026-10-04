package com.aiorderdeliveryagent.backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExternalBaseUrlValidatorTests {

	private final ExternalBaseUrlValidator productionValidator = new ExternalBaseUrlValidator(false);

	@Test
	void normalizesSafeHttpsBaseUrl() {
		assertEquals(
				"https://orders.example.com/api/v1",
				productionValidator.validateAndNormalize(" HTTPS://ORDERS.EXAMPLE.COM/api/v1/ "));
	}

	@Test
	void httpIsRejectedOutsideLocalEnvironment() {
		assertRejected("http://orders.example.com");
	}

	@Test
	void localEnvironmentMayUseHttpForPublicHosts() {
		ExternalBaseUrlValidator localValidator = new ExternalBaseUrlValidator(true);
		assertEquals(
				"http://orders.example.com/",
				localValidator.validateAndNormalize("http://orders.example.com/"));
	}

	@Test
	void rejectsMalformedOrUnsupportedUrls() {
		List.of(
				"not-a-url",
				"ftp://orders.example.com",
				"https:/orders.example.com",
				"https://orders.example.com:0",
				"https://orders.example.com:99999",
				"https://single-label",
				"https://orders.example.com/%2e%2e/admin",
				"https://orders.example.com/%5cadmin")
				.forEach(this::assertRejected);
	}

	@Test
	void rejectsLocalhostAndLoopbackAddresses() {
		List.of(
				"https://localhost",
				"https://api.localhost",
				"https://127.0.0.1",
				"https://127.255.255.254",
				"https://[::1]",
				"https://[::ffff:127.0.0.1]")
				.forEach(this::assertRejected);
	}

	@Test
	void rejectsPrivateIpv4Ranges() {
		List.of(
				"https://10.0.0.1",
				"https://172.16.0.1",
				"https://172.31.255.254",
				"https://192.168.1.1",
				"https://100.64.0.1")
				.forEach(this::assertRejected);
	}

	@Test
	void rejectsLinkLocalAndMetadataAddresses() {
		List.of(
				"https://169.254.1.1",
				"https://169.254.169.254",
				"https://[fe80::1]")
				.forEach(this::assertRejected);
	}

	@Test
	void rejectsUnspecifiedPrivateAndMulticastIpv6Addresses() {
		List.of(
				"https://[::]",
				"https://[fc00::1]",
				"https://[fd00::1]",
				"https://[ff02::1]")
				.forEach(this::assertRejected);
	}

	@Test
	void rejectsUnspecifiedMulticastAndReservedIpv4Addresses() {
		List.of(
				"https://0.0.0.0",
				"https://224.0.0.1",
				"https://255.255.255.255")
				.forEach(this::assertRejected);
	}

	@Test
	void rejectsAmbiguousNumericHostFormsUsedForBypasses() {
		List.of(
				"https://2130706433",
				"https://0177.0.0.1",
				"https://0x7f000001")
				.forEach(this::assertRejected);
	}

	@Test
	void rejectsUrlsContainingCredentials() {
		assertRejected("https://user:pass@orders.example.com");
	}

	@Test
	void rejectsFragments() {
		assertRejected("https://orders.example.com/api#fragment");
	}

	@Test
	void rejectsAllConfiguredBaseUrlQueryStrings() {
		assertRejected("https://orders.example.com/api?target=value");
	}

	private void assertRejected(String candidate) {
		assertThrows(
				InvalidIntegrationUrlException.class,
				() -> productionValidator.validateAndNormalize(candidate));
	}
}
