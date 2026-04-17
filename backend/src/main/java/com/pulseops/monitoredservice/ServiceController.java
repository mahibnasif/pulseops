package com.pulseops.monitoredservice;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.pulseops.common.response.PageResponse;
import com.pulseops.common.security.CurrentUserService;
import com.pulseops.monitoredservice.dto.CreateServiceRequest;
import com.pulseops.monitoredservice.dto.ManualCheckResponse;
import com.pulseops.monitoredservice.dto.ServiceResponse;
import com.pulseops.monitoredservice.dto.UpdateServiceRequest;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/organizations/{organizationId}/services")
@SecurityRequirement(name = "bearerAuth")
public class ServiceController {

	private final ServiceManagementService serviceManagementService;
	private final CurrentUserService currentUserService;

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring owns the injected singleton service for the controller lifetime.")
	public ServiceController(
			ServiceManagementService serviceManagementService,
			CurrentUserService currentUserService) {
		this.serviceManagementService = serviceManagementService;
		this.currentUserService = currentUserService;
	}

	@GetMapping
	PageResponse<ServiceResponse> list(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@RequestParam(required = false) ServiceStatus status,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "name") String sort,
			@RequestParam(defaultValue = "asc") String direction) {
		return serviceManagementService.list(
				currentUserService.requireUser(jwt),
				organizationId,
				status,
				active,
				search,
				page,
				size,
				sort,
				direction);
	}

	@PostMapping
	ResponseEntity<ServiceResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@Valid @RequestBody CreateServiceRequest request) {
		var service = serviceManagementService.create(
				currentUserService.requireUser(jwt),
				organizationId,
				request);
		return ResponseEntity
				.created(URI.create(
						"/api/v1/organizations/%s/services/%s".formatted(
								organizationId,
								service.id())))
				.body(service);
	}

	@GetMapping("/{serviceId}")
	ServiceResponse get(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID serviceId) {
		return serviceManagementService.get(
				currentUserService.requireUser(jwt),
				organizationId,
				serviceId);
	}

	@PatchMapping("/{serviceId}")
	ServiceResponse update(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID serviceId,
			@Valid @RequestBody UpdateServiceRequest request) {
		return serviceManagementService.update(
				currentUserService.requireUser(jwt),
				organizationId,
				serviceId,
				request);
	}

	@DeleteMapping("/{serviceId}")
	ResponseEntity<Void> delete(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID serviceId) {
		serviceManagementService.delete(
				currentUserService.requireUser(jwt),
				organizationId,
				serviceId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{serviceId}/pause")
	ServiceResponse pause(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID serviceId) {
		return serviceManagementService.pause(
				currentUserService.requireUser(jwt),
				organizationId,
				serviceId);
	}

	@PostMapping("/{serviceId}/resume")
	ServiceResponse resume(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID serviceId) {
		return serviceManagementService.resume(
				currentUserService.requireUser(jwt),
				organizationId,
				serviceId);
	}

	@PostMapping("/{serviceId}/check")
	ManualCheckResponse manualCheck(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@PathVariable UUID serviceId) {
		return serviceManagementService.manualCheck(
				currentUserService.requireUser(jwt),
				organizationId,
				serviceId);
	}
}
