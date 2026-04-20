package com.pulseops.healthcheck;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.pulseops.monitoredservice.CheckErrorType;
import com.pulseops.monitoredservice.ManualCheckResult;
import com.pulseops.monitoredservice.MonitoredService;
import com.pulseops.monitoredservice.ServiceStatus;

@Entity
@Table(name = "health_check_results")
public class HealthCheckResult {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(name = "service_id", nullable = false)
	private UUID serviceId;

	@Column(name = "checked_at", nullable = false)
	private Instant checkedAt;

	@Column(nullable = false)
	private boolean success;

	@Column(nullable = false)
	private boolean degraded;

	@Column(name = "status_code")
	private Integer statusCode;

	@Column(name = "response_time_milliseconds", nullable = false)
	private long responseTimeMilliseconds;

	@Enumerated(EnumType.STRING)
	@Column(name = "error_type", length = 40)
	private CheckErrorType errorType;

	@Column(name = "error_message", length = 500)
	private String errorMessage;

	@Column(name = "response_validation_passed", nullable = false)
	private boolean responseValidationPassed;

	@Column(name = "response_excerpt", length = 500)
	private String responseExcerpt;

	@Enumerated(EnumType.STRING)
	@Column(name = "check_source", nullable = false, length = 20)
	private CheckSource checkSource;

	@Enumerated(EnumType.STRING)
	@Column(name = "status_before", nullable = false, length = 32)
	private ServiceStatus statusBefore;

	@Enumerated(EnumType.STRING)
	@Column(name = "status_after", nullable = false, length = 32)
	private ServiceStatus statusAfter;

	@Column(name = "applied_to_status", nullable = false)
	private boolean appliedToStatus;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected HealthCheckResult() {
	}

	public static HealthCheckResult create(
			MonitoredService service,
			ManualCheckResult result,
			CheckSource source,
			ServiceStatus statusBefore,
			boolean appliedToStatus,
			Instant createdAt) {
		var entity = new HealthCheckResult();
		entity.organizationId = service.getOrganizationId();
		entity.serviceId = service.getId();
		entity.checkedAt = result.checkedAt();
		entity.success = result.success();
		entity.degraded = result.degraded();
		entity.statusCode = result.statusCode();
		entity.responseTimeMilliseconds = result.responseTimeMilliseconds();
		entity.errorType = result.errorType();
		entity.errorMessage = result.errorMessage();
		entity.responseValidationPassed = result.responseValidationPassed();
		entity.responseExcerpt = result.responseExcerpt();
		entity.checkSource = source;
		entity.statusBefore = statusBefore;
		entity.statusAfter = service.getStatus();
		entity.appliedToStatus = appliedToStatus;
		entity.createdAt = createdAt;
		return entity;
	}

	public UUID getId() { return id; }
	public UUID getOrganizationId() { return organizationId; }
	public UUID getServiceId() { return serviceId; }
	public Instant getCheckedAt() { return checkedAt; }
	public boolean isSuccess() { return success; }
	public boolean isDegraded() { return degraded; }
	public Integer getStatusCode() { return statusCode; }
	public long getResponseTimeMilliseconds() { return responseTimeMilliseconds; }
	public CheckErrorType getErrorType() { return errorType; }
	public String getErrorMessage() { return errorMessage; }
	public boolean isResponseValidationPassed() { return responseValidationPassed; }
	public String getResponseExcerpt() { return responseExcerpt; }
	public CheckSource getCheckSource() { return checkSource; }
	public ServiceStatus getStatusBefore() { return statusBefore; }
	public ServiceStatus getStatusAfter() { return statusAfter; }
	public boolean isAppliedToStatus() { return appliedToStatus; }
	public Instant getCreatedAt() { return createdAt; }
}
