package com.pulseops.auth;

import com.pulseops.auth.dto.AuthResponse;
import com.pulseops.auth.dto.LoginRequest;
import com.pulseops.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	static final String REFRESH_COOKIE_NAME = "pulseops_refresh";

	private final AuthService authService;
	private final AuthProperties properties;

	public AuthController(AuthService authService, AuthProperties properties) {
		this.authService = authService;
		this.properties = properties;
	}

	@PostMapping("/register")
	ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return authenticatedResponse(authService.register(request), HttpStatus.CREATED);
	}

	@PostMapping("/login")
	ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return authenticatedResponse(authService.login(request), HttpStatus.OK);
	}

	@PostMapping("/refresh")
	ResponseEntity<AuthResponse> refresh(
			@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
		var session = authService.refresh(requireRefreshToken(refreshToken));
		return authenticatedResponse(session, HttpStatus.OK);
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(
			@CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			authService.logout(refreshToken);
		}

		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
				.build();
	}

	private ResponseEntity<AuthResponse> authenticatedResponse(AuthSession session, HttpStatus status) {
		return ResponseEntity.status(status)
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.header(HttpHeaders.PRAGMA, "no-cache")
				.header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken()).toString())
				.body(AuthResponse.from(session));
	}

	private ResponseCookie refreshCookie(String refreshToken) {
		return ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
				.httpOnly(true)
				.secure(properties.refreshCookieSecure())
				.sameSite("Strict")
				.path("/api/v1/auth")
				.maxAge(properties.refreshTokenTtl())
				.build();
	}

	private ResponseCookie clearRefreshCookie() {
		return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
				.httpOnly(true)
				.secure(properties.refreshCookieSecure())
				.sameSite("Strict")
				.path("/api/v1/auth")
				.maxAge(0)
				.build();
	}

	private String requireRefreshToken(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new com.pulseops.common.exception.ApiException(
					HttpStatus.UNAUTHORIZED,
					"INVALID_REFRESH_TOKEN",
					"The refresh session is invalid or expired.");
		}
		return refreshToken;
	}
}
