package com.pulseops.incident;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_comments")
public class IncidentComment {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;
	@Column(name = "incident_id", nullable = false)
	private UUID incidentId;
	@Column(name = "author_id", nullable = false)
	private UUID authorId;
	@Column(nullable = false, length = 4000)
	private String content;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected IncidentComment() {
	}

	public static IncidentComment create(
			UUID incidentId, UUID authorId, String content, Instant now) {
		var comment = new IncidentComment();
		comment.incidentId = incidentId;
		comment.authorId = authorId;
		comment.content = content;
		comment.createdAt = now;
		comment.updatedAt = now;
		return comment;
	}

	public UUID getId() { return id; }
	public UUID getIncidentId() { return incidentId; }
	public UUID getAuthorId() { return authorId; }
	public String getContent() { return content; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
