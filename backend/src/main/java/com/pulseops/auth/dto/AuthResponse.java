package com.pulseops.auth.dto;

import com.pulseops.auth.AuthSession;

public record AuthResponse(
		String accessToken,
		String tokenType,
		long expiresIn,
		UserResponse user) {

	public static AuthResponse from(AuthSession session) {
		return new AuthResponse(
				session.accessToken(),
				"Bearer",
				session.accessTokenExpiresInSeconds(),
				UserResponse.from(session.user()));
	}
}
