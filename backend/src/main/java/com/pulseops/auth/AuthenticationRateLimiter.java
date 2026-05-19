package com.pulseops.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationRateLimiter {

	private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
	private static final int MAX_FORWARDED_FOR_LENGTH = 512;
	private static final int MAX_CLIENT_IP_LENGTH = 45;

	private final AuthRateLimitProperties properties;
	private final Clock clock;
	private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

	public AuthenticationRateLimiter(AuthRateLimitProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	public void checkLogin(HttpServletRequest request, String email) {
		if (!properties.enabled()) {
			return;
		}
		var client = clientFingerprint(request);
		consume(
				"login:client:" + client,
				properties.loginAttempts(),
				properties.loginWindow());
		consume(
				"login:identity:" + fingerprint(normalizeEmail(email)),
				properties.loginAttempts(),
				properties.loginWindow());
	}

	public void checkRegistration(HttpServletRequest request) {
		if (properties.enabled()) {
			consume(
					"register:client:" + clientFingerprint(request),
					properties.registrationAttempts(),
					properties.registrationWindow());
		}
	}

	public void checkRefresh(HttpServletRequest request) {
		if (properties.enabled()) {
			consume(
					"refresh:client:" + clientFingerprint(request),
					properties.refreshAttempts(),
					properties.refreshWindow());
		}
	}

	private void consume(String key, int limit, Duration window) {
		var now = Instant.now(clock);
		if (!counters.containsKey(key) && counters.size() >= properties.maxTrackedKeys()) {
			removeExpired(now);
			if (counters.size() >= properties.maxTrackedKeys()) {
				throw new AuthenticationRateLimitException(1);
			}
		}

		var counter = counters.compute(key, (ignored, current) -> {
			if (current == null || !now.isBefore(current.resetsAt())) {
				return new Counter(1, now.plus(window));
			}
			return new Counter(current.attempts() + 1, current.resetsAt());
		});
		if (counter.attempts() > limit) {
			var retryAfter = Math.max(1, Duration.between(now, counter.resetsAt()).toSeconds());
			throw new AuthenticationRateLimitException(retryAfter);
		}
	}

	private String clientFingerprint(HttpServletRequest request) {
		var address = request.getRemoteAddr();
		if (properties.trustProxyClientIp()) {
			var forwarded = lastForwardedAddress(request.getHeader(FORWARDED_FOR_HEADER));
			if (forwarded != null) {
				address = forwarded;
			}
		}
		return fingerprint(address == null ? "unknown" : address);
	}

	private String lastForwardedAddress(String value) {
		if (value == null || value.isBlank() || value.length() > MAX_FORWARDED_FOR_LENGTH) {
			return null;
		}
		var separator = value.lastIndexOf(',');
		return normalizeAddressLiteral(value.substring(separator + 1).trim());
	}

	private String normalizeAddressLiteral(String value) {
		if (value == null
				|| value.isBlank()
				|| value.length() > MAX_CLIENT_IP_LENGTH
				|| !value.matches("[0-9A-Fa-f:.]+")) {
			return null;
		}
		try {
			return InetAddress.getByName(value).getHostAddress();
		}
		catch (UnknownHostException exception) {
			return null;
		}
	}

	private String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}

	private String fingerprint(String value) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(
					digest.digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private void removeExpired(Instant now) {
		counters.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().resetsAt()));
	}

	int trackedKeyCount() {
		return counters.size();
	}

	private record Counter(int attempts, Instant resetsAt) {
	}
}
