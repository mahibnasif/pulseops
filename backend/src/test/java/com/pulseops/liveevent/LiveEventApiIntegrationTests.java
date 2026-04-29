package com.pulseops.liveevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;
import com.pulseops.monitoredservice.ManualCheckResult;
import com.pulseops.monitoredservice.ManualHealthCheckClient;
import com.pulseops.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@AutoConfigureMockMvc
class LiveEventApiIntegrationTests extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private LiveEventStreamService streamService;

	@MockitoBean
	private ManualHealthCheckClient healthCheckClient;

	@Test
	void streamRequiresAuthenticationAndActiveOrganizationMembership() throws Exception {
		var owner = register("owner@example.com");
		var outsider = register("outsider@example.com");
		var organizationId = createOrganization(owner, "Platform");

		mockMvc.perform(options(
						"/api/v1/organizations/{organizationId}/events",
						organizationId)
						.header(HttpHeaders.ORIGIN, "http://localhost:5173")
						.header(
								HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
								"GET")
						.header(
								HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
								"authorization,last-event-id"))
				.andExpect(status().isOk())
				.andExpect(header().string(
						HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
						org.hamcrest.Matchers.containsStringIgnoringCase("last-event-id")));

		mockMvc.perform(get(
						"/api/v1/organizations/{organizationId}/events",
						organizationId)
						.accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get(
						"/api/v1/organizations/{organizationId}/events",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken()))
						.accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
				.andExpect(status().isNotFound());
	}

	@Test
	void committedServiceChangesAreDeliveredOnlyToTheOrganizationStream()
			throws Exception {
		var owner = register("owner@example.com");
		var outsider = register("outsider@example.com");
		var organizationId = createOrganization(owner, "Platform");
		var otherOrganizationId = createOrganization(outsider, "External");

		var ownerStream = subscribe(owner, organizationId);
		var outsiderStream = subscribe(outsider, otherOrganizationId);
		try {
			var created = mockMvc.perform(post(
							"/api/v1/organizations/{organizationId}/services",
							organizationId)
							.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "name":"Checkout API",
									  "serviceType":"HTTPS",
									  "url":"https://example.com"
									}
									"""))
					.andExpect(status().isCreated())
					.andReturn();
			var serviceId = read(created, "$.id");

			assertThat(ownerStream.getResponse().getContentAsString())
					.contains("event:pulseops-ready")
					.contains("event:pulseops-update")
					.contains("SERVICE_CREATED")
					.contains(serviceId);
			assertThat(outsiderStream.getResponse().getContentAsString())
					.contains("event:pulseops-ready")
					.doesNotContain(serviceId);
		}
		finally {
			streamService.disconnectAll();
		}
	}

	@Test
	void healthAndIncidentEventsAreDeliveredAfterTheirCommandsCommit()
			throws Exception {
		var owner = register("owner@example.com");
		var organizationId = createOrganization(owner, "Platform");
		var serviceId = createService(owner, organizationId);
		var stream = subscribe(owner, organizationId);
		try {
			when(healthCheckClient.check(any())).thenReturn(new ManualCheckResult(
					Instant.parse("2026-07-30T12:00:00Z"),
					true,
					false,
					200,
					25,
					null,
					null,
					true,
					"healthy"));
			mockMvc.perform(post(
							"/api/v1/organizations/{organizationId}/services/{serviceId}/check",
							organizationId,
							serviceId)
							.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
					.andExpect(status().isOk());

			var created = mockMvc.perform(post(
							"/api/v1/organizations/{organizationId}/incidents",
							organizationId)
							.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{
									  "serviceId":"%s",
									  "title":"Elevated errors",
									  "severity":"HIGH"
									}
									""".formatted(serviceId)))
					.andExpect(status().isCreated())
					.andReturn();
			var incidentId = read(created, "$.id");

			mockMvc.perform(post(
							"/api/v1/organizations/{organizationId}/incidents/{incidentId}/comments",
							organizationId,
							incidentId)
							.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"content\":\"Investigating now.\"}"))
					.andExpect(status().isCreated());

			assertThat(stream.getResponse().getContentAsString())
					.contains("HEALTH_CHECK_RECORDED")
					.contains("SERVICE_STATUS_CHANGED")
					.contains("INCIDENT_CREATED")
					.contains("INCIDENT_COMMENT_ADDED")
					.contains(serviceId.toString())
					.contains(incidentId);
		}
		finally {
			streamService.disconnectAll();
		}
	}

	private MvcResult subscribe(RegisteredUser user, UUID organizationId)
			throws Exception {
		return mockMvc.perform(get(
						"/api/v1/organizations/{organizationId}/events",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
						.accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andExpect(header().string("X-Accel-Buffering", "no"))
				.andReturn();
	}

	private RegisteredUser register(String email) throws Exception {
		var result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName":"Live",
								  "lastName":"User",
								  "email":"%s",
								  "password":"Correct-Horse-42"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return new RegisteredUser(read(result, "$.accessToken"));
	}

	private UUID createOrganization(RegisteredUser owner, String name) throws Exception {
		var result = mockMvc.perform(post("/api/v1/organizations")
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"%s\"}".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.id"));
	}

	private UUID createService(RegisteredUser owner, UUID organizationId)
			throws Exception {
		var result = mockMvc.perform(post(
						"/api/v1/organizations/{organizationId}/services",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"Checkout API",
								  "serviceType":"HTTPS",
								  "url":"https://example.com"
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.id"));
	}

	private String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record RegisteredUser(String accessToken) {
	}

}
