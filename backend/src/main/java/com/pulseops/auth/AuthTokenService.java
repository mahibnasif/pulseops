package com.pulseops.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import com.pulseops.user.User;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithms;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class AuthTokenService {

	private static final int REFRESH_TOKEN_BYTES = 32;

	private final JwtEncoder jwtEncoder;
	private final AuthProperties properties;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public AuthTokenService(JwtEncoder jwtEncoder, AuthProperties properties, Clock clock) {
		this.jwtEncoder = jwtEncoder;
		this.properties = properties;
		this.clock = clock;
	}

	public String issueAccessToken(User user) {
		var now = Instant.now(clock);
		var claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(user.getId().toString())
				.audience(java.util.List.of(properties.audience()))
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTokenTtl()))
				.id(UUID.randomUUID().toString())
				.claim("email", user.getEmail())
				.claim("scope", "user")
				.build();
		var header = JwsHeader.with(() -> JwsAlgorithms.HS256)
				.type("JWT")
				.build();

		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	public String generateRefreshToken() {
		var bytes = new byte[REFRESH_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hashRefreshToken(String rawToken) {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}
}
