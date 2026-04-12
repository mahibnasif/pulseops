package com.pulseops;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pulseops.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PulseopsBackendApplicationTests extends AbstractIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void contextLoadsWithPostgreSqlAndFlyway() {
		var appliedMigrations = jdbcTemplate.queryForObject(
				"select count(*) from flyway_schema_history where success",
				Integer.class);

		assertEquals(3, appliedMigrations);
	}

}
