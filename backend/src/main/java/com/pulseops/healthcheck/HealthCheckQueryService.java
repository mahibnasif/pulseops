package com.pulseops.healthcheck;

import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.common.response.PageResponse;
import com.pulseops.healthcheck.dto.HealthCheckResponse;
import com.pulseops.monitoredservice.MonitoredServiceRepository;
import com.pulseops.organization.OrganizationAccessService;
import com.pulseops.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthCheckQueryService {

	private final HealthCheckResultRepository resultRepository;
	private final MonitoredServiceRepository serviceRepository;
	private final OrganizationAccessService accessService;

	public HealthCheckQueryService(
			HealthCheckResultRepository resultRepository,
			MonitoredServiceRepository serviceRepository,
			OrganizationAccessService accessService) {
		this.resultRepository = resultRepository;
		this.serviceRepository = serviceRepository;
		this.accessService = accessService;
	}

	@Transactional(readOnly = true)
	public PageResponse<HealthCheckResponse> list(
			User user,
			UUID organizationId,
			UUID serviceId,
			CheckSource source,
			Boolean success,
			int page,
			int size) {
		accessService.requireMember(organizationId, user.getId());
		if (serviceRepository
				.findByIdAndOrganizationIdAndDeletedAtIsNull(serviceId, organizationId)
				.isEmpty()) {
			throw new ApiException(
					HttpStatus.NOT_FOUND,
					"SERVICE_NOT_FOUND",
					"The monitored service could not be found.");
		}
		var pageable = PageRequest.of(
				page,
				Math.min(size, 100),
				Sort.Direction.DESC,
				"checkedAt");
		return PageResponse.from(resultRepository
				.search(organizationId, serviceId, source, success, pageable)
				.map(HealthCheckResponse::from));
	}
}
