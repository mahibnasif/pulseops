package com.pulseops.auth.dto;

import java.util.UUID;

import com.pulseops.user.User;

public record UserResponse(
		UUID id,
		String firstName,
		String lastName,
		String email,
		boolean emailVerified,
		String accountStatus) {

	public static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getFirstName(),
				user.getLastName(),
				user.getEmail(),
				user.isEmailVerified(),
				user.getAccountStatus().name());
	}
}
