package com.pulseops.membership;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OrganizationMembershipRepository
		extends JpaRepository<OrganizationMembership, UUID> {

	List<OrganizationMembership> findAllByUserIdAndStatusOrderByJoinedAtAsc(
			UUID userId,
			MembershipStatus status);

	List<OrganizationMembership> findAllByOrganizationIdAndStatusOrderByJoinedAtAsc(
			UUID organizationId,
			MembershipStatus status);

	Optional<OrganizationMembership> findByOrganizationIdAndUserId(
			UUID organizationId,
			UUID userId);

	Optional<OrganizationMembership> findByOrganizationIdAndUserIdAndStatus(
			UUID organizationId,
			UUID userId,
			MembershipStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OrganizationMembership> findLockedByOrganizationIdAndUserId(
			UUID organizationId,
			UUID userId);
}
