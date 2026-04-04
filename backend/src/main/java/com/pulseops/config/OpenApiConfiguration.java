package com.pulseops.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(
		title = "PulseOps API",
		version = "v1",
		description = "Service monitoring and incident-management API.",
		contact = @Contact(name = "PulseOps"),
		license = @License(name = "MIT")))
public class OpenApiConfiguration {
}
