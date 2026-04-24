package com.pulseops.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentTimelineEventRepository
		extends JpaRepository<IncidentTimelineEvent, UUID> {
	List<IncidentTimelineEvent> findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
