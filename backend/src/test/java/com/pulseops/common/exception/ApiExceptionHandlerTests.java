package com.pulseops.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.pulseops.auth.AuthRateLimitProperties;
import com.pulseops.auth.AuthenticationRateLimitException;
import com.pulseops.auth.AuthenticationRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiExceptionHandlerTests {

	@Test
	void rateLimitResponsesIncludeRetryAfterWithoutLeakingTheKey() {
		var clock = Clock.fixed(
				Instant.parse("2026-07-30T12:00:00Z"),
				ZoneOffset.UTC);
		var properties = new AuthRateLimitProperties(
				true,
				1,
				Duration.ofMinutes(5),
				1,
				Duration.ofHours(1),
				1,
				Duration.ofMinutes(5),
				100,
				false);
		var limiter = new AuthenticationRateLimiter(properties, clock);
		var request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.20");
		request.setRequestURI("/api/v1/auth/login");
		limiter.checkLogin(request, "operator@example.com");
		var exception = catchThrowableOfType(
				() -> limiter.checkLogin(request, "operator@example.com"),
				AuthenticationRateLimitException.class);

		var response = new ApiExceptionHandler(clock)
				.handleApiException(exception, request);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("300");
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("AUTH_RATE_LIMIT_EXCEEDED");
		assertThat(response.getBody().message()).doesNotContain("operator@example.com");
	}
}
