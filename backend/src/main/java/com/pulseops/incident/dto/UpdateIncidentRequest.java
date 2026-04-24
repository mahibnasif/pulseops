package com.pulseops.incident.dto;

import jakarta.validation.constraints.Size;

import com.pulseops.incident.IncidentSeverity;
import com.pulseops.incident.IncidentStatus;

public record UpdateIncidentRequest(
		@Size(max = 200) String title,
		@Size(max = 4000) String description,
		IncidentSeverity severity,
		IncidentStatus status,
		@Size(max = 4000) String rootCause,
		@Size(max = 4000) String resolutionSummary) {
}
