CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    service_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(30) NOT NULL,
    assigned_user_id UUID,
    detected_at TIMESTAMPTZ NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    root_cause VARCHAR(4000),
    resolution_summary VARCHAR(4000),
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_incidents_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_incidents_service
        FOREIGN KEY (service_id) REFERENCES monitored_services (id),
    CONSTRAINT fk_incidents_assigned_user
        FOREIGN KEY (assigned_user_id) REFERENCES users (id),
    CONSTRAINT fk_incidents_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_incidents_severity
        CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_status
        CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING', 'RESOLVED')),
    CONSTRAINT ck_incidents_source
        CHECK (source IN ('AUTOMATIC_MONITORING', 'MANUAL', 'INTEGRATION')),
    CONSTRAINT ck_incidents_resolution
        CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_incidents_active_automatic_service
    ON incidents (service_id)
    WHERE source = 'AUTOMATIC_MONITORING' AND status <> 'RESOLVED';
CREATE INDEX idx_incidents_organization_status
    ON incidents (organization_id, status, detected_at DESC);
CREATE INDEX idx_incidents_service_status
    ON incidents (service_id, status, detected_at DESC);
CREATE INDEX idx_incidents_assignee_status
    ON incidents (assigned_user_id, status)
    WHERE assigned_user_id IS NOT NULL;

CREATE TABLE incident_comments (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    author_id UUID NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_incident_comments_incident
        FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_comments_author
        FOREIGN KEY (author_id) REFERENCES users (id)
);

CREATE INDEX idx_incident_comments_incident_created
    ON incident_comments (incident_id, created_at);

CREATE TABLE incident_timeline_events (
    id UUID PRIMARY KEY,
    incident_id UUID NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_user_id UUID,
    message VARCHAR(1000) NOT NULL,
    old_value VARCHAR(500),
    new_value VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_incident_timeline_incident
        FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE,
    CONSTRAINT fk_incident_timeline_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT ck_incident_timeline_event_type
        CHECK (event_type IN (
            'INCIDENT_CREATED', 'STATUS_CHANGED', 'SEVERITY_CHANGED',
            'ASSIGNED', 'UNASSIGNED', 'COMMENT_ADDED', 'SERVICE_RECOVERED',
            'INCIDENT_RESOLVED', 'ROOT_CAUSE_UPDATED', 'RESOLUTION_UPDATED'
        ))
);

CREATE INDEX idx_incident_timeline_incident_created
    ON incident_timeline_events (incident_id, created_at);
