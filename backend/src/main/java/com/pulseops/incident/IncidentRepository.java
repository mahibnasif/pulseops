package com.pulseops.incident;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Incident> findLockedByIdAndOrganizationId(UUID id, UUID organizationId);

	Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

	@Query("""
			SELECT incident
			FROM Incident incident
			WHERE incident.serviceId = :serviceId
			  AND incident.source = com.pulseops.incident.IncidentSource.AUTOMATIC_MONITORING
			  AND incident.status <> com.pulseops.incident.IncidentStatus.RESOLVED
			""")
	Optional<Incident> findActiveAutomaticByServiceId(
			@Param("serviceId") UUID serviceId);

	@Query("""
			SELECT incident
			FROM Incident incident
			WHERE incident.organizationId = :organizationId
			  AND (:status IS NULL OR incident.status = :status)
			  AND (:severity IS NULL OR incident.severity = :severity)
			  AND (:serviceId IS NULL OR incident.serviceId = :serviceId)
			  AND (:assigneeId IS NULL OR incident.assignedUserId = :assigneeId)
			  AND LOWER(incident.title) LIKE LOWER(CONCAT('%', :search, '%'))
			""")
	Page<Incident> search(
			@Param("organizationId") UUID organizationId,
			@Param("status") IncidentStatus status,
			@Param("severity") IncidentSeverity severity,
			@Param("serviceId") UUID serviceId,
			@Param("assigneeId") UUID assigneeId,
			@Param("search") String search,
			Pageable pageable);
}
