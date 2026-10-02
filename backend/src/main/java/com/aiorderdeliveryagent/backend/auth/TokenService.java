package com.aiorderdeliveryagent.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.JwsAlgorithms;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class TokenService {

	private static final int REFRESH_TOKEN_BYTES = 32;
	private final JwtEncoder jwtEncoder;
	private final AuthTokenProperties properties;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	TokenService(JwtEncoder jwtEncoder, AuthTokenProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	IssuedAccessToken issueAccessToken(AuthenticatedUserPrincipal principal, UUID sessionId) {
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(AuthTokenProperties.ACCESS_TOKEN_ISSUER)
				.subject(Long.toString(principal.userId()))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("sid", sessionId.toString())
				.build();
		JwsHeader headers = JwsHeader.with(() -> JwsAlgorithms.HS256).build();
		String value = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
		return new IssuedAccessToken(value, properties.accessTokenTtl().toSeconds());
	}

	IssuedRefreshToken issueRefreshToken() {
		byte[] randomBytes = new byte[REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(randomBytes);
		String value = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		return new IssuedRefreshToken(
				value,
				hashRefreshToken(value),
				clock.instant().plus(properties.refreshTokenTtl()));
	}

	String hashRefreshToken(String refreshToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.US_ASCII)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	record IssuedAccessToken(String value, long expiresInSeconds) {
	}

	record IssuedRefreshToken(String value, String hash, Instant expiresAt) {
	}
}
