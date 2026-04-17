package com.pulseops.monitoredservice.dto;

import java.time.Instant;

import com.pulseops.monitoredservice.CheckErrorType;
import com.pulseops.monitoredservice.ManualCheckResult;

public record ManualCheckResponse(
		Instant checkedAt,
		boolean success,
		boolean degraded,
		Integer statusCode,
		long responseTimeMilliseconds,
		CheckErrorType errorType,
		String errorMessage,
		boolean responseValidationPassed,
		String responseExcerpt,
		boolean affectsServiceStatus) {

	public static ManualCheckResponse from(ManualCheckResult result) {
		return new ManualCheckResponse(
				result.checkedAt(), result.success(), result.degraded(),
				result.statusCode(), result.responseTimeMilliseconds(),
				result.errorType(), result.errorMessage(),
				result.responseValidationPassed(), result.responseExcerpt(), false);
	}
}
