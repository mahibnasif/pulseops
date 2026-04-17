package com.pulseops.monitoredservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pulseops.monitoring")
public record MonitoringProperties(
		boolean allowPrivateTargets,
		int maxResponseBytes) {

	public MonitoringProperties {
		if (maxResponseBytes < 1024 || maxResponseBytes > 1_048_576) {
			throw new IllegalArgumentException(
					"pulseops.monitoring.max-response-bytes must be between 1024 and 1048576.");
		}
	}
}
