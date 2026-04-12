package com.pulseops.membership.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(@NotNull UUID newOwnerId) {
}
