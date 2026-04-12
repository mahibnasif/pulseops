package com.pulseops.organization;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "organizations")
public class Organization {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, unique = true, length = 80)
	private String slug;

	@Column(length = 1000)
	private String description;

	@Column(name = "owner_id", nullable = false)
	private UUID ownerId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Organization() {
	}

	private Organization(
			String name,
			String slug,
			String description,
			UUID ownerId,
			Instant now) {
		this.name = name;
		this.slug = slug;
		this.description = description;
		this.ownerId = ownerId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static Organization create(
			String name,
			String slug,
			String description,
			UUID ownerId,
			Instant now) {
		return new Organization(name, slug, description, ownerId, now);
	}

	public void update(String newName, String newSlug, String newDescription, Instant now) {
		if (newName != null) {
			name = newName;
		}
		if (newSlug != null) {
			slug = newSlug;
		}
		if (newDescription != null) {
			description = newDescription;
		}
		updatedAt = now;
	}

	public void transferOwnership(UUID newOwnerId, Instant now) {
		ownerId = newOwnerId;
		updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getSlug() {
		return slug;
	}

	public String getDescription() {
		return description;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
