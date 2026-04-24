package com.pulseops.incident.dto;

import java.util.List;

public record IncidentDetailsResponse(
		IncidentResponse incident,
		List<IncidentCommentResponse> comments,
		List<TimelineEventResponse> timeline) {

	public IncidentDetailsResponse {
		comments = List.copyOf(comments);
		timeline = List.copyOf(timeline);
	}
}
