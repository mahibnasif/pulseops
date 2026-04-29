package com.pulseops.healthcheck;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.incident.IncidentAutomationService;
import com.pulseops.liveevent.LiveEventPublisher;
import com.pulseops.liveevent.LiveEventType;
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
	private final LiveEventPublisher liveEventPublisher;
	private final Clock clock;

	public HealthCheckRecordService(
			MonitoredServiceRepository serviceRepository,
			HealthCheckResultRepository resultRepository,
			IncidentAutomationService incidentAutomationService,
			LiveEventPublisher liveEventPublisher,
			Clock clock) {
		this.serviceRepository = serviceRepository;
		this.resultRepository = resultRepository;
		this.incidentAutomationService = incidentAutomationService;
		this.liveEventPublisher = liveEventPublisher;
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
		var saved = resultRepository.save(HealthCheckResult.create(
				service, result, CheckSource.MANUAL, before, applied, now));
		publishEvents(service.getOrganizationId(), serviceId, saved.getId(),
				before, service.getStatus(), applied);
		return saved;
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
		var saved = resultRepository.save(HealthCheckResult.create(
				service, result, CheckSource.SCHEDULED, before, applied, now));
		publishEvents(service.getOrganizationId(), serviceId, saved.getId(),
				before, service.getStatus(), applied);
		return Optional.of(saved);
	}

	private void publishEvents(
			UUID organizationId,
			UUID serviceId,
			UUID resultId,
			com.pulseops.monitoredservice.ServiceStatus before,
			com.pulseops.monitoredservice.ServiceStatus after,
			boolean applied) {
		liveEventPublisher.publish(
				organizationId,
				LiveEventType.HEALTH_CHECK_RECORDED,
				"healthCheck",
				resultId);
		if (applied && before != after) {
			liveEventPublisher.publish(
					organizationId,
					LiveEventType.SERVICE_STATUS_CHANGED,
					"service",
					serviceId);
		}
	}

	private ApiException serviceNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"SERVICE_NOT_FOUND",
				"The monitored service could not be found.");
	}
}
