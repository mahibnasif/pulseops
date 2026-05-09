package com.pulseops.monitoredservice;

import java.net.InetAddress;
import java.net.UnknownHostException;

import com.pulseops.common.exception.ApiException;
import org.apache.hc.client5.http.DnsResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ValidatingDnsResolver implements DnsResolver {

	private final TargetAddressPolicy addressPolicy;
	private final AddressLookup addressLookup;

	@Autowired
	public ValidatingDnsResolver(TargetAddressPolicy addressPolicy) {
		this(addressPolicy, InetAddress::getAllByName);
	}

	ValidatingDnsResolver(
			TargetAddressPolicy addressPolicy,
			AddressLookup addressLookup) {
		this.addressPolicy = addressPolicy;
		this.addressLookup = addressLookup;
	}

	@Override
	public InetAddress[] resolve(String host) throws UnknownHostException {
		try {
			var addresses = addressLookup.resolve(host);
			addressPolicy.requireAllowed(addresses);
			return addresses.clone();
		}
		catch (ApiException exception) {
			var resolutionFailure = new UnknownHostException(exception.getCode());
			resolutionFailure.initCause(exception);
			throw resolutionFailure;
		}
	}

	@Override
	public String resolveCanonicalHostname(String host) {
		return host;
	}

	@FunctionalInterface
	interface AddressLookup {
		InetAddress[] resolve(String host) throws UnknownHostException;
	}
}
