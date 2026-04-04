package com.pulseops.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

	private static final String[] PUBLIC_FOUNDATION_ENDPOINTS = {
			"/actuator/health",
			"/actuator/health/**",
			"/v3/api-docs/**",
			"/swagger-ui.html",
			"/swagger-ui/**"
	};

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(PUBLIC_FOUNDATION_ENDPOINTS).permitAll()
						.anyRequest().denyAll())
				.httpBasic(httpBasic -> httpBasic.disable())
				.formLogin(form -> form.disable())
				.build();
	}

}
