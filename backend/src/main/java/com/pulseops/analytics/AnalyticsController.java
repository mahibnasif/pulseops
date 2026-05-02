package com.pulseops.analytics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.pulseops.analytics.dto.AnalyticsResponse;
import com.pulseops.common.exception.ApiException;
import com.pulseops.common.security.CurrentUserService;
import com.pulseops.organization.OrganizationAccessService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/analytics")
@SecurityRequirement(name = "bearerAuth")
public class AnalyticsController {

	static final Duration DEFAULT_RANGE = Duration.ofDays(30);
	static final Duration MAXIMUM_RANGE = Duration.ofDays(366);

	private final AnalyticsService analyticsService;
	private final CurrentUserService currentUserService;
	private final OrganizationAccessService accessService;
	private final Clock clock;

	public AnalyticsController(
			AnalyticsService analyticsService,
			CurrentUserService currentUserService,
			OrganizationAccessService accessService,
			Clock clock) {
		this.analyticsService = analyticsService;
		this.currentUserService = currentUserService;
		this.accessService = accessService;
		this.clock = clock;
	}

	@GetMapping
	AnalyticsResponse snapshot(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID organizationId,
			@RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to) {
		var user = currentUserService.requireUser(jwt);
		accessService.requireMember(organizationId, user.getId());
		var effectiveTo = to == null ? Instant.now(clock) : to;
		var effectiveFrom = from == null
				? effectiveTo.minus(DEFAULT_RANGE)
				: from;
		var range = Duration.between(effectiveFrom, effectiveTo);
		if (range.isZero() || range.isNegative()
				|| range.compareTo(MAXIMUM_RANGE) > 0) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"ANALYTICS_RANGE_INVALID",
					"Analytics ranges must be positive and no longer than 366 days.");
		}
		return analyticsService.snapshot(
				organizationId,
				effectiveFrom,
				effectiveTo,
				AnalyticsBucket.forRange(range));
	}
}
