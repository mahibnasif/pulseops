package com.pulseops.membership.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.membership.MembershipRole;
import com.pulseops.membership.MembershipStatus;
import com.pulseops.membership.OrganizationMembership;
import com.pulseops.user.User;

public record MemberResponse(
		UUID membershipId,
		UUID userId,
		String firstName,
		String lastName,
		String email,
		MembershipRole role,
		MembershipStatus status,
		Instant joinedAt) {

	public static MemberResponse from(OrganizationMembership membership, User user) {
		return new MemberResponse(
				membership.getId(),
				user.getId(),
				user.getFirstName(),
				user.getLastName(),
				user.getEmail(),
				membership.getRole(),
				membership.getStatus(),
				membership.getJoinedAt());
	}
}
