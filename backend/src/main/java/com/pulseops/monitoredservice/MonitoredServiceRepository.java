package com.pulseops.monitoredservice;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitoredServiceRepository
		extends JpaRepository<MonitoredService, UUID> {

	Optional<MonitoredService> findByIdAndOrganizationIdAndDeletedAtIsNull(
			UUID id,
			UUID organizationId);

	boolean existsByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(
			UUID organizationId,
			String name);

	boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(
			UUID organizationId,
			String name,
			UUID id);

	@Query("""
			SELECT service
			FROM MonitoredService service
			WHERE service.organizationId = :organizationId
			  AND service.deletedAt IS NULL
			  AND (:status IS NULL OR service.status = :status)
			  AND (:active IS NULL OR service.active = :active)
			  AND (:search IS NULL
			       OR LOWER(service.name) LIKE LOWER(CONCAT('%', :search, '%')))
			""")
	Page<MonitoredService> search(
			@Param("organizationId") UUID organizationId,
			@Param("status") ServiceStatus status,
			@Param("active") Boolean active,
			@Param("search") String search,
			Pageable pageable);
}
