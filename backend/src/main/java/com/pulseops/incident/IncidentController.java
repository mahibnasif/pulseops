package com.pulseops.incident;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.pulseops.common.response.PageResponse;
import com.pulseops.common.security.CurrentUserService;
import com.pulseops.incident.dto.AssignIncidentRequest;
import com.pulseops.incident.dto.CommentRequest;
import com.pulseops.incident.dto.CreateIncidentRequest;
import com.pulseops.incident.dto.IncidentCommentResponse;
import com.pulseops.incident.dto.IncidentDetailsResponse;
import com.pulseops.incident.dto.IncidentResponse;
import com.pulseops.incident.dto.ResolveIncidentRequest;
import com.pulseops.incident.dto.UpdateIncidentRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/incidents")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

	private final IncidentManagementService incidentService;
	private final CurrentUserService currentUserService;

	public IncidentController(
			IncidentManagementService incidentService,
			CurrentUserService currentUserService) {
		this.incidentService = incidentService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	PageResponse<IncidentResponse> list(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@RequestParam(required = false) IncidentStatus status,
			@RequestParam(required = false) IncidentSeverity severity,
			@RequestParam(required = false) UUID serviceId,
			@RequestParam(required = false) UUID assigneeId,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "detectedAt") String sort,
			@RequestParam(defaultValue = "desc") String direction) {
		return incidentService.list(
				currentUserService.requireUser(jwt), organizationId, status, severity,
				serviceId, assigneeId, search, page, size, sort, direction);
	}

	@PostMapping
	ResponseEntity<IncidentResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@Valid @RequestBody CreateIncidentRequest request) {
		var incident = incidentService.create(
				currentUserService.requireUser(jwt), organizationId, request);
		return ResponseEntity.created(URI.create(
				"/api/v1/organizations/%s/incidents/%s"
						.formatted(organizationId, incident.id())))
				.body(incident);
	}

	@GetMapping("/{incidentId}")
	IncidentDetailsResponse get(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID incidentId) {
		return incidentService.get(
				currentUserService.requireUser(jwt), organizationId, incidentId);
	}

	@PatchMapping("/{incidentId}")
	IncidentResponse update(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID incidentId,
			@Valid @RequestBody UpdateIncidentRequest request) {
		return incidentService.update(
				currentUserService.requireUser(jwt), organizationId, incidentId, request);
	}

	@PostMapping("/{incidentId}/assign")
	IncidentResponse assign(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID incidentId,
			@RequestBody AssignIncidentRequest request) {
		return incidentService.assign(
				currentUserService.requireUser(jwt), organizationId, incidentId, request);
	}

	@PostMapping("/{incidentId}/comments")
	ResponseEntity<IncidentCommentResponse> comment(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID incidentId,
			@Valid @RequestBody CommentRequest request) {
		return ResponseEntity.status(201).body(incidentService.comment(
				currentUserService.requireUser(jwt), organizationId, incidentId, request));
	}

	@PostMapping("/{incidentId}/resolve")
	IncidentResponse resolve(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID incidentId,
			@Valid @RequestBody ResolveIncidentRequest request) {
		return incidentService.resolve(
				currentUserService.requireUser(jwt), organizationId, incidentId, request);
	}

	@PostMapping("/{incidentId}/reopen")
	IncidentResponse reopen(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID incidentId) {
		return incidentService.reopen(
				currentUserService.requireUser(jwt), organizationId, incidentId);
	}
}
