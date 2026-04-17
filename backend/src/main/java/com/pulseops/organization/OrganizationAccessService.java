package com.pulseops.organization;

import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.membership.MembershipRole;
import com.pulseops.membership.MembershipStatus;
import com.pulseops.membership.OrganizationMembership;
import com.pulseops.membership.OrganizationMembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrganizationAccessService {

	private final OrganizationRepository organizationRepository;
	private final OrganizationMembershipRepository membershipRepository;

	public OrganizationAccessService(
			OrganizationRepository organizationRepository,
			OrganizationMembershipRepository membershipRepository) {
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
	}

	public OrganizationMembership requireMember(UUID organizationId, UUID userId) {
		var membership = membershipRepository
				.findByOrganizationIdAndUserIdAndStatus(
						organizationId,
						userId,
						MembershipStatus.ACTIVE)
				.orElseThrow(this::organizationNotFound);
		if (!organizationRepository.existsById(organizationId)) {
			throw organizationNotFound();
		}
		return membership;
	}

	public OrganizationMembership requireAdmin(UUID organizationId, UUID userId) {
		var membership = requireMember(organizationId, userId);
		if (membership.getRole() != MembershipRole.ADMIN) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"INSUFFICIENT_ORGANIZATION_ROLE",
					"Administrator access is required for this organization.");
		}
		return membership;
	}

	public OrganizationMembership requireEngineer(UUID organizationId, UUID userId) {
		var membership = requireMember(organizationId, userId);
		if (membership.getRole() == MembershipRole.VIEWER) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"INSUFFICIENT_ORGANIZATION_ROLE",
					"Engineer or administrator access is required for this organization.");
		}
		return membership;
	}

	private ApiException organizationNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"ORGANIZATION_NOT_FOUND",
				"The organization could not be found.");
	}
}
