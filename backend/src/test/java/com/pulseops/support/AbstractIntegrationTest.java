package com.pulseops.support;

import java.util.Base64;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer postgres;

	static {
		postgres = new PostgreSQLContainer("postgres:17-alpine");
		postgres.start();
	}

	@DynamicPropertySource
	static void authenticationProperties(DynamicPropertyRegistry registry) {
		var testKey = new byte[32];
		java.util.Arrays.fill(testKey, (byte) 7);
		registry.add(
				"pulseops.auth.jwt-secret",
				() -> Base64.getEncoder().encodeToString(testKey));
		registry.add("pulseops.auth.refresh-cookie-secure", () -> "false");
		registry.add("pulseops.monitoring.scheduler-enabled", () -> "false");
	}
}
