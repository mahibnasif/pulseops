package com.pulseops.monitoredservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TargetUrlValidatorTests {

	private final TargetUrlValidator validator = new TargetUrlValidator();

	@Test
	void acceptsAWellFormedUrlWithoutResolvingIt() {
		var uri = validator.validateStructure(
				ServiceType.HTTPS,
				"https://status.example.com:8443/health?full=true");

		assertThat(uri.getHost()).isEqualTo("status.example.com");
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"http://user:password@example.com",
		"http://example.com/#fragment",
		"file:///etc/passwd",
		"//example.com/health",
		"http://"
	})
	void rejectsUnsafeOrMalformedUrls(String value) {
		assertThatThrownBy(() -> validator.validateStructure(ServiceType.HTTP, value))
				.extracting("code")
				.isEqualTo("INVALID_SERVICE_URL");
	}

	@Test
	void rejectsAProtocolThatDoesNotMatchTheServiceType() {
		assertThatThrownBy(
				() -> validator.validateStructure(ServiceType.HTTPS, "http://example.com"))
				.extracting("code")
				.isEqualTo("INVALID_SERVICE_URL");
	}
}
