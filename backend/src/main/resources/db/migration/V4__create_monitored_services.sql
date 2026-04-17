CREATE TABLE monitored_services (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000),
    service_type VARCHAR(32) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    expected_status_code INTEGER NOT NULL,
    expected_response_text VARCHAR(500),
    expected_json_path VARCHAR(500),
    expected_json_value VARCHAR(500),
    timeout_milliseconds INTEGER NOT NULL,
    check_interval_seconds INTEGER NOT NULL,
    failure_threshold INTEGER NOT NULL,
    recovery_threshold INTEGER NOT NULL,
    degraded_latency_threshold_milliseconds INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_checked_at TIMESTAMPTZ,
    last_successful_check_at TIMESTAMPTZ,
    last_failure_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_monitored_services_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_monitored_services_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_monitored_services_type
        CHECK (service_type IN ('HTTP', 'HTTPS', 'TCP', 'JSON_API')),
    CONSTRAINT ck_monitored_services_method
        CHECK (http_method IN ('GET', 'HEAD')),
    CONSTRAINT ck_monitored_services_status
        CHECK (status IN ('UNKNOWN', 'OPERATIONAL', 'DEGRADED', 'DOWN', 'PAUSED')),
    CONSTRAINT ck_monitored_services_expected_status
        CHECK (expected_status_code BETWEEN 100 AND 599),
    CONSTRAINT ck_monitored_services_timeout
        CHECK (timeout_milliseconds BETWEEN 250 AND 60000),
    CONSTRAINT ck_monitored_services_interval
        CHECK (check_interval_seconds BETWEEN 30 AND 86400),
    CONSTRAINT ck_monitored_services_failure_threshold
        CHECK (failure_threshold BETWEEN 1 AND 20),
    CONSTRAINT ck_monitored_services_recovery_threshold
        CHECK (recovery_threshold BETWEEN 1 AND 20),
    CONSTRAINT ck_monitored_services_degraded_latency
        CHECK (degraded_latency_threshold_milliseconds BETWEEN 1 AND 60000)
);

CREATE UNIQUE INDEX uk_monitored_services_organization_name_active
    ON monitored_services (organization_id, LOWER(name))
    WHERE deleted_at IS NULL;
CREATE INDEX idx_monitored_services_organization_status
    ON monitored_services (organization_id, status)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_monitored_services_organization_active
    ON monitored_services (organization_id, is_active)
    WHERE deleted_at IS NULL;
