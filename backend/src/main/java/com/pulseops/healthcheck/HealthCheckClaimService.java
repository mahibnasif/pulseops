package com.pulseops.healthcheck;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulseops.monitoredservice.MonitoringProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthCheckClaimService {

	private static final String CLAIM_SQL = """
			WITH candidates AS (
			    SELECT id
			    FROM monitored_services
			    WHERE is_active = TRUE
			      AND deleted_at IS NULL
			      AND next_check_at <= ?
			      AND (check_claimed_until IS NULL OR check_claimed_until < ?)
			    ORDER BY next_check_at
			    FOR UPDATE SKIP LOCKED
			    LIMIT ?
			)
			UPDATE monitored_services service
			SET check_claimed_by = ?,
			    check_claimed_until = ?
			FROM candidates
			WHERE service.id = candidates.id
			RETURNING service.id
			""";

	private final JdbcTemplate jdbcTemplate;
	private final MonitoringProperties properties;
	private final Clock clock;

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring owns the injected singleton JDBC template for this service.")
	public HealthCheckClaimService(
			JdbcTemplate jdbcTemplate,
			MonitoringProperties properties,
			Clock clock) {
		this.jdbcTemplate = jdbcTemplate;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public List<UUID> claimDue(String owner) {
		var now = Instant.now(clock);
		var leaseUntil = now.plusSeconds(properties.claimLeaseSeconds());
		return jdbcTemplate.query(
				CLAIM_SQL,
				(resultSet, rowNumber) ->
						resultSet.getObject("id", UUID.class),
				Timestamp.from(now),
				Timestamp.from(now),
				properties.batchSize(),
				owner,
				Timestamp.from(leaseUntil));
	}
}
