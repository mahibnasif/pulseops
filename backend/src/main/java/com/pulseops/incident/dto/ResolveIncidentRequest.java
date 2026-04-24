package com.pulseops.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveIncidentRequest(
		@Size(max = 4000) String rootCause,
		@NotBlank @Size(max = 4000) String resolutionSummary) {
}
