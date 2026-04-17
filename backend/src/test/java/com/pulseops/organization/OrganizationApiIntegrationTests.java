package com.pulseops.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.pulseops.auth.RefreshTokenRepository;
import com.pulseops.invitation.OrganizationInvitationRepository;
import com.pulseops.membership.MembershipRole;
import com.pulseops.membership.MembershipStatus;
import com.pulseops.membership.OrganizationMembershipRepository;
import com.pulseops.monitoredservice.MonitoredServiceRepository;
import com.pulseops.support.AbstractIntegrationTest;
import com.pulseops.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class OrganizationApiIntegrationTests extends AbstractIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

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

	@Autowired
	private MonitoredServiceRepository monitoredServiceRepository;

	@BeforeEach
	void cleanDatabase() {
		monitoredServiceRepository.deleteAll();
		invitationRepository.deleteAll();
		membershipRepository.deleteAll();
		organizationRepository.deleteAll();
		refreshTokenRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createsAnOrganizationAndMakesTheCreatorItsAdminOwner() throws Exception {
		var owner = register("owner@example.com", "Owner");

		var result = createOrganization(owner.accessToken(), "Platform Team", null)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Platform Team"))
				.andExpect(jsonPath("$.slug").value("platform-team"))
				.andExpect(jsonPath("$.ownerId").value(owner.userId().toString()))
				.andExpect(jsonPath("$.currentUserRole").value("ADMIN"))
				.andReturn();
		var organizationId = UUID.fromString(read(result, "$.id"));

		mockMvc.perform(get("/api/v1/organizations")
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(organizationId.toString()));

		var membership = membershipRepository
				.findByOrganizationIdAndUserIdAndStatus(
						organizationId,
						owner.userId(),
						MembershipStatus.ACTIVE)
				.orElseThrow();
		assertThat(membership.getRole()).isEqualTo(MembershipRole.ADMIN);
	}

	@Test
	void preventsCrossOrganizationAccessAndEnforcesViewerAuthorization() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var viewer = register("viewer@example.com", "Viewer");
		var outsider = register("outsider@example.com", "Outsider");
		var organizationId = createOrganizationId(owner, "Core Services");
		var invitationId = invite(owner, organizationId, viewer.email(), "VIEWER");

		mockMvc.perform(post("/api/v1/invitations/{id}/accept", invitationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(viewer.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentUserRole").value("VIEWER"));

		mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(outsider.accessToken())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ORGANIZATION_NOT_FOUND"));

		mockMvc.perform(patch("/api/v1/organizations/{id}", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(viewer.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"name":"Unauthorized rename"}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("INSUFFICIENT_ORGANIZATION_ROLE"));
	}

	@Test
	void storesInvitationSecretsAsHashesAndOnlyTheInvitedEmailCanAccept() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var invitee = register("engineer@example.com", "Engineer");
		var wrongUser = register("wrong@example.com", "Wrong");
		var organizationId = createOrganizationId(owner, "Reliability");

		var result = mockMvc.perform(post(
						"/api/v1/organizations/{id}/invitations",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"ENGINEER@example.com","role":"ENGINEER"}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.invitation.email").value("engineer@example.com"))
				.andReturn();
		var invitationId = UUID.fromString(read(result, "$.invitation.id"));
		var rawToken = read(result, "$.acceptanceToken");
		var persisted = invitationRepository.findById(invitationId).orElseThrow();
		assertThat(persisted.getTokenHash()).hasSize(64).doesNotContain(rawToken);

		mockMvc.perform(post("/api/v1/invitations/accept")
						.header(HttpHeaders.AUTHORIZATION, bearer(wrongUser.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(rawToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));

		mockMvc.perform(post("/api/v1/invitations/accept")
						.header(HttpHeaders.AUTHORIZATION, bearer(invitee.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"token":"%s"}
								""".formatted(rawToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(organizationId.toString()))
				.andExpect(jsonPath("$.currentUserRole").value("ENGINEER"));
	}

	@Test
	void adminCanChangeRolesAndRemoveMembers() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var member = register("member@example.com", "Member");
		var organizationId = createOrganizationId(owner, "Operations");
		var invitationId = invite(owner, organizationId, member.email(), "VIEWER");
		accept(member, invitationId);

		mockMvc.perform(patch(
						"/api/v1/organizations/{organizationId}/members/{userId}",
						organizationId,
						member.userId())
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"role":"ENGINEER"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ENGINEER"));

		mockMvc.perform(delete(
						"/api/v1/organizations/{organizationId}/members/{userId}",
						organizationId,
						member.userId())
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(member.accessToken())))
				.andExpect(status().isNotFound());
		assertThat(membershipRepository
				.findByOrganizationIdAndUserId(organizationId, member.userId())
				.orElseThrow()
				.getStatus()).isEqualTo(MembershipStatus.REMOVED);
	}

	@Test
	void ownershipMustBeTransferredBeforeTheOwnerCanLeave() throws Exception {
		var owner = register("owner@example.com", "Owner");
		var successor = register("successor@example.com", "Successor");
		var organizationId = createOrganizationId(owner, "Product");
		accept(successor, invite(owner, organizationId, successor.email(), "ENGINEER"));

		mockMvc.perform(post("/api/v1/organizations/{id}/leave", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OWNER_MUST_TRANSFER"));

		mockMvc.perform(post(
						"/api/v1/organizations/{id}/transfer-ownership",
						organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken()))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"newOwnerId":"%s"}
								""".formatted(successor.userId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownerId").value(successor.userId().toString()));

		mockMvc.perform(post("/api/v1/organizations/{id}/leave", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(owner.accessToken())))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
						.header(HttpHeaders.AUTHORIZATION, bearer(successor.accessToken())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.currentUserRole").value("ADMIN"));
	}

	@Test
	void rejectsDuplicateOrganizationSlugs() throws Exception {
		var owner = register("owner@example.com", "Owner");
		createOrganization(owner.accessToken(), "First", "shared-slug")
				.andExpect(status().isCreated());
		createOrganization(owner.accessToken(), "Second", "shared-slug")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ORGANIZATION_SLUG_TAKEN"));
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

	private UUID createOrganizationId(RegisteredUser user, String name) throws Exception {
		return UUID.fromString(read(
				createOrganization(user.accessToken(), name, null)
						.andExpect(status().isCreated())
						.andReturn(),
				"$.id"));
	}

	private org.springframework.test.web.servlet.ResultActions createOrganization(
			String accessToken,
			String name,
			String slug) throws Exception {
		var slugProperty = slug == null ? "" : ",\"slug\":\"" + slug + "\"";
		return mockMvc.perform(post("/api/v1/organizations")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"%s","description":"Test organization"%s}
						""".formatted(name, slugProperty)));
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

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private record RegisteredUser(UUID userId, String email, String accessToken) {
	}
}
