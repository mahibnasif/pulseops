package com.pulseops.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class AuthPropertiesTests {

	@Test
	void acceptsBoundedTokenLifetimesAndExactOrigins() {
		var properties = properties(
				Duration.ofMinutes(15),
				Duration.ofDays(30),
				List.of(" https://app.example.com ", "http://localhost:5173"));

		assertThat(properties.allowedOrigins())
				.containsExactly("https://app.example.com", "http://localhost:5173");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"*",
		"https://*.example.com",
		"file:///tmp/pulseops",
		"https://user@example.com",
		"https://app.example.com/path",
		"https://app.example.com?debug=true",
		"not an origin"
	})
	void rejectsWildcardOrMalformedCorsOrigins(String origin) {
		assertThatThrownBy(() -> properties(
				Duration.ofMinutes(15),
				Duration.ofDays(30),
				List.of(origin)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("exact HTTP or HTTPS origins");
	}

	@Test
	void rejectsUnboundedOrInvertedTokenLifetimes() {
		assertThatThrownBy(() -> properties(
				Duration.ofHours(2),
				Duration.ofDays(30),
				List.of("https://app.example.com")))
				.hasMessageContaining("access-token-ttl");

		assertThatThrownBy(() -> properties(
				Duration.ofMinutes(15),
				Duration.ofMinutes(10),
				List.of("https://app.example.com")))
				.hasMessageContaining("refresh-token-ttl");

		assertThatThrownBy(() -> properties(
				Duration.ofMinutes(15),
				Duration.ofDays(91),
				List.of("https://app.example.com")))
				.hasMessageContaining("refresh-token-ttl");
	}

	private static AuthProperties properties(
			Duration accessTokenTtl,
			Duration refreshTokenTtl,
			List<String> origins) {
		return new AuthProperties(
				"pulseops",
				"pulseops-web",
				accessTokenTtl,
				refreshTokenTtl,
				"not-used-in-this-unit-test",
				true,
				origins);
	}
}
