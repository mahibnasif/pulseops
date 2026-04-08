package com.pulseops.auth;

import com.pulseops.user.User;

public record AuthSession(
		String accessToken,
		long accessTokenExpiresInSeconds,
		String refreshToken,
		User user) {
}
