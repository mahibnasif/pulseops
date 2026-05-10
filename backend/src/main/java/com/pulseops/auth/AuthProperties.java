package com.pulseops.auth;

import java.time.Duration;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pulseops.auth")
public record AuthProperties(
		@NotBlank @Size(max = 200) String issuer,
		@NotBlank @Size(max = 200) String audience,
		@NotNull Duration accessTokenTtl,
		@NotNull Duration refreshTokenTtl,
		@NotBlank String jwtSecret,
		boolean refreshCookieSecure,
		@NotEmpty List<String> allowedOrigins) {

	public AuthProperties {
		if (accessTokenTtl != null
				&& (accessTokenTtl.compareTo(Duration.ofMinutes(1)) < 0
						|| accessTokenTtl.compareTo(Duration.ofHours(1)) > 0)) {
			throw new IllegalArgumentException(
					"pulseops.auth.access-token-ttl must be between one minute and one hour.");
		}
		if (refreshTokenTtl != null
				&& (refreshTokenTtl.compareTo(Duration.ofHours(1)) < 0
						|| refreshTokenTtl.compareTo(Duration.ofDays(90)) > 0
						|| (accessTokenTtl != null
								&& refreshTokenTtl.compareTo(accessTokenTtl) <= 0))) {
			throw new IllegalArgumentException(
					"pulseops.auth.refresh-token-ttl must be longer than the access token "
							+ "and between one hour and 90 days.");
		}
		allowedOrigins = allowedOrigins == null
				? List.of()
				: allowedOrigins.stream().map(String::trim).toList();
		for (var origin : allowedOrigins) {
			requireSafeOrigin(origin);
		}
	}

	@Override
	public List<String> allowedOrigins() {
		return List.copyOf(allowedOrigins);
	}

	private static void requireSafeOrigin(String origin) {
		final URI uri;
		try {
			uri = new URI(origin);
		}
		catch (URISyntaxException exception) {
			throw invalidOrigin();
		}
		var scheme = uri.getScheme();
		if (origin.contains("*")
				|| scheme == null
				|| (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
				|| uri.getHost() == null
				|| uri.getUserInfo() != null
				|| (uri.getPath() != null && !uri.getPath().isEmpty())
				|| uri.getQuery() != null
				|| uri.getFragment() != null) {
			throw invalidOrigin();
		}
	}

	private static IllegalArgumentException invalidOrigin() {
		return new IllegalArgumentException(
				"pulseops.auth.allowed-origins must contain exact HTTP or HTTPS origins.");
	}
}
