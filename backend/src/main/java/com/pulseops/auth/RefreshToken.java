package com.pulseops.auth;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "replaced_by_token_hash", length = 64)
	private String replacedByTokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RefreshToken() {
	}

	private RefreshToken(
			User user,
			String tokenHash,
			UUID familyId,
			Instant expiresAt,
			Instant createdAt) {
		this.user = user;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public static RefreshToken issue(
			User user,
			String tokenHash,
			UUID familyId,
			Instant expiresAt,
			Instant createdAt) {
		return new RefreshToken(user, tokenHash, familyId, expiresAt, createdAt);
	}

	public void revoke(Instant now, String replacementHash) {
		if (revokedAt == null) {
			revokedAt = now;
			replacedByTokenHash = replacementHash;
		}
	}

	public User getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}
}
