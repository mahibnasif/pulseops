package com.pulseops.healthcheck;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthCheckResultRepository
		extends JpaRepository<HealthCheckResult, UUID> {

	@Query("""
			SELECT result
			FROM HealthCheckResult result
			WHERE result.organizationId = :organizationId
			  AND result.serviceId = :serviceId
			  AND (:source IS NULL OR result.checkSource = :source)
			  AND (:success IS NULL OR result.success = :success)
			""")
	Page<HealthCheckResult> search(
			@Param("organizationId") UUID organizationId,
			@Param("serviceId") UUID serviceId,
			@Param("source") CheckSource source,
			@Param("success") Boolean success,
			Pageable pageable);
}
