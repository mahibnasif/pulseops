package com.pulseops.invitation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OrganizationInvitationRepository
		extends JpaRepository<OrganizationInvitation, UUID> {

	List<OrganizationInvitation> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

	List<OrganizationInvitation> findAllByEmailAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
			String email,
			Instant now);

	Optional<OrganizationInvitation> findFirstByOrganizationIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
			UUID organizationId,
			String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OrganizationInvitation> findLockedById(UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<OrganizationInvitation> findLockedByTokenHash(String tokenHash);
}
