package com.pulseops.monitoredservice;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "monitored_services")
public class MonitoredService {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "service_type", nullable = false, length = 32)
	private ServiceType serviceType;

	@Column(nullable = false, length = 2048)
	private String url;

	@Enumerated(EnumType.STRING)
	@Column(name = "http_method", nullable = false, length = 16)
	private HttpMethod httpMethod;

	@Column(name = "expected_status_code", nullable = false)
	private int expectedStatusCode;

	@Column(name = "expected_response_text", length = 500)
	private String expectedResponseText;

	@Column(name = "expected_json_path", length = 500)
	private String expectedJsonPath;

	@Column(name = "expected_json_value", length = 500)
	private String expectedJsonValue;

	@Column(name = "timeout_milliseconds", nullable = false)
	private int timeoutMilliseconds;

	@Column(name = "check_interval_seconds", nullable = false)
	private int checkIntervalSeconds;

	@Column(name = "failure_threshold", nullable = false)
	private int failureThreshold;

	@Column(name = "recovery_threshold", nullable = false)
	private int recoveryThreshold;

	@Column(name = "degraded_latency_threshold_milliseconds", nullable = false)
	private int degradedLatencyThresholdMilliseconds;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ServiceStatus status;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_checked_at")
	private Instant lastCheckedAt;

	@Column(name = "last_successful_check_at")
	private Instant lastSuccessfulCheckAt;

	@Column(name = "last_failure_at")
	private Instant lastFailureAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected MonitoredService() {
	}

	private MonitoredService(
			UUID organizationId,
			UUID createdBy,
			ServiceConfiguration configuration,
			Instant now) {
		this.organizationId = organizationId;
		this.createdBy = createdBy;
		this.status = ServiceStatus.UNKNOWN;
		this.active = true;
		this.createdAt = now;
		apply(configuration, now);
	}

	public static MonitoredService create(
			UUID organizationId,
			UUID createdBy,
			ServiceConfiguration configuration,
			Instant now) {
		return new MonitoredService(organizationId, createdBy, configuration, now);
	}

	public void update(ServiceConfiguration configuration, Instant now) {
		apply(configuration, now);
	}

	public void pause(Instant now) {
		active = false;
		status = ServiceStatus.PAUSED;
		updatedAt = now;
	}

	public void resume(Instant now) {
		active = true;
		status = ServiceStatus.UNKNOWN;
		updatedAt = now;
	}

	public void recordManualCheck(boolean success, Instant checkedAt) {
		lastCheckedAt = checkedAt;
		if (success) {
			lastSuccessfulCheckAt = checkedAt;
		}
		else {
			lastFailureAt = checkedAt;
		}
		updatedAt = checkedAt;
	}

	public void delete(Instant now) {
		active = false;
		deletedAt = now;
		updatedAt = now;
	}

	private void apply(ServiceConfiguration configuration, Instant now) {
		name = configuration.name();
		description = configuration.description();
		serviceType = configuration.serviceType();
		url = configuration.url();
		httpMethod = configuration.httpMethod();
		expectedStatusCode = configuration.expectedStatusCode();
		expectedResponseText = configuration.expectedResponseText();
		expectedJsonPath = configuration.expectedJsonPath();
		expectedJsonValue = configuration.expectedJsonValue();
		timeoutMilliseconds = configuration.timeoutMilliseconds();
		checkIntervalSeconds = configuration.checkIntervalSeconds();
		failureThreshold = configuration.failureThreshold();
		recoveryThreshold = configuration.recoveryThreshold();
		degradedLatencyThresholdMilliseconds =
				configuration.degradedLatencyThresholdMilliseconds();
		updatedAt = now;
	}

	public UUID getId() { return id; }
	public UUID getOrganizationId() { return organizationId; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public ServiceType getServiceType() { return serviceType; }
	public String getUrl() { return url; }
	public HttpMethod getHttpMethod() { return httpMethod; }
	public int getExpectedStatusCode() { return expectedStatusCode; }
	public String getExpectedResponseText() { return expectedResponseText; }
	public String getExpectedJsonPath() { return expectedJsonPath; }
	public String getExpectedJsonValue() { return expectedJsonValue; }
	public int getTimeoutMilliseconds() { return timeoutMilliseconds; }
	public int getCheckIntervalSeconds() { return checkIntervalSeconds; }
	public int getFailureThreshold() { return failureThreshold; }
	public int getRecoveryThreshold() { return recoveryThreshold; }
	public int getDegradedLatencyThresholdMilliseconds() {
		return degradedLatencyThresholdMilliseconds;
	}
	public ServiceStatus getStatus() { return status; }
	public boolean isActive() { return active; }
	public UUID getCreatedBy() { return createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public Instant getLastCheckedAt() { return lastCheckedAt; }
	public Instant getLastSuccessfulCheckAt() { return lastSuccessfulCheckAt; }
	public Instant getLastFailureAt() { return lastFailureAt; }
}
