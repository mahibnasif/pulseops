package com.pulseops.monitoredservice;

public record ServiceConfiguration(
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
		int degradedLatencyThresholdMilliseconds) {
}
