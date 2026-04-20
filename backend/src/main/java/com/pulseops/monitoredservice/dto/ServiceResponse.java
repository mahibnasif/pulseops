package com.pulseops.monitoredservice.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.monitoredservice.HttpMethod;
import com.pulseops.monitoredservice.MonitoredService;
import com.pulseops.monitoredservice.ServiceStatus;
import com.pulseops.monitoredservice.ServiceType;

public record ServiceResponse(
		UUID id,
		UUID organizationId,
		String name,
		String description,
		ServiceType serviceType,
		String url,
		HttpMethod httpMethod,
		int expectedStatusCode,
		String expectedResponseText,
		String expectedJsonPath,
		String expectedJsonValue,
		int timeoutMilliseconds,
		int checkIntervalSeconds,
		int failureThreshold,
		int recoveryThreshold,
		int degradedLatencyThresholdMilliseconds,
		ServiceStatus status,
		boolean active,
		UUID createdBy,
		Instant createdAt,
		Instant updatedAt,
		Instant lastCheckedAt,
		Instant lastSuccessfulCheckAt,
		Instant lastFailureAt,
		Instant nextCheckAt,
		int consecutiveFailures,
		int consecutiveSuccesses,
		Instant lastStatusChangedAt) {

	public static ServiceResponse from(MonitoredService service) {
		return new ServiceResponse(
				service.getId(), service.getOrganizationId(), service.getName(),
				service.getDescription(), service.getServiceType(), service.getUrl(),
				service.getHttpMethod(), service.getExpectedStatusCode(),
				service.getExpectedResponseText(), service.getExpectedJsonPath(),
				service.getExpectedJsonValue(), service.getTimeoutMilliseconds(),
				service.getCheckIntervalSeconds(), service.getFailureThreshold(),
				service.getRecoveryThreshold(),
				service.getDegradedLatencyThresholdMilliseconds(),
				service.getStatus(), service.isActive(), service.getCreatedBy(),
				service.getCreatedAt(), service.getUpdatedAt(),
				service.getLastCheckedAt(), service.getLastSuccessfulCheckAt(),
				service.getLastFailureAt(), service.getNextCheckAt(),
				service.getConsecutiveFailures(), service.getConsecutiveSuccesses(),
				service.getLastStatusChangedAt());
	}
}
