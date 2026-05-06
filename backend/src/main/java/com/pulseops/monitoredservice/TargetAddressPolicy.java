package com.pulseops.monitoredservice;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

import com.pulseops.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class TargetAddressPolicy {

	private final MonitoringProperties properties;

	public TargetAddressPolicy(MonitoringProperties properties) {
		this.properties = properties;
	}

	public void requireAllowed(InetAddress[] addresses) {
		if (addresses.length == 0) {
			throw dnsFailure();
		}
		if (properties.allowPrivateTargets()) {
			return;
		}
		for (var address : addresses) {
			if (isBlocked(address)) {
				throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"TARGET_ADDRESS_BLOCKED",
						"The monitoring target resolves to a prohibited network address.");
			}
		}
	}

	boolean isBlocked(InetAddress address) {
		if (address.isAnyLocalAddress()
				|| address.isLoopbackAddress()
				|| address.isLinkLocalAddress()
				|| address.isSiteLocalAddress()
				|| address.isMulticastAddress()) {
			return true;
		}
		var bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			return isBlockedIpv4(bytes, 0);
		}
		if (address instanceof Inet6Address) {
			if (isIpv4Mapped(bytes)) {
				return isBlockedIpv4(bytes, 12);
			}
			return isBlockedIpv6(bytes);
		}
		return true;
	}

	private boolean isBlockedIpv4(byte[] bytes, int offset) {
		var first = Byte.toUnsignedInt(bytes[offset]);
		var second = Byte.toUnsignedInt(bytes[offset + 1]);
		var third = Byte.toUnsignedInt(bytes[offset + 2]);
		return first == 0
				|| first == 10
				|| first == 127
				|| first >= 224
				|| (first == 100 && second >= 64 && second <= 127)
				|| (first == 169 && second == 254)
				|| (first == 172 && second >= 16 && second <= 31)
				|| (first == 192 && second == 0 && third == 0)
				|| (first == 192 && second == 0 && third == 2)
				|| (first == 192 && second == 88 && third == 99)
				|| (first == 192 && second == 168)
				|| (first == 198 && (second == 18 || second == 19))
				|| (first == 198 && second == 51 && third == 100)
				|| (first == 203 && second == 0 && third == 113);
	}

	private boolean isBlockedIpv6(byte[] bytes) {
		var first = Byte.toUnsignedInt(bytes[0]);
		var second = Byte.toUnsignedInt(bytes[1]);
		if ((first & 0xfe) == 0xfc) {
			return true;
		}
		if (first == 0x20
				&& second == 0x01
				&& (Byte.toUnsignedInt(bytes[2]) & 0xfe) == 0) {
			return true;
		}
		if (isPrefix(bytes, new int[] {0x20, 0x01, 0x0d, 0xb8})) {
			return true;
		}
		if (first == 0x20 && second == 0x02) {
			return true;
		}
		return isPrefix(
						bytes,
						new int[] {
							0x00, 0x64, 0xff, 0x9b, 0x00, 0x00,
							0x00, 0x00, 0x00, 0x00, 0x00, 0x00
						})
				|| isPrefix(
						bytes,
						new int[] {0x00, 0x64, 0xff, 0x9b, 0x00, 0x01});
	}

	private boolean isIpv4Mapped(byte[] bytes) {
		for (var index = 0; index < 10; index++) {
			if (bytes[index] != 0) {
				return false;
			}
		}
		return Byte.toUnsignedInt(bytes[10]) == 0xff
				&& Byte.toUnsignedInt(bytes[11]) == 0xff;
	}

	private boolean isPrefix(byte[] bytes, int[] prefix) {
		for (var index = 0; index < prefix.length; index++) {
			if (Byte.toUnsignedInt(bytes[index]) != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	ApiException dnsFailure() {
		return new ApiException(
				HttpStatus.BAD_REQUEST,
				"TARGET_DNS_FAILURE",
				"The monitoring target could not be resolved.");
	}
}
