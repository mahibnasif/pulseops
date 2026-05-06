package com.pulseops.monitoredservice;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import javax.net.ssl.SSLException;

import tools.jackson.databind.json.JsonMapper;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.util.Timeout;
import org.springframework.stereotype.Component;

@Component
public class HttpManualHealthCheckClient implements ManualHealthCheckClient {

	private static final int EXCERPT_CHARACTERS = 500;

	private final CloseableHttpClient httpClient;
	private final TargetUrlValidator targetUrlValidator;
	private final MonitoringProperties properties;
	private final Clock clock;
	private final JsonMapper jsonMapper;

	public HttpManualHealthCheckClient(
			CloseableHttpClient monitoringHttpClient,
			TargetUrlValidator targetUrlValidator,
			MonitoringProperties properties,
			Clock clock) {
		this.httpClient = monitoringHttpClient;
		this.targetUrlValidator = targetUrlValidator;
		this.properties = properties;
		this.clock = clock;
		this.jsonMapper = JsonMapper.builder().build();
	}

	@Override
	public ManualCheckResult check(MonitoredService service) {
		var checkedAt = Instant.now(clock);
		var uri = targetUrlValidator.validateStructure(
				service.getServiceType(),
				service.getUrl());
		HttpUriRequestBase request = service.getHttpMethod() == HttpMethod.HEAD
				? new HttpHead(uri)
				: new HttpGet(uri);
		var timeout = Timeout.ofMilliseconds(service.getTimeoutMilliseconds());
		request.setConfig(RequestConfig.custom()
				.setConnectionRequestTimeout(timeout)
				.setConnectTimeout(timeout)
				.setResponseTimeout(timeout)
				.build());
		request.setHeader("User-Agent", "PulseOps/1.0");
		var started = System.nanoTime();

		try {
			return httpClient.execute(request, response -> {
				var elapsed = elapsedMilliseconds(started);
				var entity = response.getEntity();
				byte[] bytes = new byte[0];
				if (entity != null) {
					try (var input = entity.getContent()) {
						bytes = input.readNBytes(properties.maxResponseBytes() + 1);
					}
				}
				var body = new String(
						bytes,
						0,
						Math.min(bytes.length, properties.maxResponseBytes()),
						StandardCharsets.UTF_8);
				return evaluate(service, checkedAt, response.getCode(), elapsed, body);
			});
		}
		catch (SocketTimeoutException exception) {
			return failed(checkedAt, started, CheckErrorType.TIMEOUT, "The request timed out.");
		}
		catch (IOException exception) {
			return networkFailure(checkedAt, started, exception);
		}
	}

	private ManualCheckResult evaluate(
			MonitoredService service,
			Instant checkedAt,
			int statusCode,
			long elapsed,
			String body) {
		if (statusCode != service.getExpectedStatusCode()) {
			return completedFailure(
					checkedAt, statusCode, elapsed, CheckErrorType.UNEXPECTED_STATUS,
					"The response status did not match the configured expectation.", body);
		}
		if (service.getExpectedResponseText() != null
				&& !body.contains(service.getExpectedResponseText())) {
			return completedFailure(
					checkedAt, statusCode, elapsed, CheckErrorType.CONTENT_MISMATCH,
					"The expected response text was not found.", body);
		}
		if (service.getExpectedJsonPath() != null) {
			try {
				var root = jsonMapper.readTree(body);
				var pointer = toJsonPointer(service.getExpectedJsonPath());
				var node = root.at(pointer);
				if (node.isMissingNode()
						|| !node.asString().equals(service.getExpectedJsonValue())) {
					return completedFailure(
							checkedAt, statusCode, elapsed,
							CheckErrorType.JSON_VALIDATION_FAILURE,
							"The JSON response did not match the configured expectation.", body);
				}
			}
			catch (RuntimeException exception) {
				return completedFailure(
						checkedAt, statusCode, elapsed,
						CheckErrorType.JSON_VALIDATION_FAILURE,
						"The response was not valid JSON for the configured path.", body);
			}
		}
		return new ManualCheckResult(
				checkedAt,
				true,
				elapsed > service.getDegradedLatencyThresholdMilliseconds(),
				statusCode,
				elapsed,
				null,
				null,
				true,
				excerpt(body));
	}

	private String toJsonPointer(String jsonPath) {
		if ("$".equals(jsonPath)) {
			return "";
		}
		if (!jsonPath.startsWith("$.") || jsonPath.contains("[") || jsonPath.contains("]")) {
			throw new IllegalArgumentException("Unsupported JSON path.");
		}
		var parts = jsonPath.substring(2).split("\\.");
		var pointer = new StringBuilder();
		for (var part : parts) {
			if (part.isBlank()) {
				throw new IllegalArgumentException("Unsupported JSON path.");
			}
			pointer.append('/').append(part.replace("~", "~0").replace("/", "~1"));
		}
		return pointer.toString();
	}

	private ManualCheckResult completedFailure(
			Instant checkedAt,
			int statusCode,
			long elapsed,
			CheckErrorType errorType,
			String message,
			String body) {
		return new ManualCheckResult(
				checkedAt, false, false, statusCode, elapsed, errorType,
				message, false, excerpt(body));
	}

	private ManualCheckResult networkFailure(
			Instant checkedAt,
			long started,
			IOException exception) {
		var unknownHost = findCause(exception, UnknownHostException.class);
		if (unknownHost != null) {
			if ("TARGET_ADDRESS_BLOCKED".equals(unknownHost.getMessage())) {
				return failed(
						checkedAt, started, CheckErrorType.NETWORK_ERROR,
						"The target resolved to a prohibited network address.");
			}
			return failed(
					checkedAt, started, CheckErrorType.DNS_FAILURE,
					"The target hostname could not be resolved.");
		}
		if (findCause(exception, SSLException.class) != null) {
			return failed(
					checkedAt, started, CheckErrorType.SSL_ERROR,
					"The TLS connection could not be established.");
		}
		if (findCause(exception, ConnectException.class) != null) {
			return failed(
					checkedAt, started, CheckErrorType.CONNECTION_REFUSED,
					"The target refused the connection.");
		}
		return failed(
				checkedAt, started, CheckErrorType.NETWORK_ERROR,
				"The network request could not be completed.");
	}

	private <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
		Throwable cause = exception;
		while (cause != null) {
			if (type.isInstance(cause)) {
				return type.cast(cause);
			}
			cause = cause.getCause();
		}
		return null;
	}

	private ManualCheckResult failed(
			Instant checkedAt,
			long started,
			CheckErrorType type,
			String message) {
		return new ManualCheckResult(
				checkedAt, false, false, null, elapsedMilliseconds(started),
				type, message, false, null);
	}

	private long elapsedMilliseconds(long started) {
		return Math.max(0, (System.nanoTime() - started) / 1_000_000);
	}

	private String excerpt(String value) {
		return value.substring(0, Math.min(value.length(), EXCERPT_CHARACTERS));
	}
}
