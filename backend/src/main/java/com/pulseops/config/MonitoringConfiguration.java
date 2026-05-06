package com.pulseops.config;

import com.pulseops.monitoredservice.MonitoringProperties;
import com.pulseops.monitoredservice.ValidatingDnsResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MonitoringProperties.class)
@EnableScheduling
public class MonitoringConfiguration {

	@Bean
	CloseableHttpClient monitoringHttpClient(
			ValidatingDnsResolver dnsResolver) {
		var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
				.setDnsResolver(dnsResolver)
				.setDefaultConnectionConfig(ConnectionConfig.custom()
						.setConnectTimeout(Timeout.ofSeconds(10))
						.setSocketTimeout(Timeout.ofSeconds(60))
						.build())
				.setMaxConnTotal(50)
				.setMaxConnPerRoute(5)
				.build();
		return HttpClients.custom()
				.setConnectionManager(connectionManager)
				.disableRedirectHandling()
				.disableCookieManagement()
				.disableAutomaticRetries()
				.disableContentCompression()
				.build();
	}
}
