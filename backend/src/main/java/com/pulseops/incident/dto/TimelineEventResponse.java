package com.pulseops.incident.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.incident.IncidentTimelineEvent;
import com.pulseops.incident.TimelineEventType;

public record TimelineEventResponse(
		UUID id,
		TimelineEventType eventType,
		UUID actorUserId,
		String message,
		String oldValue,
		String newValue,
		Instant createdAt) {
	public static TimelineEventResponse from(IncidentTimelineEvent event) {
		return new TimelineEventResponse(
				event.getId(), event.getEventType(), event.getActorUserId(),
				event.getMessage(), event.getOldValue(), event.getNewValue(),
				event.getCreatedAt());
	}
}
