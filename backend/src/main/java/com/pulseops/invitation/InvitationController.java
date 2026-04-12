package com.pulseops.invitation;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.pulseops.common.security.CurrentUserService;
import com.pulseops.invitation.dto.AcceptInvitationRequest;
import com.pulseops.invitation.dto.InvitationResponse;
import com.pulseops.organization.OrganizationService;
import com.pulseops.organization.dto.OrganizationResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
@SecurityRequirement(name = "bearerAuth")
public class InvitationController {

	private final OrganizationService organizationService;
	private final CurrentUserService currentUserService;

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring owns the injected singleton service for the controller lifetime.")
	public InvitationController(
			OrganizationService organizationService,
			CurrentUserService currentUserService) {
		this.organizationService = organizationService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	List<InvitationResponse> pending(@AuthenticationPrincipal Jwt jwt) {
		return organizationService.listPendingInvitations(
				currentUserService.requireUser(jwt));
	}

	@PostMapping("/{invitationId}/accept")
	OrganizationResponse acceptById(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID invitationId) {
		return organizationService.acceptInvitation(
				currentUserService.requireUser(jwt),
				invitationId);
	}

	@PostMapping("/accept")
	OrganizationResponse acceptByToken(
			@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody AcceptInvitationRequest request) {
		return organizationService.acceptInvitation(
				currentUserService.requireUser(jwt),
				request);
	}
}
