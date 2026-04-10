# Database

## Ownership

PostgreSQL is the source of truth. Flyway migrations under
`backend/src/main/resources/db/migration` own the schema. Hibernate uses
`ddl-auto: validate`; it must never create or update production tables.

## Planned relationships

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : owns
    USERS ||--o{ ORGANIZATION_MEMBERSHIPS : joins
    ORGANIZATIONS ||--o{ ORGANIZATION_MEMBERSHIPS : contains
    ORGANIZATIONS ||--o{ MONITORED_SERVICES : owns
    MONITORED_SERVICES ||--o{ HEALTH_CHECK_RESULTS : produces
    MONITORED_SERVICES ||--o{ INCIDENTS : triggers
    INCIDENTS ||--o{ INCIDENT_COMMENTS : contains
    INCIDENTS ||--o{ INCIDENT_TIMELINE_EVENTS : records
    USERS ||--o{ NOTIFICATIONS : receives
```

Phase 2 introduces `users` and `refresh_tokens`. User emails are normalized and
unique. Passwords are stored only as BCrypt hashes. Refresh-token values are
represented only by unique SHA-256 hashes, with family IDs supporting rotation,
revocation, and replay response.

The remaining detailed columns are introduced with their owning feature. UUIDs
are application-generated. Every tenant-owned table carries an organization
reference where doing so strengthens authorization and integrity.

## Migration rules

- Never edit a migration after it has been merged and applied.
- Add a new forward migration for every schema change.
- Use explicit constraints and indexes.
- Avoid long table rewrites in production migrations.
- Test migrations against PostgreSQL through Testcontainers.
- Name migrations `V<version>__<description>.sql`.

`V1` establishes the Flyway baseline. `V2` creates the authentication tables,
constraints, foreign key, and refresh-token lookup indexes.
