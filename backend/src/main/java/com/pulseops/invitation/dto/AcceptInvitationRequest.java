package com.pulseops.invitation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(@NotBlank @Size(max = 200) String token) {
}
