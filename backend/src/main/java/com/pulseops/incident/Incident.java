package com.pulseops.incident;

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
@Table(name = "incidents")
public class Incident {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	@Column(name = "organization_id", nullable = false)
	private UUID organizationId;
	@Column(name = "service_id", nullable = false)
	private UUID serviceId;
	@Column(nullable = false, length = 200)
	private String title;
	@Column(length = 4000)
	private String description;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private IncidentSeverity severity;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private IncidentStatus status;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private IncidentSource source;
	@Column(name = "assigned_user_id")
	private UUID assignedUserId;
	@Column(name = "detected_at", nullable = false)
	private Instant detectedAt;
	@Column(name = "acknowledged_at")
	private Instant acknowledgedAt;
	@Column(name = "resolved_at")
	private Instant resolvedAt;
	@Column(name = "root_cause", length = 4000)
	private String rootCause;
	@Column(name = "resolution_summary", length = 4000)
	private String resolutionSummary;
	@Column(name = "created_by")
	private UUID createdBy;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Version
	@Column(nullable = false)
	private long version;

	protected Incident() {
	}

	public static Incident create(
			UUID organizationId,
			UUID serviceId,
			String title,
			String description,
			IncidentSeverity severity,
			IncidentSource source,
			UUID createdBy,
			Instant now) {
		var incident = new Incident();
		incident.organizationId = organizationId;
		incident.serviceId = serviceId;
		incident.title = title;
		incident.description = description;
		incident.severity = severity;
		incident.status = IncidentStatus.OPEN;
		incident.source = source;
		incident.createdBy = createdBy;
		incident.detectedAt = now;
		incident.createdAt = now;
		incident.updatedAt = now;
		return incident;
	}

	public void updateDetails(
			String title,
			String description,
			String rootCause,
			String resolutionSummary,
			Instant now) {
		this.title = title;
		this.description = description;
		this.rootCause = rootCause;
		this.resolutionSummary = resolutionSummary;
		this.updatedAt = now;
	}

	public void changeSeverity(IncidentSeverity severity, Instant now) {
		this.severity = severity;
		this.updatedAt = now;
	}

	public void changeStatus(IncidentStatus status, Instant now) {
		this.status = status;
		if (status == IncidentStatus.ACKNOWLEDGED && acknowledgedAt == null) {
			acknowledgedAt = now;
		}
		this.updatedAt = now;
	}

	public void assign(UUID userId, Instant now) {
		assignedUserId = userId;
		updatedAt = now;
	}

	public void resolve(String rootCause, String summary, Instant now) {
		this.rootCause = rootCause;
		this.resolutionSummary = summary;
		this.status = IncidentStatus.RESOLVED;
		this.resolvedAt = now;
		this.updatedAt = now;
	}

	public void reopen(Instant now) {
		status = IncidentStatus.OPEN;
		resolvedAt = null;
		resolutionSummary = null;
		updatedAt = now;
	}

	public UUID getId() { return id; }
	public UUID getOrganizationId() { return organizationId; }
	public UUID getServiceId() { return serviceId; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public IncidentSeverity getSeverity() { return severity; }
	public IncidentStatus getStatus() { return status; }
	public IncidentSource getSource() { return source; }
	public UUID getAssignedUserId() { return assignedUserId; }
	public Instant getDetectedAt() { return detectedAt; }
	public Instant getAcknowledgedAt() { return acknowledgedAt; }
	public Instant getResolvedAt() { return resolvedAt; }
	public String getRootCause() { return rootCause; }
	public String getResolutionSummary() { return resolutionSummary; }
	public UUID getCreatedBy() { return createdBy; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
