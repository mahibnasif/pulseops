package com.pulseops.incident;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.common.response.PageResponse;
import com.pulseops.incident.dto.AssignIncidentRequest;
import com.pulseops.incident.dto.CommentRequest;
import com.pulseops.incident.dto.CreateIncidentRequest;
import com.pulseops.incident.dto.IncidentCommentResponse;
import com.pulseops.incident.dto.IncidentDetailsResponse;
import com.pulseops.incident.dto.IncidentResponse;
import com.pulseops.incident.dto.ResolveIncidentRequest;
import com.pulseops.incident.dto.TimelineEventResponse;
import com.pulseops.incident.dto.UpdateIncidentRequest;
import com.pulseops.membership.MembershipStatus;
import com.pulseops.membership.OrganizationMembershipRepository;
import com.pulseops.monitoredservice.MonitoredServiceRepository;
import com.pulseops.organization.OrganizationAccessService;
import com.pulseops.user.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentManagementService {

	private static final Set<String> SORT_FIELDS =
			Set.of("detectedAt", "updatedAt", "severity", "status", "title");

	private final IncidentRepository incidentRepository;
	private final IncidentCommentRepository commentRepository;
	private final IncidentTimelineEventRepository timelineRepository;
	private final MonitoredServiceRepository serviceRepository;
	private final OrganizationMembershipRepository membershipRepository;
	private final OrganizationAccessService accessService;
	private final Clock clock;

	public IncidentManagementService(
			IncidentRepository incidentRepository,
			IncidentCommentRepository commentRepository,
			IncidentTimelineEventRepository timelineRepository,
			MonitoredServiceRepository serviceRepository,
			OrganizationMembershipRepository membershipRepository,
			OrganizationAccessService accessService,
			Clock clock) {
		this.incidentRepository = incidentRepository;
		this.commentRepository = commentRepository;
		this.timelineRepository = timelineRepository;
		this.serviceRepository = serviceRepository;
		this.membershipRepository = membershipRepository;
		this.accessService = accessService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public PageResponse<IncidentResponse> list(
			User user,
			UUID organizationId,
			IncidentStatus status,
			IncidentSeverity severity,
			UUID serviceId,
			UUID assigneeId,
			String search,
			int page,
			int size,
			String sort,
			String direction) {
		accessService.requireMember(organizationId, user.getId());
		var sortField = SORT_FIELDS.contains(sort) ? sort : "detectedAt";
		var sortDirection = "asc".equalsIgnoreCase(direction)
				? Sort.Direction.ASC
				: Sort.Direction.DESC;
		return PageResponse.from(incidentRepository.search(
				organizationId,
				status,
				severity,
				serviceId,
				assigneeId,
				search == null ? "" : search.trim(),
				PageRequest.of(page, Math.min(size, 100), sortDirection, sortField))
				.map(IncidentResponse::from));
	}

	@Transactional
	public IncidentResponse create(
			User user, UUID organizationId, CreateIncidentRequest request) {
		accessService.requireEngineer(organizationId, user.getId());
		requireService(organizationId, request.serviceId());
		var now = Instant.now(clock);
		var incident = incidentRepository.saveAndFlush(Incident.create(
				organizationId,
				request.serviceId(),
				request.title().trim(),
				trimToNull(request.description()),
				request.severity(),
				IncidentSource.MANUAL,
				user.getId(),
				now));
		event(
				incident,
				TimelineEventType.INCIDENT_CREATED,
				user.getId(),
				"Manual incident created.",
				null,
				IncidentStatus.OPEN.name(),
				now);
		return IncidentResponse.from(incident);
	}

	@Transactional(readOnly = true)
	public IncidentDetailsResponse get(User user, UUID organizationId, UUID incidentId) {
		accessService.requireMember(organizationId, user.getId());
		var incident = requireIncident(organizationId, incidentId, false);
		return new IncidentDetailsResponse(
				IncidentResponse.from(incident),
				commentRepository.findAllByIncidentIdOrderByCreatedAtAsc(incidentId)
						.stream().map(IncidentCommentResponse::from).toList(),
				timelineRepository.findAllByIncidentIdOrderByCreatedAtAsc(incidentId)
						.stream().map(TimelineEventResponse::from).toList());
	}

	@Transactional
	public IncidentResponse update(
			User user,
			UUID organizationId,
			UUID incidentId,
			UpdateIncidentRequest request) {
		accessService.requireEngineer(organizationId, user.getId());
		if (allNull(request)) {
			throw badRequest("EMPTY_UPDATE", "At least one incident field is required.");
		}
		var incident = requireIncident(organizationId, incidentId, true);
		var now = Instant.now(clock);
		if (request.severity() != null && request.severity() != incident.getSeverity()) {
			var old = incident.getSeverity();
			incident.changeSeverity(request.severity(), now);
			event(incident, TimelineEventType.SEVERITY_CHANGED, user.getId(),
					"Incident severity changed.", old.name(), request.severity().name(), now);
		}
		if (request.status() != null && request.status() != incident.getStatus()) {
			changeStatus(incident, request.status(), user.getId(), now);
		}
		var title = request.title() == null ? incident.getTitle() : request.title().trim();
		if (title.isEmpty()) {
			throw badRequest("VALIDATION_ERROR", "Incident title cannot be blank.");
		}
		var description = request.description() == null
				? incident.getDescription() : trimToNull(request.description());
		var rootCause = request.rootCause() == null
				? incident.getRootCause() : trimToNull(request.rootCause());
		var summary = request.resolutionSummary() == null
				? incident.getResolutionSummary() : trimToNull(request.resolutionSummary());
		if (request.rootCause() != null && !same(incident.getRootCause(), rootCause)) {
			event(incident, TimelineEventType.ROOT_CAUSE_UPDATED, user.getId(),
					"Root cause updated.", incident.getRootCause(), rootCause, now);
		}
		if (request.resolutionSummary() != null
				&& !same(incident.getResolutionSummary(), summary)) {
			event(incident, TimelineEventType.RESOLUTION_UPDATED, user.getId(),
					"Resolution summary updated.", incident.getResolutionSummary(), summary, now);
		}
		incident.updateDetails(title, description, rootCause, summary, now);
		return IncidentResponse.from(incident);
	}

	@Transactional
	public IncidentResponse assign(
			User user,
			UUID organizationId,
			UUID incidentId,
			AssignIncidentRequest request) {
		accessService.requireEngineer(organizationId, user.getId());
		var incident = requireIncident(organizationId, incidentId, true);
		var assignedUserId = request.userId();
		if (assignedUserId != null && membershipRepository
				.findByOrganizationIdAndUserIdAndStatus(
						organizationId, assignedUserId, MembershipStatus.ACTIVE)
				.isEmpty()) {
			throw badRequest(
					"INVALID_INCIDENT_ASSIGNEE",
					"The assignee must be an active organization member.");
		}
		var previous = incident.getAssignedUserId();
		if (java.util.Objects.equals(previous, assignedUserId)) {
			return IncidentResponse.from(incident);
		}
		var now = Instant.now(clock);
		incident.assign(assignedUserId, now);
		event(
				incident,
				assignedUserId == null ? TimelineEventType.UNASSIGNED : TimelineEventType.ASSIGNED,
				user.getId(),
				assignedUserId == null ? "Incident unassigned." : "Incident assigned.",
				previous == null ? null : previous.toString(),
				assignedUserId == null ? null : assignedUserId.toString(),
				now);
		return IncidentResponse.from(incident);
	}

	@Transactional
	public IncidentCommentResponse comment(
			User user,
			UUID organizationId,
			UUID incidentId,
			CommentRequest request) {
		accessService.requireEngineer(organizationId, user.getId());
		var incident = requireIncident(organizationId, incidentId, true);
		var now = Instant.now(clock);
		var comment = commentRepository.save(IncidentComment.create(
				incidentId, user.getId(), request.content().trim(), now));
		event(incident, TimelineEventType.COMMENT_ADDED, user.getId(),
				"Comment added.", null, comment.getId().toString(), now);
		return IncidentCommentResponse.from(comment);
	}

	@Transactional
	public IncidentResponse resolve(
			User user,
			UUID organizationId,
			UUID incidentId,
			ResolveIncidentRequest request) {
		accessService.requireEngineer(organizationId, user.getId());
		var incident = requireIncident(organizationId, incidentId, true);
		if (incident.getStatus() == IncidentStatus.RESOLVED) {
			throw conflict("INCIDENT_ALREADY_RESOLVED", "The incident is already resolved.");
		}
		var previous = incident.getStatus();
		var now = Instant.now(clock);
		incident.resolve(
				trimToNull(request.rootCause()),
				request.resolutionSummary().trim(),
				now);
		event(incident, TimelineEventType.INCIDENT_RESOLVED, user.getId(),
				"Incident resolved.", previous.name(), IncidentStatus.RESOLVED.name(), now);
		return IncidentResponse.from(incident);
	}

	@Transactional
	public IncidentResponse reopen(User user, UUID organizationId, UUID incidentId) {
		accessService.requireEngineer(organizationId, user.getId());
		var incident = requireIncident(organizationId, incidentId, true);
		if (incident.getStatus() != IncidentStatus.RESOLVED) {
			throw conflict("INCIDENT_NOT_RESOLVED", "Only a resolved incident can be reopened.");
		}
		if (incident.getSource() == IncidentSource.AUTOMATIC_MONITORING
				&& incidentRepository.findActiveAutomaticByServiceId(
						incident.getServiceId()).isPresent()) {
			throw conflict(
					"ACTIVE_AUTOMATIC_INCIDENT_EXISTS",
					"The service already has an active automatic incident.");
		}
		var now = Instant.now(clock);
		incident.reopen(now);
		event(incident, TimelineEventType.STATUS_CHANGED, user.getId(),
				"Incident reopened.", IncidentStatus.RESOLVED.name(),
				IncidentStatus.OPEN.name(), now);
		return IncidentResponse.from(incident);
	}

	private void changeStatus(
			Incident incident, IncidentStatus target, UUID actorId, Instant now) {
		if (target == IncidentStatus.RESOLVED) {
			throw badRequest(
					"USE_RESOLVE_ENDPOINT",
					"Resolve the incident with a resolution summary.");
		}
		if (incident.getStatus() == IncidentStatus.RESOLVED) {
			throw conflict("INCIDENT_RESOLVED", "Reopen the incident before changing status.");
		}
		var allowed = allowedTransitions(incident.getStatus());
		if (!allowed.contains(target)) {
			throw conflict(
					"INVALID_INCIDENT_TRANSITION",
					"The requested incident status transition is not allowed.");
		}
		var previous = incident.getStatus();
		incident.changeStatus(target, now);
		event(incident, TimelineEventType.STATUS_CHANGED, actorId,
				"Incident status changed.", previous.name(), target.name(), now);
	}

	private EnumSet<IncidentStatus> allowedTransitions(IncidentStatus status) {
		return switch (status) {
			case OPEN -> EnumSet.of(
					IncidentStatus.ACKNOWLEDGED,
					IncidentStatus.INVESTIGATING,
					IncidentStatus.IDENTIFIED,
					IncidentStatus.MONITORING);
			case ACKNOWLEDGED -> EnumSet.of(
					IncidentStatus.INVESTIGATING,
					IncidentStatus.IDENTIFIED,
					IncidentStatus.MONITORING);
			case INVESTIGATING -> EnumSet.of(
					IncidentStatus.IDENTIFIED,
					IncidentStatus.MONITORING);
			case IDENTIFIED -> EnumSet.of(
					IncidentStatus.INVESTIGATING,
					IncidentStatus.MONITORING);
			case MONITORING -> EnumSet.of(
					IncidentStatus.INVESTIGATING,
					IncidentStatus.IDENTIFIED);
			case RESOLVED -> EnumSet.noneOf(IncidentStatus.class);
		};
	}

	private Incident requireIncident(
			UUID organizationId, UUID incidentId, boolean locked) {
		return (locked
				? incidentRepository.findLockedByIdAndOrganizationId(incidentId, organizationId)
				: incidentRepository.findByIdAndOrganizationId(incidentId, organizationId))
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"INCIDENT_NOT_FOUND",
						"The incident could not be found."));
	}

	private void requireService(UUID organizationId, UUID serviceId) {
		if (serviceRepository
				.findByIdAndOrganizationIdAndDeletedAtIsNull(serviceId, organizationId)
				.isEmpty()) {
			throw badRequest(
					"INVALID_INCIDENT_SERVICE",
					"The service must belong to the organization.");
		}
	}

	private void event(
			Incident incident,
			TimelineEventType type,
			UUID actor,
			String message,
			String oldValue,
			String newValue,
			Instant now) {
		timelineRepository.save(IncidentTimelineEvent.create(
				incident.getId(), type, actor, message, oldValue, newValue, now));
	}

	private boolean allNull(UpdateIncidentRequest request) {
		return request.title() == null
				&& request.description() == null
				&& request.severity() == null
				&& request.status() == null
				&& request.rootCause() == null
				&& request.resolutionSummary() == null;
	}

	private boolean same(String left, String right) {
		return java.util.Objects.equals(left, right);
	}

	private String trimToNull(String value) {
		if (value == null) return null;
		var trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private ApiException badRequest(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	private ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}
}
