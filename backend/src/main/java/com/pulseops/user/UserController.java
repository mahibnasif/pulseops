package com.pulseops.user;

import java.util.UUID;

import com.pulseops.auth.dto.UserResponse;
import com.pulseops.common.exception.ApiException;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserRepository userRepository;

	public UserController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@GetMapping("/me")
	@SecurityRequirement(name = "bearerAuth")
	UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
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
		return UserResponse.from(user);
	}

	private ApiException authenticationFailed() {
		return new ApiException(
				HttpStatus.UNAUTHORIZED,
				"AUTHENTICATION_FAILED",
				"Authentication could not be completed.");
	}
}
