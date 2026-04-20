package com.pulseops.monitoredservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.pulseops.auth.RefreshTokenRepository;
import com.pulseops.healthcheck.HealthCheckClaimService;
import com.pulseops.healthcheck.HealthCheckExecutionService;
import com.pulseops.healthcheck.HealthCheckResultRepository;
import com.pulseops.invitation.OrganizationInvitationRepository;
import com.pulseops.membership.OrganizationMembershipRepository;
import com.pulseops.organization.OrganizationRepository;
import com.pulseops.support.AbstractIntegrationTest;
import com.pulseops.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class ServiceApiIntegrationTests extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MonitoredServiceRepository serviceRepository;

	@Autowired
	private HealthCheckResultRepository checkResultRepository;

	@Autowired
	private HealthCheckClaimService claimService;

	@Autowired
	private HealthCheckExecutionService executionService;

	@Autowired
	private OrganizationInvitationRepository invitationRepository;

	@Autowired
	private OrganizationMembershipRepository membershipRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@MockitoBean
	private ManualHealthCheckClient healthCheckClient;

	@BeforeEach
	void cleanDatabase() {
		checkResultRepository.deleteAll();
		serviceRepository.deleteAll();
		invitationRepository.deleteAll();
		membershipRepository.deleteAll();
		organizationRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void adminCreatesListsAndReadsAServiceWithSecureDefaults() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var organizationId = createOrganization(owner, "Platform");

		var created = createService(owner, organizationId, "Public API", "HTTPS",
				"https://example.com/health")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.expectedStatusCode").value(200))
				.andExpect(jsonPath("$.httpMethod").value("GET"))
				.andExpect(jsonPath("$.failureThreshold").value(3))
				.andExpect(jsonPath("$.recoveryThreshold").value(2))
				.andExpect(jsonPath("$.status").value("UNKNOWN"))
				.andExpect(jsonPath("$.active").value(true))
				.andReturn();
		var serviceId = UUID.fromString(read(created, "$.id"));

		mockMvc.perform(get("/api/v1/organizations/{org}/services", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.param("search", "public")
						.param("status", "UNKNOWN"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(1))
				.andExpect(jsonPath("$.content[0].id").value(serviceId.toString()))
				.andExpect(jsonPath("$.totalElements").value(1));

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/services/{service}",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Public API"));
	}

	@Test
	void validatesProtocolsUrlShapeJsonPairsAndDuplicateNames() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var organizationId = createOrganization(owner, "Platform");

		createService(owner, organizationId, "Gateway", "HTTP", "http://example.com")
				.andExpect(status().isCreated());
		createService(owner, organizationId, "gateway", "HTTP", "http://example.com")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SERVICE_NAME_TAKEN"));

		createService(owner, organizationId, "TCP target", "TCP", "tcp://example.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_SERVICE_TYPE"));

		createService(owner, organizationId, "Wrong scheme", "HTTPS", "http://example.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SERVICE_URL"));

		mockMvc.perform(post("/api/v1/organizations/{org}/services", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"JSON endpoint",
								  "serviceType":"HTTPS",
								  "url":"https://example.com/status",
								  "expectedJsonPath":"$.status"
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void enforcesTenantAndRoleBoundariesForCommandsAndManualChecks() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var engineer = register("engineer@example.com", "Engineer");
		var viewer = register("viewer@example.com", "Viewer");
		var outsider = register("outsider@example.com", "Outsider");
		var organizationId = createOrganization(owner, "Platform");
		accept(engineer, invite(owner, organizationId, engineer.email(), "ENGINEER"));
		accept(viewer, invite(owner, organizationId, viewer.email(), "VIEWER"));
		var created = createService(
				owner, organizationId, "API", "HTTPS", "https://example.com")
				.andExpect(status().isCreated())
				.andReturn();
		var serviceId = UUID.fromString(read(created, "$.id"));

		mockMvc.perform(patch(
						"/api/v1/organizations/{org}/services/{service}",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"Not allowed\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/services/{service}/check",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(viewer.accessToken())))
				.andExpect(status().isForbidden());

		when(healthCheckClient.check(any())).thenReturn(new ManualCheckResult(
				Instant.parse("2026-07-30T12:00:00Z"),
				true,
				false,
				200,
				42,
				null,
				null,
				true,
				"{\"status\":\"ok\"}"));
		mockMvc.perform(post(
						"/api/v1/organizations/{org}/services/{service}/check",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(engineer.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.responseTimeMilliseconds").value(42))
				.andExpect(jsonPath("$.checkSource").value("MANUAL"))
				.andExpect(jsonPath("$.statusBefore").value("UNKNOWN"))
				.andExpect(jsonPath("$.statusAfter").value("OPERATIONAL"))
				.andExpect(jsonPath("$.affectsServiceStatus").value(true));

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/services/{service}",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));

		assertThat(serviceRepository.findById(serviceId).orElseThrow().getLastCheckedAt())
				.isEqualTo(Instant.parse("2026-07-30T12:00:00Z"));
		assertThat(checkResultRepository.count()).isEqualTo(1);
	}

	@Test
	void claimsDueServicesPersistsScheduledHistoryAndRejectsAStaleWorker()
			throws Exception {
		var owner = register("owner@example.com", "Owner");
		var organizationId = createOrganization(owner, "Platform");
		var created = createService(
				owner, organizationId, "Scheduler API", "HTTPS", "https://example.com")
				.andExpect(status().isCreated())
				.andReturn();
		var serviceId = UUID.fromString(read(created, "$.id"));
		when(healthCheckClient.check(any())).thenReturn(new ManualCheckResult(
				Instant.parse("2026-07-30T12:30:00Z"),
				true,
				false,
				200,
				35,
				null,
				null,
				true,
				"healthy"));

		var claimed = claimService.claimDue("integration-worker");
		assertThat(claimed).contains(serviceId);
		assertThat(executionService.executeScheduled(serviceId, "integration-worker"))
				.isPresent();
		assertThat(executionService.executeScheduled(serviceId, "stale-worker"))
				.isEmpty();

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/services/{service}/checks",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].checkSource").value("SCHEDULED"))
				.andExpect(jsonPath("$.content[0].statusBefore").value("UNKNOWN"))
				.andExpect(jsonPath("$.content[0].statusAfter").value("OPERATIONAL"));

		var service = serviceRepository.findById(serviceId).orElseThrow();
		assertThat(service.getStatus()).isEqualTo(ServiceStatus.OPERATIONAL);
		assertThat(service.getConsecutiveSuccesses()).isEqualTo(1);
		assertThat(service.getNextCheckAt()).isAfter(Instant.now());
		assertThat(claimService.claimDue("another-worker")).doesNotContain(serviceId);
	}

	@Test
	void adminUpdatesPausesResumesAndSoftDeletesAService() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var organizationId = createOrganization(owner, "Platform");
		var created = createService(
				owner, organizationId, "API", "HTTPS", "https://example.com")
				.andExpect(status().isCreated())
				.andReturn();
		var serviceId = UUID.fromString(read(created, "$.id"));

		mockMvc.perform(patch(
						"/api/v1/organizations/{org}/services/{service}",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Customer API","failureThreshold":5}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Customer API"))
				.andExpect(jsonPath("$.failureThreshold").value(5));

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/services/{service}/pause",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PAUSED"))
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(post(
						"/api/v1/organizations/{org}/services/{service}/resume",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UNKNOWN"))
				.andExpect(jsonPath("$.active").value(true));

		mockMvc.perform(delete(
						"/api/v1/organizations/{org}/services/{service}",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isNoContent());
		mockMvc.perform(get(
						"/api/v1/organizations/{org}/services/{service}",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isNotFound());
		assertThat(serviceRepository.findById(serviceId)).isPresent();
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
						.content("""
								{"name":"%s"}
								""".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.id"));
	}

	private org.springframework.test.web.servlet.ResultActions createService(
			RegisteredUser user,
			UUID organizationId,
			String name,
			String type,
			String url) throws Exception {
		return mockMvc.perform(post("/api/v1/organizations/{org}/services", organizationId)
				.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "name":"%s",
						  "serviceType":"%s",
						  "url":"%s"
						}
						""".formatted(name, type, url)));
	}

	private UUID invite(
			RegisteredUser owner,
			UUID organizationId,
			String email,
			String role) throws Exception {
		var result = mockMvc.perform(post(
						"/api/v1/organizations/{id}/invitations",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","role":"%s"}
								""".formatted(email, role)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.invitation.id"));
	}

	private void accept(RegisteredUser user, UUID invitationId) throws Exception {
		mockMvc.perform(post("/api/v1/invitations/{id}/accept", invitationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
				.andExpect(status().isOk());
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
