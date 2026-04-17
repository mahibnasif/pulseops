package com.pulseops.monitoredservice;

public interface ManualHealthCheckClient {

	ManualCheckResult check(MonitoredService service);
}
