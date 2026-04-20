package com.pulseops.config;

import java.net.http.HttpClient;
import java.time.Duration;

import com.pulseops.monitoredservice.MonitoringProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MonitoringProperties.class)
@EnableScheduling
public class MonitoringConfiguration {

	@Bean
	HttpClient monitoringHttpClient() {
		return HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}
}
