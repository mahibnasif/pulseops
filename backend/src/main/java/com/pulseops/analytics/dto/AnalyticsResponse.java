package com.pulseops.analytics.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalyticsResponse(
		Range range,
		ServiceSummary services,
		ReliabilitySummary reliability,
		IncidentSummary incidents,
		List<HealthPoint> healthTrend,
		List<IncidentPoint> incidentTrend,
		List<SeverityCount> incidentsBySeverity,
		List<ServiceReliability> serviceReliability) {

	public AnalyticsResponse {
		healthTrend = List.copyOf(healthTrend);
		incidentTrend = List.copyOf(incidentTrend);
		incidentsBySeverity = List.copyOf(incidentsBySeverity);
		serviceReliability = List.copyOf(serviceReliability);
	}

	public record Range(
			Instant from,
			Instant to,
			String bucket,
			String timezone,
			String uptimeMethod) {
	}

	public record ServiceSummary(
			long total,
			long operational,
			long degraded,
			long down,
			long paused,
			long unknown) {
	}

	public record ReliabilitySummary(
			long totalChecks,
			long successfulChecks,
			long failedChecks,
			Double uptimePercentage,
			Double averageResponseTimeMilliseconds,
			Double p50ResponseTimeMilliseconds,
			Double p95ResponseTimeMilliseconds) {
	}

	public record IncidentSummary(
			long total,
			long active,
			long critical,
			Double meanTimeToAcknowledgeMinutes,
			Double meanTimeToResolveMinutes,
			Double longestIncidentMinutes) {
	}

	public record HealthPoint(
			Instant bucketStart,
			long totalChecks,
			long successfulChecks,
			long failedChecks,
			Double uptimePercentage,
			Double averageResponseTimeMilliseconds) {
	}

	public record IncidentPoint(
			Instant bucketStart,
			long totalIncidents,
			long resolvedIncidents) {
	}

	public record SeverityCount(String severity, long count) {
	}

	public record ServiceReliability(
			UUID serviceId,
			String serviceName,
			String status,
			long totalChecks,
			long failedChecks,
			Double uptimePercentage,
			Double averageResponseTimeMilliseconds,
			Double p95ResponseTimeMilliseconds,
			long incidentCount) {
	}
}
