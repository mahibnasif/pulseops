package com.pulseops.monitoredservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;

class ValidatingDnsResolverTests {

	private final TargetAddressPolicy policy = new TargetAddressPolicy(
			new MonitoringProperties(false, 65_536, false, 10, 120));

	@Test
	void returnsTheSameValidatedAddressesUsedByTheTransport() throws Exception {
		var resolved = new InetAddress[] {InetAddress.getByName("8.8.8.8")};
		var resolver = new ValidatingDnsResolver(policy, ignored -> resolved);

		var result = resolver.resolve("status.example.com");

		assertThat(result).containsExactly(resolved);
		assertThat(result).isNotSameAs(resolved);
	}

	@Test
	void preventsTheTransportFromConnectingToAReboundPrivateAddress() throws Exception {
		var resolver = new ValidatingDnsResolver(
				policy,
				ignored -> new InetAddress[] {InetAddress.getByName("169.254.169.254")});

		assertThatThrownBy(() -> resolver.resolve("status.example.com"))
				.isInstanceOf(java.net.UnknownHostException.class)
				.hasMessage("TARGET_ADDRESS_BLOCKED");
	}
}
