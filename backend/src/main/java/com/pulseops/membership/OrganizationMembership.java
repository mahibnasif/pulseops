package com.pulseops.membership;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "organization_memberships")
public class OrganizationMembership {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MembershipRole role;

	@Enumerated(EnumType.STRING)
	@Column(name = "membership_status", nullable = false, length = 32)
	private MembershipStatus status;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected OrganizationMembership() {
	}

	private OrganizationMembership(
			UUID organizationId,
			UUID userId,
			MembershipRole role,
			Instant now) {
		this.organizationId = organizationId;
		this.userId = userId;
		this.role = role;
		this.status = MembershipStatus.ACTIVE;
		this.joinedAt = now;
		this.updatedAt = now;
	}

	public static OrganizationMembership active(
			UUID organizationId,
			UUID userId,
			MembershipRole role,
			Instant now) {
		return new OrganizationMembership(organizationId, userId, role, now);
	}

	public void changeRole(MembershipRole newRole, Instant now) {
		role = newRole;
		updatedAt = now;
	}

	public void reactivate(MembershipRole newRole, Instant now) {
		role = newRole;
		status = MembershipStatus.ACTIVE;
		joinedAt = now;
		updatedAt = now;
	}

	public void leave(Instant now) {
		status = MembershipStatus.LEFT;
		updatedAt = now;
	}

	public void remove(Instant now) {
		status = MembershipStatus.REMOVED;
		updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public UUID getUserId() {
		return userId;
	}

	public MembershipRole getRole() {
		return role;
	}

	public MembershipStatus getStatus() {
		return status;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}
