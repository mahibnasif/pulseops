package com.pulseops.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pulseops.auth-rate-limit")
public record AuthRateLimitProperties(
		boolean enabled,
		int loginAttempts,
		@NotNull Duration loginWindow,
		int registrationAttempts,
		@NotNull Duration registrationWindow,
		int refreshAttempts,
		@NotNull Duration refreshWindow,
		int maxTrackedKeys,
		boolean trustProxyClientIp) {

	public AuthRateLimitProperties {
		requireAttempts("login-attempts", loginAttempts);
		requireAttempts("registration-attempts", registrationAttempts);
		requireAttempts("refresh-attempts", refreshAttempts);
		requireWindow("login-window", loginWindow);
		requireWindow("registration-window", registrationWindow);
		requireWindow("refresh-window", refreshWindow);
		if (maxTrackedKeys < 100 || maxTrackedKeys > 1_000_000) {
			throw new IllegalArgumentException(
					"pulseops.auth-rate-limit.max-tracked-keys must be between 100 and 1000000.");
		}
	}

	private static void requireAttempts(String property, int value) {
		if (value < 1 || value > 10_000) {
			throw new IllegalArgumentException(
					"pulseops.auth-rate-limit." + property + " must be between 1 and 10000.");
		}
	}

	private static void requireWindow(String property, Duration value) {
		if (value == null
				|| value.isNegative()
				|| value.isZero()
				|| value.compareTo(Duration.ofDays(1)) > 0) {
			throw new IllegalArgumentException(
					"pulseops.auth-rate-limit." + property
							+ " must be greater than zero and no longer than one day.");
		}
	}
}
