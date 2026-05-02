package com.pulseops.analytics;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pulseops.analytics.dto.AnalyticsResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

	private final JdbcClient jdbcClient;

	public AnalyticsService(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Transactional(readOnly = true)
	public AnalyticsResponse snapshot(
			UUID organizationId,
			Instant from,
			Instant to,
			AnalyticsBucket bucket) {
		return new AnalyticsResponse(
				new AnalyticsResponse.Range(
						from,
						to,
						bucket.name(),
						"UTC",
						"successful health-check samples / total health-check samples"),
				serviceSummary(organizationId),
				reliabilitySummary(organizationId, from, to),
				incidentSummary(organizationId, from, to),
				healthTrend(organizationId, from, to, bucket),
				incidentTrend(organizationId, from, to, bucket),
				incidentsBySeverity(organizationId, from, to),
				serviceReliability(organizationId, from, to));
	}

	private AnalyticsResponse.ServiceSummary serviceSummary(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT
				  COUNT(*) AS total,
				  COUNT(*) FILTER (WHERE status = 'OPERATIONAL') AS operational,
				  COUNT(*) FILTER (WHERE status = 'DEGRADED') AS degraded,
				  COUNT(*) FILTER (WHERE status = 'DOWN') AS down,
				  COUNT(*) FILTER (WHERE status = 'PAUSED') AS paused,
				  COUNT(*) FILTER (WHERE status = 'UNKNOWN') AS unknown
				FROM monitored_services
				WHERE organization_id = :organizationId
				  AND deleted_at IS NULL
				""")
				.param("organizationId", organizationId)
				.query((resultSet, rowNumber) -> new AnalyticsResponse.ServiceSummary(
						resultSet.getLong("total"),
						resultSet.getLong("operational"),
						resultSet.getLong("degraded"),
						resultSet.getLong("down"),
						resultSet.getLong("paused"),
						resultSet.getLong("unknown")))
				.single();
	}

	private AnalyticsResponse.ReliabilitySummary reliabilitySummary(
			UUID organizationId,
			Instant from,
			Instant to) {
		return jdbcClient.sql("""
				SELECT
				  COUNT(*) AS total_checks,
				  COUNT(*) FILTER (WHERE success) AS successful_checks,
				  COUNT(*) FILTER (WHERE NOT success) AS failed_checks,
				  ROUND(100.0 * COUNT(*) FILTER (WHERE success)
				      / NULLIF(COUNT(*), 0), 2) AS uptime_percentage,
				  ROUND(AVG(response_time_milliseconds), 2)
				      AS average_response_time,
				  ROUND(PERCENTILE_CONT(0.50) WITHIN GROUP
				      (ORDER BY response_time_milliseconds)::numeric, 2)
				      AS p50_response_time,
				  ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP
				      (ORDER BY response_time_milliseconds)::numeric, 2)
				      AS p95_response_time
				FROM health_check_results
				WHERE organization_id = :organizationId
				  AND checked_at >= :from
				  AND checked_at < :to
				""")
				.param("organizationId", organizationId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((resultSet, rowNumber) ->
						new AnalyticsResponse.ReliabilitySummary(
								resultSet.getLong("total_checks"),
								resultSet.getLong("successful_checks"),
								resultSet.getLong("failed_checks"),
								nullableDouble(resultSet, "uptime_percentage"),
								nullableDouble(resultSet, "average_response_time"),
								nullableDouble(resultSet, "p50_response_time"),
								nullableDouble(resultSet, "p95_response_time")))
				.single();
	}

	private AnalyticsResponse.IncidentSummary incidentSummary(
			UUID organizationId,
			Instant from,
			Instant to) {
		return jdbcClient.sql("""
				SELECT
				  COUNT(*) AS total,
				  COUNT(*) FILTER (WHERE status <> 'RESOLVED') AS active,
				  COUNT(*) FILTER (WHERE severity = 'CRITICAL') AS critical,
				  ROUND(AVG(EXTRACT(EPOCH FROM
				      (acknowledged_at - detected_at)) / 60.0)
				      FILTER (WHERE acknowledged_at IS NOT NULL), 2) AS mtta,
				  ROUND(AVG(EXTRACT(EPOCH FROM
				      (resolved_at - detected_at)) / 60.0)
				      FILTER (WHERE resolved_at IS NOT NULL), 2) AS mttr,
				  ROUND(MAX(EXTRACT(EPOCH FROM
				      (COALESCE(resolved_at, :to) - detected_at)) / 60.0), 2)
				      AS longest_incident
				FROM incidents
				WHERE organization_id = :organizationId
				  AND detected_at >= :from
				  AND detected_at < :to
				""")
				.param("organizationId", organizationId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((resultSet, rowNumber) ->
						new AnalyticsResponse.IncidentSummary(
								resultSet.getLong("total"),
								resultSet.getLong("active"),
								resultSet.getLong("critical"),
								nullableDouble(resultSet, "mtta"),
								nullableDouble(resultSet, "mttr"),
								nullableDouble(resultSet, "longest_incident")))
				.single();
	}

	private List<AnalyticsResponse.HealthPoint> healthTrend(
			UUID organizationId,
			Instant from,
			Instant to,
			AnalyticsBucket bucket) {
		var sql = """
				SELECT
				  DATE_TRUNC('__BUCKET__', checked_at) AS bucket_start,
				  COUNT(*) AS total_checks,
				  COUNT(*) FILTER (WHERE success) AS successful_checks,
				  COUNT(*) FILTER (WHERE NOT success) AS failed_checks,
				  ROUND(100.0 * COUNT(*) FILTER (WHERE success)
				      / NULLIF(COUNT(*), 0), 2) AS uptime_percentage,
				  ROUND(AVG(response_time_milliseconds), 2)
				      AS average_response_time
				FROM health_check_results
				WHERE organization_id = :organizationId
				  AND checked_at >= :from
				  AND checked_at < :to
				GROUP BY bucket_start
				ORDER BY bucket_start
				""".replace("__BUCKET__", bucket.sqlUnit());
		return jdbcClient.sql(sql)
				.param("organizationId", organizationId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((resultSet, rowNumber) -> new AnalyticsResponse.HealthPoint(
						resultSet.getTimestamp("bucket_start").toInstant(),
						resultSet.getLong("total_checks"),
						resultSet.getLong("successful_checks"),
						resultSet.getLong("failed_checks"),
						nullableDouble(resultSet, "uptime_percentage"),
						nullableDouble(resultSet, "average_response_time")))
				.list();
	}

	private List<AnalyticsResponse.IncidentPoint> incidentTrend(
			UUID organizationId,
			Instant from,
			Instant to,
			AnalyticsBucket bucket) {
		var sql = """
				SELECT
				  DATE_TRUNC('__BUCKET__', detected_at) AS bucket_start,
				  COUNT(*) AS total_incidents,
				  COUNT(*) FILTER (WHERE resolved_at IS NOT NULL)
				      AS resolved_incidents
				FROM incidents
				WHERE organization_id = :organizationId
				  AND detected_at >= :from
				  AND detected_at < :to
				GROUP BY bucket_start
				ORDER BY bucket_start
				""".replace("__BUCKET__", bucket.sqlUnit());
		return jdbcClient.sql(sql)
				.param("organizationId", organizationId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((resultSet, rowNumber) -> new AnalyticsResponse.IncidentPoint(
						resultSet.getTimestamp("bucket_start").toInstant(),
						resultSet.getLong("total_incidents"),
						resultSet.getLong("resolved_incidents")))
				.list();
	}

	private List<AnalyticsResponse.SeverityCount> incidentsBySeverity(
			UUID organizationId,
			Instant from,
			Instant to) {
		return jdbcClient.sql("""
				SELECT severity, COUNT(*) AS incident_count
				FROM incidents
				WHERE organization_id = :organizationId
				  AND detected_at >= :from
				  AND detected_at < :to
				GROUP BY severity
				ORDER BY CASE severity
				  WHEN 'CRITICAL' THEN 1
				  WHEN 'HIGH' THEN 2
				  WHEN 'MEDIUM' THEN 3
				  ELSE 4
				END
				""")
				.param("organizationId", organizationId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((resultSet, rowNumber) -> new AnalyticsResponse.SeverityCount(
						resultSet.getString("severity"),
						resultSet.getLong("incident_count")))
				.list();
	}

	private List<AnalyticsResponse.ServiceReliability> serviceReliability(
			UUID organizationId,
			Instant from,
			Instant to) {
		return jdbcClient.sql("""
				WITH check_metrics AS (
				  SELECT
				    service_id,
				    COUNT(*) AS total_checks,
				    COUNT(*) FILTER (WHERE NOT success) AS failed_checks,
				    ROUND(100.0 * COUNT(*) FILTER (WHERE success)
				        / NULLIF(COUNT(*), 0), 2) AS uptime_percentage,
				    ROUND(AVG(response_time_milliseconds), 2)
				        AS average_response_time,
				    ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP
				        (ORDER BY response_time_milliseconds)::numeric, 2)
				        AS p95_response_time
				  FROM health_check_results
				  WHERE organization_id = :organizationId
				    AND checked_at >= :from
				    AND checked_at < :to
				  GROUP BY service_id
				),
				incident_metrics AS (
				  SELECT service_id, COUNT(*) AS incident_count
				  FROM incidents
				  WHERE organization_id = :organizationId
				    AND detected_at >= :from
				    AND detected_at < :to
				  GROUP BY service_id
				)
				SELECT
				  service.id AS service_id,
				  service.name AS service_name,
				  service.status,
				  COALESCE(checks.total_checks, 0) AS total_checks,
				  COALESCE(checks.failed_checks, 0) AS failed_checks,
				  checks.uptime_percentage,
				  checks.average_response_time,
				  checks.p95_response_time,
				  COALESCE(incidents.incident_count, 0) AS incident_count
				FROM monitored_services service
				LEFT JOIN check_metrics checks ON checks.service_id = service.id
				LEFT JOIN incident_metrics incidents
				  ON incidents.service_id = service.id
				WHERE service.organization_id = :organizationId
				  AND service.deleted_at IS NULL
				ORDER BY checks.uptime_percentage ASC NULLS LAST,
				         service.name ASC
				""")
				.param("organizationId", organizationId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((resultSet, rowNumber) ->
						new AnalyticsResponse.ServiceReliability(
								resultSet.getObject("service_id", UUID.class),
								resultSet.getString("service_name"),
								resultSet.getString("status"),
								resultSet.getLong("total_checks"),
								resultSet.getLong("failed_checks"),
								nullableDouble(resultSet, "uptime_percentage"),
								nullableDouble(resultSet, "average_response_time"),
								nullableDouble(resultSet, "p95_response_time"),
								resultSet.getLong("incident_count")))
				.list();
	}

	private static Double nullableDouble(ResultSet resultSet, String column)
			throws SQLException {
		var value = resultSet.getDouble(column);
		return resultSet.wasNull() ? null : value;
	}
}
