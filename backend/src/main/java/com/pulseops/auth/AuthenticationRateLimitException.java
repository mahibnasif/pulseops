package com.pulseops.auth;

import com.pulseops.common.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AuthenticationRateLimitException extends ApiException {

	private final long retryAfterSeconds;

	AuthenticationRateLimitException(long retryAfterSeconds) {
		super(
				HttpStatus.TOO_MANY_REQUESTS,
				"AUTH_RATE_LIMIT_EXCEEDED",
				"Too many authentication attempts. Try again later.");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
