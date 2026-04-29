package com.pulseops.liveevent;

import java.time.Instant;
import java.util.UUID;

public record OrganizationLiveEvent(
		UUID id,
		UUID organizationId,
		LiveEventType type,
		String entityType,
		UUID entityId,
		Instant occurredAt) {
}
