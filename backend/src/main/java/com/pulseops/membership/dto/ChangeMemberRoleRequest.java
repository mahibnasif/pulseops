package com.pulseops.membership.dto;

import jakarta.validation.constraints.NotNull;

import com.pulseops.membership.MembershipRole;

public record ChangeMemberRoleRequest(@NotNull MembershipRole role) {
}
