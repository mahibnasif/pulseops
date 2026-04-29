package com.pulseops.liveevent;

import java.util.UUID;

import com.pulseops.common.security.CurrentUserService;
import com.pulseops.organization.OrganizationAccessService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/events")
@SecurityRequirement(name = "bearerAuth")
public class LiveEventController {

	private final LiveEventStreamService streamService;
	private final CurrentUserService currentUserService;
	private final OrganizationAccessService accessService;

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring owns the injected singleton stream registry.")
	public LiveEventController(
			LiveEventStreamService streamService,
			CurrentUserService currentUserService,
			OrganizationAccessService accessService) {
		this.streamService = streamService;
		this.currentUserService = currentUserService;
		this.accessService = accessService;
	}

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	ResponseEntity<SseEmitter> subscribe(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId) {
		var user = currentUserService.requireUser(jwt);
		accessService.requireMember(organizationId, user.getId());
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header("X-Accel-Buffering", "no")
				.contentType(MediaType.TEXT_EVENT_STREAM)
				.body(streamService.subscribe(organizationId, jwt.getExpiresAt()));
	}
}
