package com.pulseops.healthcheck.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.healthcheck.CheckSource;
import com.pulseops.healthcheck.HealthCheckResult;
import com.pulseops.monitoredservice.CheckErrorType;
import com.pulseops.monitoredservice.ServiceStatus;

public record HealthCheckResponse(
		UUID id,
		UUID serviceId,
		Instant checkedAt,
		boolean success,
		boolean degraded,
		Integer statusCode,
		long responseTimeMilliseconds,
		CheckErrorType errorType,
		String errorMessage,
		boolean responseValidationPassed,
		String responseExcerpt,
		CheckSource checkSource,
		ServiceStatus statusBefore,
		ServiceStatus statusAfter,
		boolean affectsServiceStatus,
		Instant createdAt) {

	public static HealthCheckResponse from(HealthCheckResult result) {
		return new HealthCheckResponse(
				result.getId(), result.getServiceId(), result.getCheckedAt(),
				result.isSuccess(), result.isDegraded(), result.getStatusCode(),
				result.getResponseTimeMilliseconds(), result.getErrorType(),
				result.getErrorMessage(), result.isResponseValidationPassed(),
				result.getResponseExcerpt(), result.getCheckSource(),
				result.getStatusBefore(), result.getStatusAfter(),
				result.isAppliedToStatus(), result.getCreatedAt());
	}
}
