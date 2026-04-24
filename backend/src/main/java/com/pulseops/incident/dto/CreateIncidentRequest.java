package com.pulseops.incident.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.pulseops.incident.IncidentSeverity;

public record CreateIncidentRequest(
		@NotNull UUID serviceId,
		@NotBlank @Size(max = 200) String title,
		@Size(max = 4000) String description,
		@NotNull IncidentSeverity severity) {
}
