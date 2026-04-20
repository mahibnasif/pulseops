ALTER TABLE monitored_services
    ADD COLUMN next_check_at TIMESTAMPTZ,
    ADD COLUMN consecutive_failures INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN consecutive_successes INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_status_changed_at TIMESTAMPTZ,
    ADD COLUMN check_claimed_by VARCHAR(120),
    ADD COLUMN check_claimed_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_monitored_services_consecutive_failures
        CHECK (consecutive_failures >= 0),
    ADD CONSTRAINT ck_monitored_services_consecutive_successes
        CHECK (consecutive_successes >= 0),
    ADD CONSTRAINT ck_monitored_services_claim_pair
        CHECK ((check_claimed_by IS NULL) = (check_claimed_until IS NULL));

UPDATE monitored_services
SET next_check_at = CURRENT_TIMESTAMP
WHERE is_active = TRUE
  AND deleted_at IS NULL;

CREATE INDEX idx_monitored_services_due
    ON monitored_services (next_check_at)
    WHERE is_active = TRUE AND deleted_at IS NULL;

CREATE TABLE health_check_results (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    service_id UUID NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    success BOOLEAN NOT NULL,
    degraded BOOLEAN NOT NULL,
    status_code INTEGER,
    response_time_milliseconds BIGINT NOT NULL,
    error_type VARCHAR(40),
    error_message VARCHAR(500),
    response_validation_passed BOOLEAN NOT NULL,
    response_excerpt VARCHAR(500),
    check_source VARCHAR(20) NOT NULL,
    status_before VARCHAR(32) NOT NULL,
    status_after VARCHAR(32) NOT NULL,
    applied_to_status BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_health_check_results_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_health_check_results_service
        FOREIGN KEY (service_id) REFERENCES monitored_services (id) ON DELETE CASCADE,
    CONSTRAINT ck_health_check_results_status_code
        CHECK (status_code IS NULL OR status_code BETWEEN 100 AND 599),
    CONSTRAINT ck_health_check_results_response_time
        CHECK (response_time_milliseconds >= 0),
    CONSTRAINT ck_health_check_results_error_type
        CHECK (error_type IS NULL OR error_type IN (
            'TIMEOUT', 'CONNECTION_REFUSED', 'DNS_FAILURE', 'SSL_ERROR',
            'UNEXPECTED_STATUS', 'CONTENT_MISMATCH', 'JSON_VALIDATION_FAILURE',
            'NETWORK_ERROR', 'UNKNOWN_ERROR'
        )),
    CONSTRAINT ck_health_check_results_source
        CHECK (check_source IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_health_check_results_status_before
        CHECK (status_before IN ('UNKNOWN', 'OPERATIONAL', 'DEGRADED', 'DOWN', 'PAUSED')),
    CONSTRAINT ck_health_check_results_status_after
        CHECK (status_after IN ('UNKNOWN', 'OPERATIONAL', 'DEGRADED', 'DOWN', 'PAUSED'))
);

CREATE INDEX idx_health_check_results_service_checked
    ON health_check_results (service_id, checked_at DESC);
CREATE INDEX idx_health_check_results_organization_checked
    ON health_check_results (organization_id, checked_at DESC);
