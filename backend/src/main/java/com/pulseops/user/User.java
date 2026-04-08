package com.pulseops.user;

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
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "email_verified", nullable = false)
	private boolean emailVerified;

	@Enumerated(EnumType.STRING)
	@Column(name = "account_status", nullable = false, length = 32)
	private AccountStatus accountStatus;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected User() {
	}

	private User(String firstName, String lastName, String email, String passwordHash, Instant now) {
		this.firstName = firstName;
		this.lastName = lastName;
		this.email = email;
		this.passwordHash = passwordHash;
		this.emailVerified = false;
		this.accountStatus = AccountStatus.ACTIVE;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static User registered(
			String firstName,
			String lastName,
			String email,
			String passwordHash,
			Instant now) {
		return new User(firstName, lastName, email, passwordHash, now);
	}

	public void recordLogin(Instant now) {
		lastLoginAt = now;
		updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public boolean isEmailVerified() {
		return emailVerified;
	}

	public AccountStatus getAccountStatus() {
		return accountStatus;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}
}
