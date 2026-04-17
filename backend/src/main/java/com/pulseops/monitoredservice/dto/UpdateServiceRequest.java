package com.pulseops.monitoredservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import com.pulseops.monitoredservice.HttpMethod;
import com.pulseops.monitoredservice.ServiceType;

public record UpdateServiceRequest(
		@Size(min = 1, max = 160) String name,
		@Size(max = 1000) String description,
		ServiceType serviceType,
		@Size(min = 1, max = 2048) String url,
		HttpMethod httpMethod,
		@Min(100) @Max(599) Integer expectedStatusCode,
		@Size(max = 500) String expectedResponseText,
		@Size(max = 500) String expectedJsonPath,
		@Size(max = 500) String expectedJsonValue,
		@Min(250) @Max(60000) Integer timeoutMilliseconds,
		@Min(30) @Max(86400) Integer checkIntervalSeconds,
		@Min(1) @Max(20) Integer failureThreshold,
		@Min(1) @Max(20) Integer recoveryThreshold,
		@Min(1) @Max(60000) Integer degradedLatencyThresholdMilliseconds) {
}
