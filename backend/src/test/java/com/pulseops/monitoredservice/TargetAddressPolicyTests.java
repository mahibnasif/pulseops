package com.pulseops.monitoredservice;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TargetAddressPolicyTests {

	private final TargetAddressPolicy policy = new TargetAddressPolicy(properties(false));

	@ParameterizedTest
	@ValueSource(strings = {
		"127.0.0.1",
		"10.0.0.1",
		"100.64.0.1",
		"169.254.169.254",
		"172.16.0.1",
		"192.0.0.1",
		"192.0.2.1",
		"192.88.99.1",
		"192.168.1.1",
		"198.18.0.1",
		"198.51.100.1",
		"203.0.113.1",
		"224.0.0.1",
		"240.0.0.1",
		"::1",
		"fc00::1",
		"fe80::1",
		"ff02::1",
		"2001:db8::1",
		"2002::1",
		"64:ff9b::c000:201",
		"64:ff9b:1::c000:201"
	})
	void blocksNonPublicAndSpecialPurposeAddresses(String value) throws Exception {
		var address = InetAddress.getByName(value);

		assertThatThrownBy(() -> policy.requireAllowed(new InetAddress[] {address}))
				.hasMessageContaining("prohibited network address")
				.extracting("code")
				.isEqualTo("TARGET_ADDRESS_BLOCKED");
	}

	@ParameterizedTest
	@ValueSource(strings = {"8.8.8.8", "1.1.1.1", "2606:4700:4700::1111"})
	void allowsPublicAddresses(String value) throws Exception {
		var address = InetAddress.getByName(value);

		assertThatCode(() -> policy.requireAllowed(new InetAddress[] {address}))
				.doesNotThrowAnyException();
	}

	@Test
	void rejectsAnEntireDnsAnswerWhenAnyAddressIsBlocked() throws Exception {
		var publicAddress = InetAddress.getByName("8.8.8.8");
		var privateAddress = InetAddress.getByName("127.0.0.1");

		assertThatThrownBy(
				() -> policy.requireAllowed(new InetAddress[] {publicAddress, privateAddress}))
				.extracting("code")
				.isEqualTo("TARGET_ADDRESS_BLOCKED");
	}

	@Test
	void rejectsAnEmptyDnsAnswer() {
		assertThatThrownBy(() -> policy.requireAllowed(new InetAddress[0]))
				.extracting("code")
				.isEqualTo("TARGET_DNS_FAILURE");
	}

	@Test
	void permitsPrivateAddressesOnlyWhenExplicitlyEnabled() throws Exception {
		var developmentPolicy = new TargetAddressPolicy(properties(true));
		var loopback = InetAddress.getByName("127.0.0.1");

		assertThatCode(
				() -> developmentPolicy.requireAllowed(new InetAddress[] {loopback}))
				.doesNotThrowAnyException();
	}

	private static MonitoringProperties properties(boolean allowPrivateTargets) {
		return new MonitoringProperties(
				allowPrivateTargets,
				65_536,
				false,
				10,
				120);
	}
}
