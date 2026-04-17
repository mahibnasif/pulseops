package com.pulseops.monitoredservice;

import java.time.Instant;

public record ManualCheckResult(
		Instant checkedAt,
		boolean success,
		boolean degraded,
		Integer statusCode,
		long responseTimeMilliseconds,
		CheckErrorType errorType,
		String errorMessage,
		boolean responseValidationPassed,
		String responseExcerpt) {
}
