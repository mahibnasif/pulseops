package com.pulseops.support;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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

	@BeforeEach
	void resetApplicationData() {
		jdbcTemplate.execute("""
				TRUNCATE TABLE
				  incident_timeline_events,
				  incident_comments,
				  incidents,
				  health_check_results,
				  monitored_services,
				  organization_invitations,
				  organization_memberships,
				  organizations,
				  refresh_tokens,
				  users
				""");
	}
}
