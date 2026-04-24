package com.pulseops.healthcheck;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.incident.IncidentAutomationService;
import com.pulseops.monitoredservice.ManualCheckResult;
import com.pulseops.monitoredservice.MonitoredServiceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthCheckRecordService {

	private final MonitoredServiceRepository serviceRepository;
	private final HealthCheckResultRepository resultRepository;
	private final IncidentAutomationService incidentAutomationService;
	private final Clock clock;

	public HealthCheckRecordService(
			MonitoredServiceRepository serviceRepository,
			HealthCheckResultRepository resultRepository,
			IncidentAutomationService incidentAutomationService,
			Clock clock) {
		this.serviceRepository = serviceRepository;
		this.resultRepository = resultRepository;
		this.incidentAutomationService = incidentAutomationService;
		this.clock = clock;
	}

	@Transactional
	public HealthCheckResult recordManual(UUID serviceId, ManualCheckResult result) {
		var service = serviceRepository.findByIdForCheck(serviceId)
				.orElseThrow(this::serviceNotFound);
		var before = service.getStatus();
		var now = Instant.now(clock);
		var applied = service.applyCompletedCheck(result, now, false);
		incidentAutomationService.onStatusTransition(service, before, applied, now);
		return resultRepository.save(HealthCheckResult.create(
				service, result, CheckSource.MANUAL, before, applied, now));
	}

	@Transactional
	public Optional<HealthCheckResult> recordScheduled(
			UUID serviceId,
			String claimOwner,
			ManualCheckResult result) {
		var service = serviceRepository.findByIdForCheck(serviceId).orElse(null);
		if (service == null || !service.isClaimedBy(claimOwner)) {
			return Optional.empty();
		}
		var before = service.getStatus();
		var now = Instant.now(clock);
		var applied = service.applyCompletedCheck(result, now, true);
		incidentAutomationService.onStatusTransition(service, before, applied, now);
		return Optional.of(resultRepository.save(HealthCheckResult.create(
				service, result, CheckSource.SCHEDULED, before, applied, now)));
	}

	private ApiException serviceNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"SERVICE_NOT_FOUND",
				"The monitored service could not be found.");
	}
}
