package com.pulseops.monitoredservice;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import com.pulseops.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TargetUrlValidator {

	public URI validateStructure(ServiceType serviceType, String value) {
		if (serviceType != ServiceType.HTTP && serviceType != ServiceType.HTTPS) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"UNSUPPORTED_SERVICE_TYPE",
					"Phase 4 supports HTTP and HTTPS services.");
		}

		final URI uri;
		try {
			uri = new URI(value);
		}
		catch (URISyntaxException exception) {
			throw invalidUrl();
		}
		var scheme = uri.getScheme() == null
				? ""
				: uri.getScheme().toLowerCase(Locale.ROOT);
		var expectedScheme = serviceType == ServiceType.HTTPS ? "https" : "http";
		if (!scheme.equals(expectedScheme)
				|| uri.getHost() == null
				|| uri.getUserInfo() != null
				|| uri.getFragment() != null) {
			throw invalidUrl();
		}
		return uri;
	}

	private ApiException invalidUrl() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVALID_SERVICE_URL",
				"The URL must be an absolute HTTP or HTTPS URL without credentials or a fragment.");
	}
}
