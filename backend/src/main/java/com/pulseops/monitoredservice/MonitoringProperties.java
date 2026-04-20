package com.pulseops.monitoredservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pulseops.monitoring")
public record MonitoringProperties(
		boolean allowPrivateTargets,
		int maxResponseBytes,
		boolean schedulerEnabled,
		int batchSize,
		long claimLeaseSeconds) {

	public MonitoringProperties {
		if (maxResponseBytes < 1024 || maxResponseBytes > 1_048_576) {
			throw new IllegalArgumentException(
					"pulseops.monitoring.max-response-bytes must be between 1024 and 1048576.");
		}
		if (batchSize < 1 || batchSize > 100) {
			throw new IllegalArgumentException(
					"pulseops.monitoring.batch-size must be between 1 and 100.");
		}
		if (claimLeaseSeconds < 60 || claimLeaseSeconds > 900) {
			throw new IllegalArgumentException(
					"pulseops.monitoring.claim-lease-seconds must be between 60 and 900.");
		}
	}
}
