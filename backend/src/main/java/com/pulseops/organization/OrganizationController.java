package com.pulseops.organization;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.pulseops.common.security.CurrentUserService;
import com.pulseops.invitation.dto.CreateInvitationRequest;
import com.pulseops.invitation.dto.CreatedInvitationResponse;
import com.pulseops.invitation.dto.InvitationResponse;
import com.pulseops.membership.dto.ChangeMemberRoleRequest;
import com.pulseops.membership.dto.MemberResponse;
import com.pulseops.membership.dto.TransferOwnershipRequest;
import com.pulseops.organization.dto.CreateOrganizationRequest;
import com.pulseops.organization.dto.OrganizationResponse;
import com.pulseops.organization.dto.UpdateOrganizationRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
@SecurityRequirement(name = "bearerAuth")
public class OrganizationController {

	private final OrganizationService organizationService;
	private final CurrentUserService currentUserService;

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring owns the injected singleton service for the controller lifetime.")
	public OrganizationController(
			OrganizationService organizationService,
			CurrentUserService currentUserService) {
		this.organizationService = organizationService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	List<OrganizationResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return organizationService.listOrganizations(currentUserService.requireUser(jwt));
	}

	@PostMapping
	ResponseEntity<OrganizationResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateOrganizationRequest request) {
		var organization = organizationService.createOrganization(
				currentUserService.requireUser(jwt),
				request);
		return ResponseEntity
				.created(URI.create("/api/v1/organizations/" + organization.id()))
				.body(organization);
	}

	@GetMapping("/{organizationId}")
	OrganizationResponse get(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId) {
		return organizationService.getOrganization(
				currentUserService.requireUser(jwt),
				organizationId);
	}

	@PatchMapping("/{organizationId}")
	OrganizationResponse update(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@Valid @RequestBody UpdateOrganizationRequest request) {
		return organizationService.updateOrganization(
				currentUserService.requireUser(jwt),
				organizationId,
				request);
	}

	@GetMapping("/{organizationId}/members")
	List<MemberResponse> members(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId) {
		return organizationService.listMembers(
				currentUserService.requireUser(jwt),
				organizationId);
	}

	@PatchMapping("/{organizationId}/members/{memberUserId}")
	MemberResponse changeRole(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID memberUserId,
			@Valid @RequestBody ChangeMemberRoleRequest request) {
		return organizationService.changeMemberRole(
				currentUserService.requireUser(jwt),
				organizationId,
				memberUserId,
				request.role());
	}

	@DeleteMapping("/{organizationId}/members/{memberUserId}")
	ResponseEntity<Void> removeMember(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID memberUserId) {
		organizationService.removeMember(
				currentUserService.requireUser(jwt),
				organizationId,
				memberUserId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{organizationId}/leave")
	ResponseEntity<Void> leave(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId) {
		organizationService.leaveOrganization(
				currentUserService.requireUser(jwt),
				organizationId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{organizationId}/transfer-ownership")
	OrganizationResponse transferOwnership(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@Valid @RequestBody TransferOwnershipRequest request) {
		return organizationService.transferOwnership(
				currentUserService.requireUser(jwt),
				organizationId,
				request.newOwnerId());
	}

	@GetMapping("/{organizationId}/invitations")
	List<InvitationResponse> invitations(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId) {
		return organizationService.listOrganizationInvitations(
				currentUserService.requireUser(jwt),
				organizationId);
	}

	@PostMapping("/{organizationId}/invitations")
	ResponseEntity<CreatedInvitationResponse> invite(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@Valid @RequestBody CreateInvitationRequest request) {
		var invitation = organizationService.invite(
				currentUserService.requireUser(jwt),
				organizationId,
				request);
		return ResponseEntity
				.created(URI.create(
						"/api/v1/organizations/%s/invitations/%s".formatted(
								organizationId,
								invitation.invitation().id())))
				.body(invitation);
	}

	@DeleteMapping("/{organizationId}/invitations/{invitationId}")
	ResponseEntity<Void> cancelInvitation(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID invitationId) {
		organizationService.cancelInvitation(
				currentUserService.requireUser(jwt),
				organizationId,
				invitationId);
		return ResponseEntity.noContent().build();
	}
}
