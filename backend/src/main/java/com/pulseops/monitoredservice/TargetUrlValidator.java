package com.pulseops.monitoredservice;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

import com.pulseops.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TargetUrlValidator {

	private final MonitoringProperties properties;

	public TargetUrlValidator(MonitoringProperties properties) {
		this.properties = properties;
	}

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

	public URI validateForRequest(ServiceType serviceType, String value) {
		var uri = validateStructure(serviceType, value);
		if (properties.allowPrivateTargets()) {
			return uri;
		}
		try {
			for (var address : InetAddress.getAllByName(uri.getHost())) {
				if (isBlocked(address)) {
					throw new ApiException(
							HttpStatus.BAD_REQUEST,
							"TARGET_ADDRESS_BLOCKED",
							"The monitoring target resolves to a prohibited network address.");
				}
			}
		}
		catch (UnknownHostException exception) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"TARGET_DNS_FAILURE",
					"The monitoring target could not be resolved.");
		}
		return uri;
	}

	private boolean isBlocked(InetAddress address) {
		if (address.isAnyLocalAddress()
				|| address.isLoopbackAddress()
				|| address.isLinkLocalAddress()
				|| address.isSiteLocalAddress()
				|| address.isMulticastAddress()) {
			return true;
		}
		var bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			var first = Byte.toUnsignedInt(bytes[0]);
			var second = Byte.toUnsignedInt(bytes[1]);
			var third = Byte.toUnsignedInt(bytes[2]);
			return first == 0
					|| first >= 224
					|| (first == 100 && second >= 64 && second <= 127)
					|| (first == 192 && second == 0 && third == 0)
					|| (first == 192 && second == 0 && third == 2)
					|| (first == 198 && (second == 18 || second == 19))
					|| (first == 198 && second == 51 && third == 100)
					|| (first == 203 && second == 0 && third == 113);
		}
		if (address instanceof Inet6Address) {
			var first = Byte.toUnsignedInt(bytes[0]);
			return (first & 0xfe) == 0xfc;
		}
		return true;
	}

	private ApiException invalidUrl() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVALID_SERVICE_URL",
				"The URL must be an absolute HTTP or HTTPS URL without credentials or a fragment.");
	}
}
