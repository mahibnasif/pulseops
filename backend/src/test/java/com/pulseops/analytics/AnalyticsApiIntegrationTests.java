package com.pulseops.analytics;

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
class AnalyticsApiIntegrationTests extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ManualHealthCheckClient healthCheckClient;

	@Test
	void aggregatesReliabilityIncidentAndServiceMetrics() throws Exception {
		var owner = register("analytics-owner@example.com");
		var organizationId = createOrganization(owner, "Analytics Team");
		var serviceId = createService(owner, organizationId);
		when(healthCheckClient.check(any()))
				.thenReturn(success("2026-07-30T10:00:00Z", 100))
				.thenReturn(success("2026-07-30T11:00:00Z", 300))
				.thenReturn(failure("2026-07-30T12:00:00Z", 500));

		check(owner, organizationId, serviceId);
		check(owner, organizationId, serviceId);
		check(owner, organizationId, serviceId);
		var manualIncidentId = createIncident(
				owner, organizationId, serviceId, "CRITICAL");
		mockMvc.perform(patch(
						"/api/v1/organizations/{org}/incidents/{incident}",
						organizationId,
						manualIncidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"ACKNOWLEDGED\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents/{incident}/resolve",
						organizationId,
						manualIncidentId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "rootCause":"Test fixture",
								  "resolutionSummary":"Analytics verified"
								}
								"""))
				.andExpect(status().isOk());

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/analytics",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.param("from", "2026-07-01T00:00:00Z")
						.param("to", "2026-08-01T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.range.bucket").value("DAY"))
				.andExpect(jsonPath("$.range.timezone").value("UTC"))
				.andExpect(jsonPath("$.services.total").value(1))
				.andExpect(jsonPath("$.services.down").value(1))
				.andExpect(jsonPath("$.reliability.totalChecks").value(3))
				.andExpect(jsonPath("$.reliability.successfulChecks").value(2))
				.andExpect(jsonPath("$.reliability.failedChecks").value(1))
				.andExpect(jsonPath("$.reliability.uptimePercentage").value(66.67))
				.andExpect(jsonPath("$.reliability.averageResponseTimeMilliseconds")
						.value(300.0))
				.andExpect(jsonPath("$.reliability.p50ResponseTimeMilliseconds")
						.value(300.0))
				.andExpect(jsonPath("$.reliability.p95ResponseTimeMilliseconds")
						.value(480.0))
				.andExpect(jsonPath("$.incidents.total").value(2))
				.andExpect(jsonPath("$.incidents.active").value(1))
				.andExpect(jsonPath("$.incidents.critical").value(1))
				.andExpect(jsonPath("$.healthTrend[0].totalChecks").value(3))
				.andExpect(jsonPath("$.incidentsBySeverity.length()").value(2))
				.andExpect(jsonPath(
						"$.incidentsBySeverity[?(@.severity == 'CRITICAL')].count")
						.value(1))
				.andExpect(jsonPath("$.serviceReliability[0].serviceId")
						.value(serviceId.toString()))
				.andExpect(jsonPath("$.serviceReliability[0].totalChecks").value(3))
				.andExpect(jsonPath("$.serviceReliability[0].incidentCount").value(2));
	}

	@Test
	void returnsNullRatesForAnOrganizationWithoutMonitoringSamples()
			throws Exception {
		var owner = register("empty-analytics@example.com");
		var organizationId = createOrganization(owner, "Empty Analytics");
		createService(owner, organizationId);

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/analytics",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.param("from", "2026-07-01T00:00:00Z")
						.param("to", "2026-08-01T00:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reliability.totalChecks").value(0))
				.andExpect(jsonPath("$.reliability.uptimePercentage").isEmpty())
				.andExpect(jsonPath("$.reliability.p95ResponseTimeMilliseconds")
						.isEmpty())
				.andExpect(jsonPath("$.healthTrend").isEmpty())
				.andExpect(jsonPath("$.serviceReliability[0].uptimePercentage")
						.isEmpty());
	}

	@Test
	void rejectsInvalidRangesAndCrossOrganizationAccess() throws Exception {
		var owner = register("range-owner@example.com");
		var outsider = register("range-outsider@example.com");
		var organizationId = createOrganization(owner, "Private Analytics");

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/analytics",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.param("from", "2026-08-01T00:00:00Z")
						.param("to", "2026-07-01T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ANALYTICS_RANGE_INVALID"));

		mockMvc.perform(get(
						"/api/v1/organizations/{org}/analytics",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));
	}

	private void check(
			RegisteredUser user, UUID organizationId, UUID serviceId)
			throws Exception {
		mockMvc.perform(post(
						"/api/v1/organizations/{org}/services/{service}/check",
						organizationId,
						serviceId)
						.header(HttpHeaders.AUTHORIZATION, bearer(user.accessToken())))
				.andExpect(status().isOk());
	}

	private UUID createService(RegisteredUser owner, UUID organizationId)
			throws Exception {
		var result = mockMvc.perform(post(
						"/api/v1/organizations/{org}/services",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name":"Analytics API",
								  "serviceType":"HTTPS",
								  "url":"https://example.com",
								  "failureThreshold":1,
								  "recoveryThreshold":1
								}
								"""))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.id"));
	}

	private UUID createIncident(
			RegisteredUser owner,
			UUID organizationId,
			UUID serviceId,
			String severity) throws Exception {
		var result = mockMvc.perform(post(
						"/api/v1/organizations/{org}/incidents",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "serviceId":"%s",
								  "title":"Analytics fixture incident",
								  "severity":"%s"
								}
								""".formatted(serviceId, severity)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.id"));
	}

	private RegisteredUser register(String email) throws Exception {
		var result = mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "firstName":"Analytics",
								  "lastName":"User",
								  "email":"%s",
								  "password":"Correct-Horse-42"
								}
								""".formatted(email)))
				.andExpect(status().isCreated())
				.andReturn();
		return new RegisteredUser(read(result, "$.accessToken"));
	}

	private UUID createOrganization(RegisteredUser owner, String name)
			throws Exception {
		var result = mockMvc.perform(post("/api/v1/organizations")
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"%s\"}".formatted(name)))
				.andExpect(status().isCreated())
				.andReturn();
		return UUID.fromString(read(result, "$.id"));
	}

	private ManualCheckResult success(String checkedAt, long responseTime) {
		return new ManualCheckResult(
				Instant.parse(checkedAt), true, false, 200, responseTime,
				null, null, true, "healthy");
	}

	private ManualCheckResult failure(String checkedAt, long responseTime) {
		return new ManualCheckResult(
				Instant.parse(checkedAt), false, false, 503, responseTime,
				CheckErrorType.UNEXPECTED_STATUS, "Unexpected status.", false, "down");
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
