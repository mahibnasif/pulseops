package com.pulseops.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.pulseops.monitoredservice.CheckErrorType;
import com.pulseops.monitoredservice.ManualCheckResult;
import com.pulseops.monitoredservice.ManualHealthCheckClient;
import com.pulseops.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class IncidentApiIntegrationTests extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private IncidentRepository incidentRepository;

	@MockitoBean
	private ManualHealthCheckClient healthCheckClient;

	@Test
	void monitoringCreatesOneAutomaticIncidentAndRecordsRecovery() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var organizationId = createOrganization(owner, "Platform");
		var serviceId = createService(owner, organizationId, 1, 1);
		when(healthCheckClient.check(any())).thenReturn(failure());

		check(owner, organizationId, serviceId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusAfter").value("DOWN"));
		check(owner, organizationId, serviceId).andExpect(status().isOk());

		var listed = mockMvc.perform(get(
						"/api/v1/organizations/{org}/incidents",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.param("status", "OPEN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].source")
						.value("AUTOMATIC_MONITORING"))
				.andExpect(jsonPath("$.content[0].severity").value("HIGH"))
				.andReturn();
		var incidentId = UUID.fromString(read(listed, "$.content[0].id"));
		assertThat(incidentRepository.count()).isEqualTo(1);

		when(healthCheckClient.check(any())).thenReturn(success());
		check(owner, organizationId, serviceId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.statusAfter").value("OPERATIONAL"));

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/incidents/{incident}",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.incident.status").value("MONITORING"))
				.andExpect(jsonPath("$.timeline[?(@.eventType == 'SERVICE_RECOVERED')]")
						.isNotEmpty());
	}

	@Test
	void engineerManagesAssignmentCommentsResolutionAndReopening() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var engineer = register("engineer@example.com", "Engineer");
		var viewer = register("viewer@example.com", "Viewer");
		var organizationId = createOrganization(owner, "Platform");
		accept(engineer, invite(owner, organizationId, engineer.email(), "ENGINEER"));
		accept(viewer, invite(owner, organizationId, viewer.email(), "VIEWER"));
		var serviceId = createService(owner, organizationId, 3, 2);

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(viewer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceId":"%s",
								  "title":"Viewer cannot declare incidents",
								  "severity":"LOW"
								}
								""".formatted(serviceId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_ORGANIZATION_ROLE"));

		var created = mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceId":"%s",
								  "title":"Elevated checkout errors",
								  "description":"Customers are seeing intermittent failures.",
								  "severity":"MEDIUM"
								}
								""".formatted(serviceId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.source").value("MANUAL"))
				.andReturn();
		var incidentId = UUID.fromString(read(created, "$.id"));

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents/{incident}/assign",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"userId\":\"%s\"}".formatted(engineer.userId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.assignedUserId")
						.value(engineer.userId().toString()));

		mockMvc.perform(patch(
						"/api/v1/organizations/{org}/incidents/{incident}",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"severity":"HIGH","status":"ACKNOWLEDGED"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.severity").value("HIGH"))
				.andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents/{incident}/comments",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Rollback is in progress.\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents/{incident}/resolve",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "rootCause":"A faulty deployment.",
								  "resolutionSummary":"Rolled back the release."
								}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESOLVED"));

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/incidents/{incident}",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(viewer.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comments[0].content")
						.value("Rollback is in progress."))
				.andExpect(jsonPath("$.timeline.length()").value(6));

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents/{incident}/comments",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(viewer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"content\":\"Not allowed\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents/{incident}/reopen",
						organizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void incidentLookupAndServiceSelectionAreTenantSafe() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var outsider = register("outsider@example.com", "Outsider");
		var organizationId = createOrganization(owner, "Platform");
		var otherOrganizationId = createOrganization(outsider, "External");
		var serviceId = createService(owner, organizationId, 3, 2);
		var otherServiceId = createService(outsider, otherOrganizationId, 3, 2);
		var created = mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"serviceId":"%s","title":"Tenant scoped","severity":"HIGH"}
								""".formatted(serviceId)))
				.andExpect(status().isCreated())
				.andReturn();
		var incidentId = UUID.fromString(read(created, "$.id"));

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"serviceId":"%s","title":"Cross tenant","severity":"HIGH"}
								""".formatted(otherServiceId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_INCIDENT_SERVICE"));

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/incidents",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/incidents/{incident}",
						otherOrganizationId,
						incidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INCIDENT_NOT_FOUND"));
	}

	private org.springframework.test.web.servlet.ResultActions check(
			RegisteredUser user, UUID organizationId, UUID serviceId) throws Exception {
		return mockMvc.perform(post(
						"/api/v1/organizations/{org}/services/{service}/check",
						organizationId,
						serviceId)
				.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())));
	}

	private UUID createService(
			RegisteredUser user,
			UUID organizationId,
			int failureThreshold,
			int recoveryThreshold) throws Exception {
		var result = mockMvc.perform(post(
						"/api/v1/organizations/{org}/services",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"Checkout API",
								  "serviceType":"HTTPS",
								  "url":"https://example.com",
								  "failureThreshold":%d,
								  "recoveryThreshold":%d
								}
								""".formatted(failureThreshold, recoveryThreshold)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.id"));
	}

	private RegisteredUser register(String email, String firstName) throws Exception {
		var result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName":"%s",
								  "lastName":"User",
								  "email":"%s",
								  "password":"Correct-Horse-42"
								}
								""".formatted(firstName, email)))
				.andExpect(status().isCreated())
				.andReturn();
		return new RegisteredUser(
				UUID.fromString(read(result, "$.user.id")),
				email,
				read(result, "$.accessToken"));
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

	private UUID invite(
			RegisteredUser owner, UUID organizationId, String email, String role)
			throws Exception {
		var result = mockMvc.perform(post(
						"/api/v1/organizations/{id}/invitations",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"%s\",\"role\":\"%s\"}".formatted(email, role)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.invitation.id"));
	}

	private void accept(RegisteredUser user, UUID invitationId) throws Exception {
		mockMvc.perform(post("/api/v1/invitations/{id}/accept", invitationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
				.andExpect(status().isOk());
	}

	private ManualCheckResult failure() {
		return new ManualCheckResult(
				Instant.parse("2026-07-30T12:00:00Z"), false, false, 503, 25,
				CheckErrorType.UNEXPECTED_STATUS, "Unexpected status.", false, "down");
	}

	private ManualCheckResult success() {
		return new ManualCheckResult(
				Instant.parse("2026-07-30T12:01:00Z"), true, false, 200, 20,
				null, null, true, "healthy");
	}

	private String read(MvcResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	private record RegisteredUser(UUID userId, String email, String accessToken) {
	}
}
