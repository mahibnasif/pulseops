package com.pulseops.organization.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.membership.MembershipRole;
import com.pulseops.organization.Organization;

public record OrganizationResponse(
		UUID id,
		String name,
		String slug,
		String description,
		UUID ownerId,
		MembershipRole currentUserRole,
		Instant createdAt,
		Instant updatedAt) {

	public static OrganizationResponse from(
			Organization organization,
			MembershipRole currentUserRole) {
		return new OrganizationResponse(
				organization.getId(),
				organization.getName(),
				organization.getSlug(),
				organization.getDescription(),
				organization.getOwnerId(),
				currentUserRole,
				organization.getCreatedAt(),
				organization.getUpdatedAt());
	}
}
