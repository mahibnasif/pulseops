package com.pulseops.config;

import java.io.IOException;

import com.pulseops.common.exception.ApiException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final HandlerExceptionResolver exceptionResolver;

	public ApiAuthenticationEntryPoint(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	public void commence(
			HttpServletRequest request,
			HttpServletResponse response,
			AuthenticationException authenticationException)
			throws IOException, ServletException {
		exceptionResolver.resolveException(
				request,
				response,
				null,
				new ApiException(
						HttpStatus.UNAUTHORIZED,
						"AUTHENTICATION_REQUIRED",
						"Authentication is required to access this resource."));
	}
}
