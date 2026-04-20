package com.pulseops.monitoredservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MonitoredServiceStateTests {

	private static final Instant CREATED_AT = Instant.parse("2026-07-30T10:00:00Z");

	@Test
	void requiresConsecutiveFailuresBeforeGoingDown() {
		var service = service();

		service.applyCompletedCheck(failure(1), CREATED_AT.plusSeconds(1), false);
		service.applyCompletedCheck(failure(2), CREATED_AT.plusSeconds(2), false);

		assertThat(service.getStatus()).isEqualTo(ServiceStatus.UNKNOWN);
		assertThat(service.getConsecutiveFailures()).isEqualTo(2);

		service.applyCompletedCheck(failure(3), CREATED_AT.plusSeconds(3), false);

		assertThat(service.getStatus()).isEqualTo(ServiceStatus.DOWN);
		assertThat(service.getConsecutiveFailures()).isEqualTo(3);
	}

	@Test
	void requiresRecoveryThresholdAfterAServiceIsDown() {
		var service = service();
		for (var index = 1; index <= 3; index++) {
			service.applyCompletedCheck(
					failure(index),
					CREATED_AT.plusSeconds(index),
					false);
		}

		service.applyCompletedCheck(success(4, false), CREATED_AT.plusSeconds(4), false);
		assertThat(service.getStatus()).isEqualTo(ServiceStatus.DOWN);
		assertThat(service.getConsecutiveSuccesses()).isEqualTo(1);

		service.applyCompletedCheck(success(5, false), CREATED_AT.plusSeconds(5), false);
		assertThat(service.getStatus()).isEqualTo(ServiceStatus.OPERATIONAL);
		assertThat(service.getConsecutiveSuccesses()).isEqualTo(2);
	}

	@Test
	void successfulSlowChecksAreDegradedAndTransientFailuresKeepPriorStatus() {
		var service = service();

		service.applyCompletedCheck(success(1, true), CREATED_AT.plusSeconds(1), false);
		assertThat(service.getStatus()).isEqualTo(ServiceStatus.DEGRADED);

		service.applyCompletedCheck(failure(2), CREATED_AT.plusSeconds(2), false);
		assertThat(service.getStatus()).isEqualTo(ServiceStatus.DEGRADED);

		service.applyCompletedCheck(success(3, false), CREATED_AT.plusSeconds(3), false);
		assertThat(service.getStatus()).isEqualTo(ServiceStatus.OPERATIONAL);
		assertThat(service.getConsecutiveFailures()).isZero();
	}

	@Test
	void scheduledCompletionSetsTheNextRunAndPausedChecksAreNotApplied() {
		var service = service();
		var completion = CREATED_AT.plusSeconds(10);

		service.applyCompletedCheck(success(1, false), completion, true);
		assertThat(service.getNextCheckAt()).isEqualTo(completion.plusSeconds(60));

		service.pause(completion.plusSeconds(1));
		var applied = service.applyCompletedCheck(
				failure(2),
				completion.plusSeconds(2),
				true);

		assertThat(applied).isFalse();
		assertThat(service.getStatus()).isEqualTo(ServiceStatus.PAUSED);
		assertThat(service.getNextCheckAt()).isNull();
	}

	private MonitoredService service() {
		return MonitoredService.create(
				UUID.randomUUID(),
				UUID.randomUUID(),
				new ServiceConfiguration(
						"API",
						null,
						ServiceType.HTTPS,
						"https://example.com/health",
						HttpMethod.GET,
						200,
						null,
						null,
						null,
						5000,
						60,
						3,
						2,
						1000),
				CREATED_AT);
	}

	private ManualCheckResult success(int seconds, boolean degraded) {
		return new ManualCheckResult(
				CREATED_AT.plusSeconds(seconds),
				true,
				degraded,
				200,
				degraded ? 1200 : 42,
				null,
				null,
				true,
				"healthy");
	}

	private ManualCheckResult failure(int seconds) {
		return new ManualCheckResult(
				CREATED_AT.plusSeconds(seconds),
				false,
				false,
				503,
				25,
				CheckErrorType.UNEXPECTED_STATUS,
				"The response status did not match the configured expectation.",
				false,
				"unavailable");
	}
}
