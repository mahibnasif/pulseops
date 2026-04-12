package com.pulseops.organization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.pulseops.common.exception.ApiException;
import com.pulseops.invitation.OrganizationInvitation;
import com.pulseops.invitation.OrganizationInvitationRepository;
import com.pulseops.invitation.dto.AcceptInvitationRequest;
import com.pulseops.invitation.dto.CreateInvitationRequest;
import com.pulseops.invitation.dto.CreatedInvitationResponse;
import com.pulseops.invitation.dto.InvitationResponse;
import com.pulseops.membership.MembershipRole;
import com.pulseops.membership.MembershipStatus;
import com.pulseops.membership.OrganizationMembership;
import com.pulseops.membership.OrganizationMembershipRepository;
import com.pulseops.membership.dto.MemberResponse;
import com.pulseops.organization.dto.CreateOrganizationRequest;
import com.pulseops.organization.dto.OrganizationResponse;
import com.pulseops.organization.dto.UpdateOrganizationRequest;
import com.pulseops.user.User;
import com.pulseops.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

	private static final Duration INVITATION_TTL = Duration.ofDays(7);
	private static final int INVITATION_TOKEN_BYTES = 32;

	private final OrganizationRepository organizationRepository;
	private final OrganizationMembershipRepository membershipRepository;
	private final OrganizationInvitationRepository invitationRepository;
	private final UserRepository userRepository;
	private final OrganizationAccessService accessService;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();
	public OrganizationService(
			OrganizationRepository organizationRepository,
			OrganizationMembershipRepository membershipRepository,
			OrganizationInvitationRepository invitationRepository,
			UserRepository userRepository,
			OrganizationAccessService accessService,
			Clock clock) {
		this.organizationRepository = organizationRepository;
		this.membershipRepository = membershipRepository;
		this.invitationRepository = invitationRepository;
		this.userRepository = userRepository;
		this.accessService = accessService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<OrganizationResponse> listOrganizations(User user) {
		return membershipRepository
				.findAllByUserIdAndStatusOrderByJoinedAtAsc(user.getId(), MembershipStatus.ACTIVE)
				.stream()
				.map(membership -> organizationRepository
						.findById(membership.getOrganizationId())
						.map(organization -> OrganizationResponse.from(
								organization,
								membership.getRole()))
						.orElse(null))
				.filter(java.util.Objects::nonNull)
				.toList();
	}

	@Transactional
	public OrganizationResponse createOrganization(User user, CreateOrganizationRequest request) {
		var now = Instant.now(clock);
		var name = requireText(request.name(), "Organization name is required.");
		var slug = request.slug() == null || request.slug().isBlank()
				? uniqueGeneratedSlug(name)
				: request.slug().trim();
		if (organizationRepository.existsBySlug(slug)) {
			throw slugConflict();
		}

		var organization = organizationRepository.save(Organization.create(
				name,
				slug,
				trimToNull(request.description()),
				user.getId(),
				now));
		membershipRepository.save(OrganizationMembership.active(
				organization.getId(),
				user.getId(),
				MembershipRole.ADMIN,
				now));
		return OrganizationResponse.from(organization, MembershipRole.ADMIN);
	}

	@Transactional(readOnly = true)
	public OrganizationResponse getOrganization(User user, UUID organizationId) {
		var membership = accessService.requireMember(organizationId, user.getId());
		return OrganizationResponse.from(
				requireOrganization(organizationId),
				membership.getRole());
	}

	@Transactional
	public OrganizationResponse updateOrganization(
			User user,
			UUID organizationId,
			UpdateOrganizationRequest request) {
		var membership = accessService.requireAdmin(organizationId, user.getId());
		if (request.name() == null && request.slug() == null && request.description() == null) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"EMPTY_UPDATE",
					"At least one organization field must be supplied.");
		}

		var organization = requireOrganization(organizationId);
		var name = request.name() == null
				? null
				: requireText(request.name(), "Organization name cannot be blank.");
		var slug = request.slug() == null ? null : request.slug().trim();
		if (slug != null
				&& !slug.equals(organization.getSlug())
				&& organizationRepository.existsBySlug(slug)) {
			throw slugConflict();
		}
		var description = request.description() == null
				? null
				: request.description().trim();
		organization.update(name, slug, description, Instant.now(clock));
		return OrganizationResponse.from(organization, membership.getRole());
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> listMembers(User user, UUID organizationId) {
		accessService.requireMember(organizationId, user.getId());
		return membershipRepository
				.findAllByOrganizationIdAndStatusOrderByJoinedAtAsc(
						organizationId,
						MembershipStatus.ACTIVE)
				.stream()
				.map(membership -> userRepository
						.findById(membership.getUserId())
						.map(member -> MemberResponse.from(membership, member))
						.orElse(null))
				.filter(java.util.Objects::nonNull)
				.toList();
	}

	@Transactional
	public MemberResponse changeMemberRole(
			User actor,
			UUID organizationId,
			UUID memberUserId,
			MembershipRole role) {
		accessService.requireAdmin(organizationId, actor.getId());
		if (requireOrganization(organizationId).getOwnerId().equals(memberUserId)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"OWNER_ROLE_LOCKED",
					"Transfer ownership before changing the owner's role.");
		}
		var membership = requireActiveMembership(organizationId, memberUserId);
		membership.changeRole(role, Instant.now(clock));
		var member = userRepository.findById(memberUserId).orElseThrow(this::memberNotFound);
		return MemberResponse.from(membership, member);
	}

	@Transactional
	public void removeMember(User actor, UUID organizationId, UUID memberUserId) {
		accessService.requireAdmin(organizationId, actor.getId());
		if (requireOrganization(organizationId).getOwnerId().equals(memberUserId)) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"OWNER_CANNOT_BE_REMOVED",
					"Transfer ownership before removing the owner.");
		}
		requireActiveMembership(organizationId, memberUserId).remove(Instant.now(clock));
	}

	@Transactional
	public void leaveOrganization(User user, UUID organizationId) {
		var membership = accessService.requireMember(organizationId, user.getId());
		if (requireOrganization(organizationId).getOwnerId().equals(user.getId())) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"OWNER_MUST_TRANSFER",
					"Transfer ownership before leaving the organization.");
		}
		membership.leave(Instant.now(clock));
	}

	@Transactional
	public OrganizationResponse transferOwnership(
			User actor,
			UUID organizationId,
			UUID newOwnerId) {
		var actorMembership = accessService.requireAdmin(organizationId, actor.getId());
		var organization = requireOrganization(organizationId);
		if (!organization.getOwnerId().equals(actor.getId())) {
			throw new ApiException(
					HttpStatus.FORBIDDEN,
					"OWNER_ACCESS_REQUIRED",
					"Only the organization owner can transfer ownership.");
		}
		if (actor.getId().equals(newOwnerId)) {
			throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"OWNER_UNCHANGED",
					"The selected member already owns the organization.");
		}

		var newOwnerMembership = requireActiveMembership(organizationId, newOwnerId);
		newOwnerMembership.changeRole(MembershipRole.ADMIN, Instant.now(clock));
		organization.transferOwnership(newOwnerId, Instant.now(clock));
		return OrganizationResponse.from(
				organization,
				actorMembership.getRole());
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> listOrganizationInvitations(
			User user,
			UUID organizationId) {
		accessService.requireAdmin(organizationId, user.getId());
		var organization = requireOrganization(organizationId);
		return invitationRepository
				.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId)
				.stream()
				.map(invitation -> InvitationResponse.from(
						invitation,
						organization))
				.toList();
	}

	@Transactional
	public CreatedInvitationResponse invite(
			User actor,
			UUID organizationId,
			CreateInvitationRequest request) {
		accessService.requireAdmin(organizationId, actor.getId());
		var organization = requireOrganization(organizationId);
		var now = Instant.now(clock);
		var email = normalizeEmail(request.email());

		userRepository.findByEmail(email).ifPresent(existingUser -> membershipRepository
				.findByOrganizationIdAndUserIdAndStatus(
						organizationId,
						existingUser.getId(),
						MembershipStatus.ACTIVE)
				.ifPresent(existingMembership -> {
					throw new ApiException(
							HttpStatus.CONFLICT,
							"MEMBERSHIP_ALREADY_EXISTS",
							"The user is already an active organization member.");
				}));

		invitationRepository
				.findFirstByOrganizationIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
						organizationId,
						email)
				.filter(invitation -> invitation.isPendingAt(now))
				.ifPresent(invitation -> {
					throw new ApiException(
							HttpStatus.CONFLICT,
							"INVITATION_ALREADY_PENDING",
							"A pending invitation already exists for this email.");
				});

		var rawToken = generateToken();
		var invitation = invitationRepository.save(OrganizationInvitation.create(
				organizationId,
				email,
				request.role(),
				hashToken(rawToken),
				now.plus(INVITATION_TTL),
				actor.getId(),
				now));
		return new CreatedInvitationResponse(
				InvitationResponse.from(invitation, organization),
				rawToken);
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> listPendingInvitations(User user) {
		var now = Instant.now(clock);
		return invitationRepository
				.findAllByEmailAndAcceptedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
						user.getEmail(),
						now)
				.stream()
				.map(invitation -> organizationRepository
						.findById(invitation.getOrganizationId())
						.map(organization -> InvitationResponse.from(invitation, organization))
						.orElse(null))
				.filter(java.util.Objects::nonNull)
				.toList();
	}

	@Transactional
	public OrganizationResponse acceptInvitation(User user, UUID invitationId) {
		var invitation = invitationRepository
				.findLockedById(invitationId)
				.orElseThrow(this::invitationNotFound);
		return acceptInvitation(user, invitation);
	}

	@Transactional
	public OrganizationResponse acceptInvitation(User user, AcceptInvitationRequest request) {
		var invitation = invitationRepository
				.findLockedByTokenHash(hashToken(request.token()))
				.orElseThrow(this::invitationNotFound);
		return acceptInvitation(user, invitation);
	}

	@Transactional
	public void cancelInvitation(User actor, UUID organizationId, UUID invitationId) {
		accessService.requireAdmin(organizationId, actor.getId());
		var invitation = invitationRepository
				.findById(invitationId)
				.filter(candidate -> candidate.getOrganizationId().equals(organizationId))
				.orElseThrow(this::invitationNotFound);
		if (invitation.getAcceptedAt() != null) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"INVITATION_ALREADY_ACCEPTED",
					"An accepted invitation cannot be cancelled.");
		}
		invitationRepository.delete(invitation);
	}

	private OrganizationResponse acceptInvitation(User user, OrganizationInvitation invitation) {
		var now = Instant.now(clock);
		if (!invitation.getEmail().equals(user.getEmail())) {
			throw invitationNotFound();
		}
		if (invitation.getAcceptedAt() != null) {
			throw new ApiException(
					HttpStatus.CONFLICT,
					"INVITATION_ALREADY_ACCEPTED",
					"The invitation has already been accepted.");
		}
		if (!invitation.getExpiresAt().isAfter(now)) {
			throw new ApiException(
					HttpStatus.GONE,
					"INVITATION_EXPIRED",
					"The invitation has expired.");
		}

		var membership = membershipRepository
				.findLockedByOrganizationIdAndUserId(
						invitation.getOrganizationId(),
						user.getId())
				.orElseGet(() -> OrganizationMembership.active(
						invitation.getOrganizationId(),
						user.getId(),
						invitation.getRole(),
						now));
		if (membership.getStatus() != MembershipStatus.ACTIVE) {
			membership.reactivate(invitation.getRole(), now);
		}
		else {
			membership.changeRole(invitation.getRole(), now);
		}
		membershipRepository.save(membership);
		invitation.accept(now);

		var organization = organizationRepository
				.findById(invitation.getOrganizationId())
				.orElseThrow(this::invitationNotFound);
		return OrganizationResponse.from(organization, membership.getRole());
	}

	private OrganizationMembership requireActiveMembership(
			UUID organizationId,
			UUID userId) {
		return membershipRepository
				.findByOrganizationIdAndUserIdAndStatus(
						organizationId,
						userId,
						MembershipStatus.ACTIVE)
				.orElseThrow(this::memberNotFound);
	}

	private String uniqueGeneratedSlug(String name) {
		var normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "")
				.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-|-$", "");
		var base = normalized.isBlank() ? "organization" : normalized;
		base = base.substring(0, Math.min(base.length(), 72));
		if (!organizationRepository.existsBySlug(base)) {
			return base;
		}
		return base + "-" + UUID.randomUUID().toString().substring(0, 7);
	}

	private String generateToken() {
		var bytes = new byte[INVITATION_TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hashToken(String token) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable.", exception);
		}
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	private String requireText(String value, String message) {
		var trimmed = value.trim();
		if (trimmed.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
		}
		return trimmed;
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		var trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private ApiException slugConflict() {
		return new ApiException(
				HttpStatus.CONFLICT,
				"ORGANIZATION_SLUG_TAKEN",
				"The organization slug is already in use.");
	}

	private ApiException memberNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"MEMBER_NOT_FOUND",
				"The organization member could not be found.");
	}

	private ApiException invitationNotFound() {
		return new ApiException(
				HttpStatus.NOT_FOUND,
				"INVITATION_NOT_FOUND",
				"The invitation could not be found.");
	}

	private Organization requireOrganization(UUID organizationId) {
		return organizationRepository
				.findById(organizationId)
				.orElseThrow(() -> new ApiException(
						HttpStatus.NOT_FOUND,
						"ORGANIZATION_NOT_FOUND",
						"The organization could not be found."));
	}
}
