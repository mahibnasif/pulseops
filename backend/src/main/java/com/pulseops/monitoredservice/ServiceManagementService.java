package com.pulseops.monitoredservice;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.common.response.PageResponse;
import com.pulseops.healthcheck.HealthCheckExecutionService;
import com.pulseops.healthcheck.dto.HealthCheckResponse;
import com.pulseops.liveevent.LiveEventPublisher;
import com.pulseops.liveevent.LiveEventType;
import com.pulseops.monitoredservice.dto.CreateServiceRequest;
import com.pulseops.monitoredservice.dto.ServiceResponse;
import com.pulseops.monitoredservice.dto.UpdateServiceRequest;
import com.pulseops.organization.OrganizationAccessService;
import com.pulseops.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceManagementService {

	private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
			"name", "status", "createdAt", "updatedAt", "lastCheckedAt");

	private final MonitoredServiceRepository serviceRepository;
	private final OrganizationAccessService accessService;
	private final TargetUrlValidator targetUrlValidator;
	private final HealthCheckExecutionService healthCheckExecutionService;
	private final LiveEventPublisher liveEventPublisher;
	private final Clock clock;

	public ServiceManagementService(
			MonitoredServiceRepository serviceRepository,
			OrganizationAccessService accessService,
			TargetUrlValidator targetUrlValidator,
			HealthCheckExecutionService healthCheckExecutionService,
			LiveEventPublisher liveEventPublisher,
			Clock clock) {
		this.serviceRepository = serviceRepository;
		this.accessService = accessService;
		this.targetUrlValidator = targetUrlValidator;
		this.healthCheckExecutionService = healthCheckExecutionService;
		this.liveEventPublisher = liveEventPublisher;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<ServiceResponse> list(
			User user,
			UUID organizationId,
			ServiceStatus status,
			Boolean active,
			String search,
			int page,
			int size,
			String sort,
			String direction) {
		accessService.requireMember(organizationId, user.getId());
		var sortField = ALLOWED_SORT_FIELDS.contains(sort) ? sort : "name";
		var sortDirection = "desc".equalsIgnoreCase(direction)
				? Sort.Direction.DESC
				: Sort.Direction.ASC;
		var pageable = PageRequest.of(page, Math.min(size, 100), sortDirection, sortField);
		var normalizedSearch = search == null ? "" : search.trim();
		return PageResponse.from(serviceRepository
				.search(organizationId, status, active, normalizedSearch, pageable)
				.map(ServiceResponse::from));
	}

	@Transactional
	public ServiceResponse create(
			User user,
			UUID organizationId,
			CreateServiceRequest request) {
		accessService.requireAdmin(organizationId, user.getId());
		var configuration = fromCreateRequest(request);
		ensureUniqueName(organizationId, null, configuration.name());
		var service = MonitoredService.create(
				organizationId,
				user.getId(),
				configuration,
				Instant.now(clock));
		try {
			serviceRepository.saveAndFlush(service);
			liveEventPublisher.publish(
					organizationId, LiveEventType.SERVICE_CREATED, "service", service.getId());
			return ServiceResponse.from(service);
		}
		catch (DataIntegrityViolationException exception) {
			throw nameConflict();
		}
	}

	@Transactional(readOnly = true)
	public ServiceResponse get(User user, UUID organizationId, UUID serviceId) {
		accessService.requireMember(organizationId, user.getId());
		return ServiceResponse.from(requireService(organizationId, serviceId));
	}

	@Transactional
	public ServiceResponse update(
			User user,
			UUID organizationId,
			UUID serviceId,
			UpdateServiceRequest request) {
		accessService.requireAdmin(organizationId, user.getId());
		var service = requireService(organizationId, serviceId);
		var configuration = merge(service, request);
		ensureUniqueName(organizationId, serviceId, configuration.name());
		service.update(configuration, Instant.now(clock));
		try {
			serviceRepository.flush();
			liveEventPublisher.publish(
					organizationId, LiveEventType.SERVICE_UPDATED, "service", serviceId);
			return ServiceResponse.from(service);
		}
		catch (DataIntegrityViolationException exception) {
			throw nameConflict();
		}
	}

	@Transactional
	public void delete(User user, UUID organizationId, UUID serviceId) {
		accessService.requireAdmin(organizationId, user.getId());
		requireService(organizationId, serviceId).delete(Instant.now(clock));
		liveEventPublisher.publish(
				organizationId, LiveEventType.SERVICE_DELETED, "service", serviceId);
	}

	@Transactional
	public ServiceResponse pause(User user, UUID organizationId, UUID serviceId) {
		accessService.requireAdmin(organizationId, user.getId());
		var service = requireService(organizationId, serviceId);
		if (!service.isActive()) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"SERVICE_ALREADY_PAUSED",
					"The service is already paused.");
		}
		service.pause(Instant.now(clock));
		liveEventPublisher.publish(
				organizationId,
				LiveEventType.SERVICE_STATUS_CHANGED,
				"service",
				serviceId);
		return ServiceResponse.from(service);
	}

	@Transactional
	public ServiceResponse resume(User user, UUID organizationId, UUID serviceId) {
		accessService.requireAdmin(organizationId, user.getId());
		var service = requireService(organizationId, serviceId);
		if (service.isActive()) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"SERVICE_ALREADY_ACTIVE",
					"The service is already active.");
		}
		service.resume(Instant.now(clock));
		liveEventPublisher.publish(
				organizationId,
				LiveEventType.SERVICE_STATUS_CHANGED,
				"service",
				serviceId);
		return ServiceResponse.from(service);
	}

	public HealthCheckResponse manualCheck(
			User user,
			UUID organizationId,
			UUID serviceId) {
		accessService.requireEngineer(organizationId, user.getId());
		var service = requireService(organizationId, serviceId);
		return HealthCheckResponse.from(
				healthCheckExecutionService.executeManual(service.getId()));
	}

	private ServiceConfiguration fromCreateRequest(CreateServiceRequest request) {
		return validatedConfiguration(
				request.name(),
				request.description(),
				request.serviceType(),
				request.url(),
				valueOrDefault(request.httpMethod(), HttpMethod.GET),
				valueOrDefault(request.expectedStatusCode(), 200),
				request.expectedResponseText(),
				request.expectedJsonPath(),
				request.expectedJsonValue(),
				valueOrDefault(request.timeoutMilliseconds(), 5000),
				valueOrDefault(request.checkIntervalSeconds(), 60),
				valueOrDefault(request.failureThreshold(), 3),
				valueOrDefault(request.recoveryThreshold(), 2),
				valueOrDefault(request.degradedLatencyThresholdMilliseconds(), 1000));
	}

	private ServiceConfiguration merge(
			MonitoredService service,
			UpdateServiceRequest request) {
		if (allNull(request)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"EMPTY_UPDATE",
					"At least one service field must be supplied.");
		}
		return validatedConfiguration(
				valueOrDefault(request.name(), service.getName()),
				request.description() == null
						? service.getDescription()
						: request.description(),
				valueOrDefault(request.serviceType(), service.getServiceType()),
				valueOrDefault(request.url(), service.getUrl()),
				valueOrDefault(request.httpMethod(), service.getHttpMethod()),
				valueOrDefault(request.expectedStatusCode(), service.getExpectedStatusCode()),
				request.expectedResponseText() == null
						? service.getExpectedResponseText()
						: request.expectedResponseText(),
				request.expectedJsonPath() == null
						? service.getExpectedJsonPath()
						: request.expectedJsonPath(),
				request.expectedJsonValue() == null
						? service.getExpectedJsonValue()
						: request.expectedJsonValue(),
				valueOrDefault(request.timeoutMilliseconds(), service.getTimeoutMilliseconds()),
				valueOrDefault(request.checkIntervalSeconds(), service.getCheckIntervalSeconds()),
				valueOrDefault(request.failureThreshold(), service.getFailureThreshold()),
				valueOrDefault(request.recoveryThreshold(), service.getRecoveryThreshold()),
				valueOrDefault(
						request.degradedLatencyThresholdMilliseconds(),
						service.getDegradedLatencyThresholdMilliseconds()));
	}

	private ServiceConfiguration validatedConfiguration(
			String name,
			String description,
			ServiceType serviceType,
			String url,
			HttpMethod httpMethod,
			int expectedStatusCode,
			String expectedResponseText,
			String expectedJsonPath,
			String expectedJsonValue,
			int timeoutMilliseconds,
			int checkIntervalSeconds,
			int failureThreshold,
			int recoveryThreshold,
			int degradedLatencyThresholdMilliseconds) {
		var normalizedName = name.trim();
		if (normalizedName.isEmpty()) {
			throw validation("Service name cannot be blank.");
		}
		var normalizedUrl = targetUrlValidator
				.validateStructure(serviceType, url.trim())
				.toASCIIString();
		var jsonPath = trimToNull(expectedJsonPath);
		var jsonValue = trimToNull(expectedJsonValue);
		if ((jsonPath == null) != (jsonValue == null)) {
			throw validation("JSON path and expected JSON value must be supplied together.");
		}
		if (jsonPath != null
				&& !jsonPath.equals("$")
				&& !jsonPath.matches("^\\$\\.[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*$")) {
			throw validation("JSON path must use simple dot notation such as $.status.");
		}
		return new ServiceConfiguration(
				normalizedName,
				trimToNull(description),
				serviceType,
				normalizedUrl,
				httpMethod,
				expectedStatusCode,
				trimToNull(expectedResponseText),
				jsonPath,
				jsonValue,
				timeoutMilliseconds,
				checkIntervalSeconds,
				failureThreshold,
				recoveryThreshold,
				degradedLatencyThresholdMilliseconds);
	}

	private boolean allNull(UpdateServiceRequest request) {
		return request.name() == null
				&& request.description() == null
				&& request.serviceType() == null
				&& request.url() == null
				&& request.httpMethod() == null
				&& request.expectedStatusCode() == null
				&& request.expectedResponseText() == null
				&& request.expectedJsonPath() == null
				&& request.expectedJsonValue() == null
				&& request.timeoutMilliseconds() == null
				&& request.checkIntervalSeconds() == null
				&& request.failureThreshold() == null
				&& request.recoveryThreshold() == null
				&& request.degradedLatencyThresholdMilliseconds() == null;
	}

	private MonitoredService requireService(UUID organizationId, UUID serviceId) {
		return serviceRepository
				.findByIdAndOrganizationIdAndDeletedAtIsNull(serviceId, organizationId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"SERVICE_NOT_FOUND",
						"The monitored service could not be found."));
	}

	private void ensureUniqueName(UUID organizationId, UUID serviceId, String name) {
		var exists = serviceId == null
				? serviceRepository.existsByOrganizationIdAndNameIgnoreCaseAndDeletedAtIsNull(
						organizationId, name)
				: serviceRepository
						.existsByOrganizationIdAndNameIgnoreCaseAndIdNotAndDeletedAtIsNull(
								organizationId, name, serviceId);
		if (exists) {
			throw nameConflict();
		}
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		var trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private <T> T valueOrDefault(T value, T defaultValue) {
		return value == null ? defaultValue : value;
	}

	private int valueOrDefault(Integer value, int defaultValue) {
		return value == null ? defaultValue : value;
	}

	private ApiException validation(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
	}

	private ApiException nameConflict() {
		return new ApiException(
				HttpStatus.CONFLICT,
				"SERVICE_NAME_TAKEN",
				"An active service with this name already exists in the organization.");
	}
}
