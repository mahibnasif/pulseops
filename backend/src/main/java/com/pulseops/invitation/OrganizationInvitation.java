package com.pulseops.invitation;

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

import com.pulseops.membership.MembershipRole;

@Entity
@Table(name = "organization_invitations")
public class OrganizationInvitation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(nullable = false, length = 320)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MembershipRole role;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected OrganizationInvitation() {
	}

	private OrganizationInvitation(
			UUID organizationId,
			String email,
			MembershipRole role,
			String tokenHash,
			Instant expiresAt,
			UUID createdBy,
			Instant now) {
		this.organizationId = organizationId;
		this.email = email;
		this.role = role;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	public static OrganizationInvitation create(
			UUID organizationId,
			String email,
			MembershipRole role,
			String tokenHash,
			Instant expiresAt,
			UUID createdBy,
			Instant now) {
		return new OrganizationInvitation(
				organizationId,
				email,
				role,
				tokenHash,
				expiresAt,
				createdBy,
				now);
	}

	public boolean isPendingAt(Instant now) {
		return acceptedAt == null && expiresAt.isAfter(now);
	}

	public void accept(Instant now) {
		acceptedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getOrganizationId() {
		return organizationId;
	}

	public String getEmail() {
		return email;
	}

	public MembershipRole getRole() {
		return role;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public UUID getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
