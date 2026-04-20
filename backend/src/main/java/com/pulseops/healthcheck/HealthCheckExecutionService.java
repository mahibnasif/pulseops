package com.pulseops.healthcheck;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.monitoredservice.CheckErrorType;
import com.pulseops.monitoredservice.ManualCheckResult;
import com.pulseops.monitoredservice.ManualHealthCheckClient;
import com.pulseops.monitoredservice.MonitoredService;
import com.pulseops.monitoredservice.MonitoredServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class HealthCheckExecutionService {

	private final MonitoredServiceRepository serviceRepository;
	private final ManualHealthCheckClient healthCheckClient;
	private final HealthCheckRecordService recordService;
	private final Clock clock;

	public HealthCheckExecutionService(
			MonitoredServiceRepository serviceRepository,
			ManualHealthCheckClient healthCheckClient,
			HealthCheckRecordService recordService,
			Clock clock) {
		this.serviceRepository = serviceRepository;
		this.healthCheckClient = healthCheckClient;
		this.recordService = recordService;
		this.clock = clock;
	}

	public HealthCheckResult executeManual(UUID serviceId) {
		var service = requireActive(serviceId);
		return recordService.recordManual(serviceId, safelyCheck(service));
	}

	public Optional<HealthCheckResult> executeScheduled(
			UUID serviceId,
			String claimOwner) {
		var service = serviceRepository.findById(serviceId).orElse(null);
		if (service == null) {
			return Optional.empty();
		}
		return recordService.recordScheduled(
				serviceId,
				claimOwner,
				safelyCheck(service));
	}

	private MonitoredService requireActive(UUID serviceId) {
		var service = serviceRepository.findById(serviceId)
				.orElseThrow(this::serviceNotFound);
		if (!service.isActive()) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"SERVICE_MONITORING_PAUSED",
					"Monitoring is paused for this service.");
		}
		return service;
	}

	private ManualCheckResult safelyCheck(MonitoredService service) {
		try {
			return healthCheckClient.check(service);
		}
		catch (RuntimeException exception) {
			return new ManualCheckResult(
					Instant.now(clock),
					false,
					false,
					null,
					0,
					CheckErrorType.UNKNOWN_ERROR,
					"The health check could not be completed safely.",
					false,
					null);
		}
	}

	private ApiException serviceNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"SERVICE_NOT_FOUND",
				"The monitored service could not be found.");
	}
}
