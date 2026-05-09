package com.pulseops.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthenticationRateLimiterTests {

	private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");

	@Test
	void rejectsLoginAttemptsByClientWithAUsefulRetryDelay() {
		var clock = new MutableClock(NOW);
		var limiter = new AuthenticationRateLimiter(properties(true, false), clock);
		var request = request("203.0.113.10");

		limiter.checkLogin(request, "operator@example.com");
		limiter.checkLogin(request, "operator@example.com");

		assertThatThrownBy(
				() -> limiter.checkLogin(request, "different@example.com"))
				.isInstanceOf(AuthenticationRateLimitException.class)
				.extracting("retryAfterSeconds")
				.isEqualTo(300L);
		assertThat(limiter.trackedKeyCount()).isEqualTo(2);
	}

	@Test
	void normalizesLoginIdentityBeforeRateLimiting() {
		var limiter = new AuthenticationRateLimiter(
				properties(true, false),
				new MutableClock(NOW));

		limiter.checkLogin(request("203.0.113.11"), " Operator@Example.com ");
		limiter.checkLogin(request("203.0.113.12"), "operator@example.com");

		assertThatThrownBy(() -> limiter.checkLogin(
				request("203.0.113.13"),
				"OPERATOR@EXAMPLE.COM"))
				.isInstanceOf(AuthenticationRateLimitException.class);
	}

	@Test
	void startsANewWindowAfterThePreviousWindowExpires() {
		var clock = new MutableClock(NOW);
		var limiter = new AuthenticationRateLimiter(properties(true, false), clock);
		var request = request("203.0.113.14");

		limiter.checkRegistration(request);
		limiter.checkRegistration(request);
		assertThatThrownBy(() -> limiter.checkRegistration(request))
				.isInstanceOf(AuthenticationRateLimitException.class);

		clock.advance(Duration.ofHours(1));

		assertThatCode(() -> limiter.checkRegistration(request))
				.doesNotThrowAnyException();
	}

	@Test
	void trustsAnOverwrittenProxyAddressHeaderOnlyWhenConfigured() {
		var limiter = new AuthenticationRateLimiter(
				properties(true, true),
				new MutableClock(NOW));
		var first = request("172.18.0.2");
		first.addHeader("X-Real-IP", "198.51.100.10");
		var second = request("172.18.0.3");
		second.addHeader("X-Real-IP", "198.51.100.10");

		limiter.checkRegistration(first);
		limiter.checkRegistration(second);

		assertThatThrownBy(() -> limiter.checkRegistration(second))
				.isInstanceOf(AuthenticationRateLimitException.class);
	}

	@Test
	void canBeDisabledForControlledTestEnvironments() {
		var limiter = new AuthenticationRateLimiter(
				properties(false, false),
				new MutableClock(NOW));
		var request = request("203.0.113.15");

		for (var index = 0; index < 20; index++) {
			limiter.checkLogin(request, "operator@example.com");
		}

		assertThat(limiter.trackedKeyCount()).isZero();
	}

	private static MockHttpServletRequest request(String remoteAddress) {
		var request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddress);
		return request;
	}

	private static AuthRateLimitProperties properties(
			boolean enabled,
			boolean trustProxyClientIp) {
		return new AuthRateLimitProperties(
				enabled,
				2,
				Duration.ofMinutes(5),
				2,
				Duration.ofHours(1),
				2,
				Duration.ofMinutes(5),
				100,
				trustProxyClientIp);
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
