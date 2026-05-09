package com.pulseops.config;

import java.time.Clock;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.pulseops.auth.AuthProperties;
import com.pulseops.auth.AuthRateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuthProperties.class, AuthRateLimitProperties.class})
public class SecurityConfiguration {

	private static final String[] PUBLIC_DOCUMENTATION_ENDPOINTS = {
			"/actuator/health",
			"/actuator/health/**",
			"/v3/api-docs/**",
			"/swagger-ui.html",
			"/swagger-ui/**"
	};

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ApiAuthenticationEntryPoint authenticationEntryPoint,
			ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
		return http
				.cors(Customizer.withDefaults())
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(PUBLIC_DOCUMENTATION_ENDPOINTS).permitAll()
						.requestMatchers(
								HttpMethod.POST,
								"/api/v1/auth/register",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh",
								"/api/v1/auth/logout")
						.permitAll()
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(Customizer.withDefaults())
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.httpBasic(httpBasic -> httpBasic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	SecretKey jwtSecretKey(AuthProperties properties) {
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(properties.jwtSecret());
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("JWT_SECRET must be valid Base64.", exception);
		}

		if (keyBytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes.");
		}
		return new SecretKeySpec(keyBytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
				.algorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AuthProperties properties) {
		var decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
		JwtClaimValidator<List<String>> audienceValidator = new JwtClaimValidator<>(
				"aud",
				audience -> audience != null && audience.contains(properties.audience()));
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(properties.issuer()),
				audienceValidator));
		return decoder;
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
		var configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.allowedOrigins());
		configuration.setAllowedMethods(java.util.List.of(
				"GET",
				"POST",
				"PUT",
				"PATCH",
				"DELETE",
				"OPTIONS"));
		configuration.setAllowedHeaders(java.util.List.of(
				"Authorization",
				"Content-Type",
				"Last-Event-ID",
				"X-Requested-With"));
		configuration.setExposedHeaders(java.util.List.of("X-Trace-Id"));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(3600L);

		var source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}
}
