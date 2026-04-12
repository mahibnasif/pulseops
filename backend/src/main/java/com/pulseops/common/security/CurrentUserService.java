package com.pulseops.common.security;

import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.user.AccountStatus;
import com.pulseops.user.User;
import com.pulseops.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

	private final UserRepository userRepository;

	public CurrentUserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User requireUser(Jwt jwt) {
		var subject = jwt == null ? null : jwt.getSubject();
		if (subject == null) {
			throw authenticationFailed();
		}

		UUID userId;
		try {
			userId = UUID.fromString(subject);
		}
		catch (IllegalArgumentException exception) {
			throw authenticationFailed();
		}

		var user = userRepository.findById(userId).orElseThrow(this::authenticationFailed);
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw authenticationFailed();
		}
		return user;
	}

	private ApiException authenticationFailed() {
		return new ApiException(
				HttpStatus.UNAUTHORIZED,
				"AUTHENTICATION_FAILED",
				"Authentication could not be completed.");
	}
}
