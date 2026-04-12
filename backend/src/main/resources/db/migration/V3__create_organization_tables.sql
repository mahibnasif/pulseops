CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(80) NOT NULL,
    description VARCHAR(1000),
    owner_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_organizations_slug UNIQUE (slug),
    CONSTRAINT fk_organizations_owner
        FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT ck_organizations_slug
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE organization_memberships (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    membership_status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_memberships_organization_user
        UNIQUE (organization_id, user_id),
    CONSTRAINT fk_memberships_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_memberships_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_memberships_role
        CHECK (role IN ('ADMIN', 'ENGINEER', 'VIEWER')),
    CONSTRAINT ck_memberships_status
        CHECK (membership_status IN ('ACTIVE', 'LEFT', 'REMOVED'))
);

CREATE TABLE organization_invitations (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    role VARCHAR(32) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_invitations_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_invitations_created_by
        FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_invitations_role
        CHECK (role IN ('ADMIN', 'ENGINEER', 'VIEWER'))
);

CREATE INDEX idx_organizations_owner_id
    ON organizations (owner_id);
CREATE INDEX idx_memberships_user_status
    ON organization_memberships (user_id, membership_status);
CREATE INDEX idx_memberships_organization_status
    ON organization_memberships (organization_id, membership_status);
CREATE INDEX idx_invitations_organization_email
    ON organization_invitations (organization_id, email);
CREATE INDEX idx_invitations_email_pending
    ON organization_invitations (email, expires_at)
    WHERE accepted_at IS NULL;
