package com.pulseops.common.exception;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	private final Clock clock;

	public ApiExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiErrorResponse> handleApiException(
			ApiException exception,
			HttpServletRequest request) {
		return response(
				exception.getStatus(),
				exception.getCode(),
				exception.getMessage(),
				Map.of(),
				request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiErrorResponse> handleValidation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		var fieldErrors = new LinkedHashMap<String, String>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
		}

		return response(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_ERROR",
				"One or more fields are invalid.",
				fieldErrors,
				request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiErrorResponse> handleUnreadableBody(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"The request body is malformed.",
				Map.of(),
				request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiErrorResponse> handleUnexpected(
			Exception exception,
			HttpServletRequest request) {
		LOGGER.error("Unhandled request failure.", exception);
		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"An unexpected error occurred.",
				Map.of(),
				request);
	}

	private ResponseEntity<ApiErrorResponse> response(
			HttpStatus status,
			String code,
			String message,
			Map<String, String> fieldErrors,
			HttpServletRequest request) {
		var traceId = UUID.randomUUID().toString();
		var body = new ApiErrorResponse(
				Instant.now(clock),
				status.value(),
				status.getReasonPhrase(),
				code,
				message,
				request.getRequestURI(),
				fieldErrors,
				traceId);

		return ResponseEntity.status(status)
				.header("X-Content-Type-Options", "nosniff")
				.header("X-Trace-Id", traceId)
				.body(body);
	}
}
