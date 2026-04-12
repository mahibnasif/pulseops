package com.pulseops.invitation.dto;

public record CreatedInvitationResponse(
		InvitationResponse invitation,
		String acceptanceToken) {
}
