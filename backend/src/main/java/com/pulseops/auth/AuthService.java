package com.pulseops.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.pulseops.auth.dto.LoginRequest;
import com.pulseops.auth.dto.RegisterRequest;
import com.pulseops.common.exception.ApiException;
import com.pulseops.user.AccountStatus;
import com.pulseops.user.User;
import com.pulseops.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final String INVALID_CREDENTIALS_MESSAGE = "Email or password is incorrect.";
	private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuthTokenService tokenService;
	private final AuthProperties properties;
	private final Clock clock;
	private final String dummyPasswordHash;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			AuthTokenService tokenService,
			AuthProperties properties,
			Clock clock) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.properties = properties;
		this.clock = clock;
		this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public AuthSession register(RegisterRequest request) {
		validatePasswordLength(request.password());
		var email = normalizeEmail(request.email());

		if (userRepository.existsByEmail(email)) {
			throw registrationConflict();
		}

		var now = Instant.now(clock);
		var user = User.registered(
				request.firstName().trim(),
				request.lastName().trim(),
				email,
				passwordEncoder.encode(request.password()),
				now);

		try {
			userRepository.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException exception) {
			throw registrationConflict();
		}

		user.recordLogin(now);
		return createSession(user, UUID.randomUUID(), now);
	}

	@Transactional
	public AuthSession login(LoginRequest request) {
		var email = normalizeEmail(request.email());
		var user = userRepository.findByEmail(email).orElse(null);
		var encodedPassword = user == null ? dummyPasswordHash : user.getPasswordHash();
		var passwordMatches = passwordEncoder.matches(request.password(), encodedPassword);

		if (user == null || !passwordMatches) {
			throw new ApiException(
					HttpStatus.UNAUTHORIZED,
					"INVALID_CREDENTIALS",
					INVALID_CREDENTIALS_MESSAGE);
		}
		ensureActive(user);

		var now = Instant.now(clock);
		user.recordLogin(now);
		return createSession(user, UUID.randomUUID(), now);
	}

	@Transactional(noRollbackFor = ApiException.class)
	public AuthSession refresh(String rawRefreshToken) {
		var now = Instant.now(clock);
		var tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
		var currentToken = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
				.orElseThrow(this::invalidRefreshToken);

		if (currentToken.isRevoked()) {
			refreshTokenRepository.revokeActiveFamily(currentToken.getFamilyId(), now);
			throw invalidRefreshToken();
		}
		if (currentToken.isExpired(now)) {
			currentToken.revoke(now, null);
			throw invalidRefreshToken();
		}

		var user = currentToken.getUser();
		ensureActive(user);

		var replacementValue = tokenService.generateRefreshToken();
		var replacementHash = tokenService.hashRefreshToken(replacementValue);
		currentToken.revoke(now, replacementHash);
		refreshTokenRepository.save(RefreshToken.issue(
				user,
				replacementHash,
				currentToken.getFamilyId(),
				now.plus(properties.refreshTokenTtl()),
				now));

		return new AuthSession(
				tokenService.issueAccessToken(user),
				properties.accessTokenTtl().toSeconds(),
				replacementValue,
				user);
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		var now = Instant.now(clock);
		var tokenHash = tokenService.hashRefreshToken(rawRefreshToken);
		refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
				.filter(token -> !token.isRevoked())
				.ifPresent(token -> token.revoke(now, null));
	}

	private AuthSession createSession(User user, UUID familyId, Instant now) {
		var refreshTokenValue = tokenService.generateRefreshToken();
		refreshTokenRepository.save(RefreshToken.issue(
				user,
				tokenService.hashRefreshToken(refreshTokenValue),
				familyId,
				now.plus(properties.refreshTokenTtl()),
				now));

		return new AuthSession(
				tokenService.issueAccessToken(user),
				properties.accessTokenTtl().toSeconds(),
				refreshTokenValue,
				user);
	}

	private void validatePasswordLength(String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"VALIDATION_ERROR",
					"Password must not exceed 72 UTF-8 bytes.");
		}
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private void ensureActive(User user) {
		if (user.getAccountStatus() != AccountStatus.ACTIVE) {
			throw new ApiException(
					HttpStatus.UNAUTHORIZED,
					"AUTHENTICATION_FAILED",
					"Authentication could not be completed.");
		}
	}

	private ApiException registrationConflict() {
		return new ApiException(
				HttpStatus.CONFLICT,
				"REGISTRATION_FAILED",
				"An account could not be created with the supplied information.");
	}

	private ApiException invalidRefreshToken() {
		return new ApiException(
				HttpStatus.UNAUTHORIZED,
				"INVALID_REFRESH_TOKEN",
				"The refresh session is invalid or expired.");
	}
}
