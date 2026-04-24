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

@Entity
@Table(name = "incident_timeline_events")
public class IncidentTimelineEvent {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	@Column(name = "incident_id", nullable = false)
	private UUID incidentId;
	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 40)
	private TimelineEventType eventType;
	@Column(name = "actor_user_id")
	private UUID actorUserId;
	@Column(nullable = false, length = 1000)
	private String message;
	@Column(name = "old_value", length = 500)
	private String oldValue;
	@Column(name = "new_value", length = 500)
	private String newValue;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected IncidentTimelineEvent() {
	}

	public static IncidentTimelineEvent create(
			UUID incidentId,
			TimelineEventType type,
			UUID actorUserId,
			String message,
			String oldValue,
			String newValue,
			Instant now) {
		var event = new IncidentTimelineEvent();
		event.incidentId = incidentId;
		event.eventType = type;
		event.actorUserId = actorUserId;
		event.message = message;
		event.oldValue = oldValue;
		event.newValue = newValue;
		event.createdAt = now;
		return event;
	}

	public UUID getId() { return id; }
	public UUID getIncidentId() { return incidentId; }
	public TimelineEventType getEventType() { return eventType; }
	public UUID getActorUserId() { return actorUserId; }
	public String getMessage() { return message; }
	public String getOldValue() { return oldValue; }
	public String getNewValue() { return newValue; }
	public Instant getCreatedAt() { return createdAt; }
}
