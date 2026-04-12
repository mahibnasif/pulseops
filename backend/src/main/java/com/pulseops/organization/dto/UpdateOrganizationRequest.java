package com.pulseops.organization.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
		@Size(min = 1, max = 120) String name,
		@Size(min = 1, max = 80)
		@Pattern(
				regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
				message = "must contain lowercase letters, numbers, and single hyphens")
		String slug,
		@Size(max = 1000) String description) {
}
