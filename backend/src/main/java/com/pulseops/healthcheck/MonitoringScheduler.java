package com.pulseops.healthcheck;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "pulseops.monitoring",
		name = "scheduler-enabled",
		havingValue = "true")
public class MonitoringScheduler {

	private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

	private final HealthCheckClaimService claimService;
	private final HealthCheckExecutionService executionService;
	private final String workerId = "worker-" + UUID.randomUUID();

	public MonitoringScheduler(
			HealthCheckClaimService claimService,
			HealthCheckExecutionService executionService) {
		this.claimService = claimService;
		this.executionService = executionService;
	}

	@Scheduled(
			fixedDelayString = "${pulseops.monitoring.poll-interval-milliseconds:5000}",
			initialDelayString = "${pulseops.monitoring.poll-interval-milliseconds:5000}")
	public void checkDueServices() {
		for (var serviceId : claimService.claimDue(workerId)) {
			try {
				executionService.executeScheduled(serviceId, workerId);
			}
			catch (RuntimeException exception) {
				log.error("Scheduled health check processing failed for service {}", serviceId);
			}
		}
	}
}
