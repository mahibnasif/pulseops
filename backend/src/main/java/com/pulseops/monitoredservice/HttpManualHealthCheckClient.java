package com.pulseops.monitoredservice;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import javax.net.ssl.SSLException;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Component;

@Component
public class HttpManualHealthCheckClient implements ManualHealthCheckClient {

	private static final int EXCERPT_CHARACTERS = 500;

	private final HttpClient httpClient;
	private final TargetUrlValidator targetUrlValidator;
	private final MonitoringProperties properties;
	private final Clock clock;
	private final JsonMapper jsonMapper;

	public HttpManualHealthCheckClient(
			HttpClient monitoringHttpClient,
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
		var uri = targetUrlValidator.validateForRequest(
				service.getServiceType(),
				service.getUrl());
		var requestBuilder = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofMillis(service.getTimeoutMilliseconds()))
				.header("User-Agent", "PulseOps/1.0");
		var request = service.getHttpMethod() == HttpMethod.HEAD
				? requestBuilder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build()
				: requestBuilder.GET().build();
		var started = System.nanoTime();

		try {
			var response = httpClient.send(
					request,
					HttpResponse.BodyHandlers.ofInputStream());
			var elapsed = elapsedMilliseconds(started);
			byte[] bytes;
			try (var input = response.body()) {
				bytes = input.readNBytes(properties.maxResponseBytes() + 1);
			}
			var body = new String(
					bytes,
					0,
					Math.min(bytes.length, properties.maxResponseBytes()),
					StandardCharsets.UTF_8);
			return evaluate(service, checkedAt, response.statusCode(), elapsed, body);
		}
		catch (HttpTimeoutException exception) {
			return failed(checkedAt, started, CheckErrorType.TIMEOUT, "The request timed out.");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return failed(
					checkedAt,
					started,
					CheckErrorType.NETWORK_ERROR,
					"The request was interrupted.");
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
		Throwable cause = exception;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		if (cause instanceof UnknownHostException) {
			return failed(
					checkedAt, started, CheckErrorType.DNS_FAILURE,
					"The target hostname could not be resolved.");
		}
		if (cause instanceof SSLException) {
			return failed(
					checkedAt, started, CheckErrorType.SSL_ERROR,
					"The TLS connection could not be established.");
		}
		if (cause instanceof ConnectException) {
			return failed(
					checkedAt, started, CheckErrorType.CONNECTION_REFUSED,
					"The target refused the connection.");
		}
		return failed(
				checkedAt, started, CheckErrorType.NETWORK_ERROR,
				"The network request could not be completed.");
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
