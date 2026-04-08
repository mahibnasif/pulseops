package com.pulseops.config;

import java.io.IOException;

import com.pulseops.common.exception.ApiException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

	private final HandlerExceptionResolver exceptionResolver;

	public ApiAccessDeniedHandler(
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
		this.exceptionResolver = exceptionResolver;
	}

	@Override
	public void handle(
			HttpServletRequest request,
			HttpServletResponse response,
			AccessDeniedException accessDeniedException)
			throws IOException, ServletException {
		exceptionResolver.resolveException(
				request,
				response,
				null,
				new ApiException(
						HttpStatus.FORBIDDEN,
						"ACCESS_DENIED",
						"You do not have permission to access this resource."));
	}
}
