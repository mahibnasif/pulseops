package com.pulseops.config;

import com.pulseops.auth.AuthProperties;
import com.pulseops.auth.AuthRateLimitProperties;
import com.pulseops.monitoredservice.MonitoringProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
public class ProductionReadinessValidator {

	private final AuthProperties auth;
	private final AuthRateLimitProperties rateLimit;
	private final MonitoringProperties monitoring;
	private final boolean openApiEnabled;
	private final boolean migrationsEnabled;

	public ProductionReadinessValidator(
			AuthProperties auth,
			AuthRateLimitProperties rateLimit,
			MonitoringProperties monitoring,
			@Value("${springdoc.api-docs.enabled}") boolean openApiEnabled,
			@Value("${spring.flyway.enabled}") boolean migrationsEnabled) {
		this.auth = auth;
		this.rateLimit = rateLimit;
		this.monitoring = monitoring;
		this.openApiEnabled = openApiEnabled;
		this.migrationsEnabled = migrationsEnabled;
	}

	@PostConstruct
	void validate() {
		if (!auth.refreshCookieSecure()) {
			throw invalid("refresh cookies must require HTTPS");
		}
		if (auth.allowedOrigins().stream()
				.anyMatch(origin -> !origin.startsWith("https://"))) {
			throw invalid("CORS origins must use HTTPS");
		}
		if (!rateLimit.enabled()) {
			throw invalid("authentication rate limiting must be enabled");
		}
		if (!rateLimit.trustProxyClientIp()) {
			throw invalid("the trusted load-balancer client address must be enabled");
		}
		if (monitoring.allowPrivateTargets()) {
			throw invalid("private monitoring targets must remain disabled");
		}
		if (openApiEnabled) {
			throw invalid("OpenAPI must be disabled");
		}
		if (migrationsEnabled) {
			throw invalid("runtime Flyway migrations must be disabled");
		}
	}

	private IllegalStateException invalid(String reason) {
		return new IllegalStateException("Invalid production configuration: " + reason + ".");
	}
}
