package com.pulseops.liveevent;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class LiveEventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;
	private final Clock clock;

	public LiveEventPublisher(
			ApplicationEventPublisher applicationEventPublisher,
			Clock clock) {
		this.applicationEventPublisher = applicationEventPublisher;
		this.clock = clock;
	}

	public void publish(
			UUID organizationId,
			LiveEventType type,
			String entityType,
			UUID entityId) {
		applicationEventPublisher.publishEvent(new OrganizationLiveEvent(
				UUID.randomUUID(),
				organizationId,
				type,
				entityType,
				entityId,
				Instant.now(clock)));
	}
}
