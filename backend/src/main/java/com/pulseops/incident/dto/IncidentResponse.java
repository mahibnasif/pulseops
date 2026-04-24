package com.pulseops.incident.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.incident.Incident;
import com.pulseops.incident.IncidentSeverity;
import com.pulseops.incident.IncidentSource;
import com.pulseops.incident.IncidentStatus;

public record IncidentResponse(
		UUID id,
		UUID organizationId,
		UUID serviceId,
		String title,
		String description,
		IncidentSeverity severity,
		IncidentStatus status,
		IncidentSource source,
		UUID assignedUserId,
		Instant detectedAt,
		Instant acknowledgedAt,
		Instant resolvedAt,
		String rootCause,
		String resolutionSummary,
		UUID createdBy,
		Instant createdAt,
		Instant updatedAt) {

	public static IncidentResponse from(Incident incident) {
		return new IncidentResponse(
				incident.getId(), incident.getOrganizationId(), incident.getServiceId(),
				incident.getTitle(), incident.getDescription(), incident.getSeverity(),
				incident.getStatus(), incident.getSource(), incident.getAssignedUserId(),
				incident.getDetectedAt(), incident.getAcknowledgedAt(),
				incident.getResolvedAt(), incident.getRootCause(),
				incident.getResolutionSummary(), incident.getCreatedBy(),
				incident.getCreatedAt(), incident.getUpdatedAt());
	}
}
