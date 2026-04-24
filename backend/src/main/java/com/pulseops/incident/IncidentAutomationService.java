package com.pulseops.incident;

import java.time.Instant;

import com.pulseops.monitoredservice.MonitoredService;
import com.pulseops.monitoredservice.ServiceStatus;
import org.springframework.stereotype.Service;

@Service
public class IncidentAutomationService {

	private final IncidentRepository incidentRepository;
	private final IncidentTimelineEventRepository timelineRepository;

	public IncidentAutomationService(
			IncidentRepository incidentRepository,
			IncidentTimelineEventRepository timelineRepository) {
		this.incidentRepository = incidentRepository;
		this.timelineRepository = timelineRepository;
	}

	public void onStatusTransition(
			MonitoredService service,
			ServiceStatus before,
			boolean applied,
			Instant now) {
		if (!applied || before == service.getStatus()) {
			return;
		}
		if (service.getStatus() == ServiceStatus.DOWN) {
			openAutomaticIncident(service, now);
		}
		else if (before == ServiceStatus.DOWN
				&& (service.getStatus() == ServiceStatus.OPERATIONAL
						|| service.getStatus() == ServiceStatus.DEGRADED)) {
			recordRecovery(service, now);
		}
	}

	private void openAutomaticIncident(MonitoredService service, Instant now) {
		if (incidentRepository.findActiveAutomaticByServiceId(service.getId()).isPresent()) {
			return;
		}
		var incident = incidentRepository.saveAndFlush(Incident.create(
				service.getOrganizationId(),
				service.getId(),
				service.getName() + " is down",
				"PulseOps opened this incident after the configured failure threshold.",
				IncidentSeverity.HIGH,
				IncidentSource.AUTOMATIC_MONITORING,
				null,
				now));
		timelineRepository.save(IncidentTimelineEvent.create(
				incident.getId(),
				TimelineEventType.INCIDENT_CREATED,
				null,
				"Monitoring automatically opened the incident.",
				null,
				IncidentStatus.OPEN.name(),
				now));
	}

	private void recordRecovery(MonitoredService service, Instant now) {
		incidentRepository.findActiveAutomaticByServiceId(service.getId())
				.ifPresent(incident -> {
					var previous = incident.getStatus();
					if (previous != IncidentStatus.MONITORING) {
						incident.changeStatus(IncidentStatus.MONITORING, now);
						timelineRepository.save(IncidentTimelineEvent.create(
								incident.getId(),
								TimelineEventType.STATUS_CHANGED,
								null,
								"Incident moved to monitoring after service recovery.",
								previous.name(),
								IncidentStatus.MONITORING.name(),
								now));
					}
					timelineRepository.save(IncidentTimelineEvent.create(
							incident.getId(),
							TimelineEventType.SERVICE_RECOVERED,
							null,
							"The monitored service recovered.",
							ServiceStatus.DOWN.name(),
							service.getStatus().name(),
							now));
				});
	}
}
