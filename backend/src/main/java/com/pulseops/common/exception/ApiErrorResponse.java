package com.pulseops.common.exception;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String code,
		String message,
		String path,
		Map<String, String> fieldErrors,
		String traceId) {

	public ApiErrorResponse {
		fieldErrors = Map.copyOf(fieldErrors);
	}

	@Override
	public Map<String, String> fieldErrors() {
		return Map.copyOf(fieldErrors);
	}
}
