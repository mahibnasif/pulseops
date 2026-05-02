package com.pulseops.analytics;

import java.time.Duration;

enum AnalyticsBucket {
	HOUR("hour"),
	DAY("day");

	private final String sqlUnit;

	AnalyticsBucket(String sqlUnit) {
		this.sqlUnit = sqlUnit;
	}

	String sqlUnit() {
		return sqlUnit;
	}

	static AnalyticsBucket forRange(Duration range) {
		return range.compareTo(Duration.ofHours(48)) <= 0 ? HOUR : DAY;
	}
}
