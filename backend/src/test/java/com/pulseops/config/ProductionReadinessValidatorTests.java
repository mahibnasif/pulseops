package com.pulseops.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import com.pulseops.auth.AuthProperties;
import com.pulseops.auth.AuthRateLimitProperties;
import com.pulseops.monitoredservice.MonitoringProperties;
import org.junit.jupiter.api.Test;

class ProductionReadinessValidatorTests {

	@Test
	void acceptsTheSecureProductionBaseline() {
		assertThatCode(() -> validator(
				auth(true, "https://pulseops.example.com"),
				rateLimit(true, true),
				monitoring(false),
				false,
				false))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsInsecureTransportAndProxySettings() {
		assertThatThrownBy(() -> validator(
				auth(false, "https://pulseops.example.com"),
				rateLimit(true, true),
				monitoring(false),
				false,
				false))
				.hasMessageContaining("refresh cookies");

		assertThatThrownBy(() -> validator(
				auth(true, "http://pulseops.example.com"),
				rateLimit(true, true),
				monitoring(false),
				false,
				false))
				.hasMessageContaining("CORS origins");

		assertThatThrownBy(() -> validator(
				auth(true, "https://pulseops.example.com"),
				rateLimit(true, false),
				monitoring(false),
				false,
				false))
				.hasMessageContaining("load-balancer client address");
	}

	@Test
	void rejectsDisabledGuardsAndRuntimeMigrations() {
		assertThatThrownBy(() -> validator(
				auth(true, "https://pulseops.example.com"),
				rateLimit(false, true),
				monitoring(false),
				false,
				false))
				.hasMessageContaining("rate limiting");

		assertThatThrownBy(() -> validator(
				auth(true, "https://pulseops.example.com"),
				rateLimit(true, true),
				monitoring(true),
				false,
				false))
				.hasMessageContaining("private monitoring targets");

		assertThatThrownBy(() -> validator(
				auth(true, "https://pulseops.example.com"),
				rateLimit(true, true),
				monitoring(false),
				true,
				false))
				.hasMessageContaining("OpenAPI");

		assertThatThrownBy(() -> validator(
				auth(true, "https://pulseops.example.com"),
				rateLimit(true, true),
				monitoring(false),
				false,
				true))
				.hasMessageContaining("runtime Flyway");
	}

	private static ProductionReadinessValidator validator(
			AuthProperties auth,
			AuthRateLimitProperties rateLimit,
			MonitoringProperties monitoring,
			boolean openApiEnabled,
			boolean migrationsEnabled) {
		var validator = new ProductionReadinessValidator(
				auth,
				rateLimit,
				monitoring,
				openApiEnabled,
				migrationsEnabled);
		validator.validate();
		return validator;
	}

	private static AuthProperties auth(boolean secureCookie, String origin) {
		return new AuthProperties(
				"pulseops",
				"pulseops-web",
				Duration.ofMinutes(15),
				Duration.ofDays(30),
				"not-used-in-this-unit-test",
				secureCookie,
				List.of(origin));
	}

	private static AuthRateLimitProperties rateLimit(boolean enabled, boolean trustProxy) {
		return new AuthRateLimitProperties(
				enabled,
				10,
				Duration.ofMinutes(5),
				5,
				Duration.ofHours(1),
				30,
				Duration.ofMinutes(5),
				10_000,
				trustProxy);
	}

	private static MonitoringProperties monitoring(boolean allowPrivateTargets) {
		return new MonitoringProperties(
				allowPrivateTargets,
				65_536,
				true,
				10,
				120);
	}
}
