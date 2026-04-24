package com.pulseops.incident;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentCommentRepository
		extends JpaRepository<IncidentComment, UUID> {
	List<IncidentComment> findAllByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
