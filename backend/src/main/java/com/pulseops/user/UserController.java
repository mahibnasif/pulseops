package com.pulseops.user;

import com.pulseops.auth.dto.UserResponse;
import com.pulseops.common.security.CurrentUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final CurrentUserService currentUserService;

	public UserController(CurrentUserService currentUserService) {
		this.currentUserService = currentUserService;
	}

	@GetMapping("/me")
	@SecurityRequirement(name = "bearerAuth")
	UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
		return UserResponse.from(currentUserService.requireUser(jwt));
	}
}
