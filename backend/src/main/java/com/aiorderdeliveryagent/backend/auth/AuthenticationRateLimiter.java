package com.aiorderdeliveryagent.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
class AuthenticationRateLimiter {

	private static final DefaultRedisScript<String> INCREMENT_WITH_TTL = new DefaultRedisScript<>("""
			local count = redis.call('INCR', KEYS[1])
			if count == 1 then
			  redis.call('PEXPIRE', KEYS[1], ARGV[1])
			end
			local ttl = redis.call('PTTL', KEYS[1])
			return tostring(count) .. ':' .. tostring(ttl)
			""", String.class);

	private final StringRedisTemplate redisTemplate;
	private final AuthenticationRateLimitProperties properties;

	AuthenticationRateLimiter(
			StringRedisTemplate redisTemplate,
			AuthenticationRateLimitProperties properties) {
		this.redisTemplate = redisTemplate;
		this.properties = properties;
	}

	void checkLogin(String normalizedEmail, String clientAddress) {
		consume(
				key("login", "ip", clientAddress),
				properties.loginIpMaxAttempts(),
				properties.loginIpWindow());
		consume(
				key("login", "identity", normalizedEmail),
				properties.loginIdentityMaxAttempts(),
				properties.loginIdentityWindow());
	}

	void loginSucceeded(String normalizedEmail) {
		delete(key("login", "identity", normalizedEmail));
	}

	void checkRefreshIp(String clientAddress) {
		consume(
				key("refresh", "ip", clientAddress),
				properties.refreshIpMaxAttempts(),
				properties.refreshIpWindow());
	}

	void checkRefreshSession(UUID sessionId) {
		consume(
				key("refresh", "session", sessionId.toString()),
				properties.refreshSessionMaxAttempts(),
				properties.refreshSessionWindow());
	}

	private void consume(String key, int maxAttempts, Duration window) {
		final String result;
		try {
			result = redisTemplate.execute(
					INCREMENT_WITH_TTL,
					List.of(key),
					Long.toString(window.toMillis()));
		}
		catch (DataAccessException exception) {
			throw new AuthenticationRateLimitUnavailableException();
		}

		if (result == null) {
			throw new AuthenticationRateLimitUnavailableException();
		}

		String[] parts = result.split(":", 2);
		if (parts.length != 2) {
			throw new AuthenticationRateLimitUnavailableException();
		}

		try {
			long count = Long.parseLong(parts[0]);
			long ttlMillis = Long.parseLong(parts[1]);
			if (count > maxAttempts) {
				long effectiveTtl = ttlMillis > 0 ? ttlMillis : window.toMillis();
				throw new AuthenticationRateLimitExceededException(
						Math.max(1, (effectiveTtl + 999) / 1000));
			}
		}
		catch (NumberFormatException exception) {
			throw new AuthenticationRateLimitUnavailableException();
		}
	}

	private void delete(String key) {
		try {
			redisTemplate.delete(key);
		}
		catch (DataAccessException exception) {
			throw new AuthenticationRateLimitUnavailableException();
		}
	}

	private String key(String endpoint, String dimension, String identifier) {
		return properties.namespace()
				+ ":" + endpoint
				+ ":" + dimension
				+ ":" + sha256(identifier);
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
