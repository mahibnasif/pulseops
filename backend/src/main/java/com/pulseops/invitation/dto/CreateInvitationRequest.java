package com.pulseops.invitation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.pulseops.membership.MembershipRole;

public record CreateInvitationRequest(
		@NotBlank @Email @Size(max = 320) String email,
		@NotNull MembershipRole role) {
}
