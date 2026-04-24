package com.pulseops.incident.dto;

import java.time.Instant;
import java.util.UUID;

import com.pulseops.incident.IncidentComment;

public record IncidentCommentResponse(
		UUID id,
		UUID authorId,
		String content,
		Instant createdAt,
		Instant updatedAt) {
	public static IncidentCommentResponse from(IncidentComment comment) {
		return new IncidentCommentResponse(
				comment.getId(), comment.getAuthorId(), comment.getContent(),
				comment.getCreatedAt(), comment.getUpdatedAt());
	}
}
