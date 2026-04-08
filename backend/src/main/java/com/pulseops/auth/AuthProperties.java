package com.pulseops.auth;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pulseops.auth")
public record AuthProperties(
		@NotBlank String issuer,
		@NotBlank String audience,
		@NotNull Duration accessTokenTtl,
		@NotNull Duration refreshTokenTtl,
		@NotBlank String jwtSecret,
		boolean refreshCookieSecure,
		@NotEmpty List<String> allowedOrigins) {

	public AuthProperties {
		allowedOrigins = List.copyOf(allowedOrigins);
	}

	@Override
	public List<String> allowedOrigins() {
		return List.copyOf(allowedOrigins);
	}
}
