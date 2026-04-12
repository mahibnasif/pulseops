package com.pulseops.invitation.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.invitation.OrganizationInvitation;
import com.pulseops.membership.MembershipRole;
import com.pulseops.organization.Organization;

public record InvitationResponse(
		UUID id,
		UUID organizationId,
		String organizationName,
		String email,
		MembershipRole role,
		Instant expiresAt,
		Instant acceptedAt,
		Instant createdAt) {

	public static InvitationResponse from(
			OrganizationInvitation invitation,
			Organization organization) {
		return new InvitationResponse(
				invitation.getId(),
				invitation.getOrganizationId(),
				organization.getName(),
				invitation.getEmail(),
				invitation.getRole(),
				invitation.getExpiresAt(),
				invitation.getAcceptedAt(),
				invitation.getCreatedAt());
	}
}
