# ADR 003: JWT authentication

- Status: Accepted for Phase 2
- Date: 2026-07-30

## Decision

Use short-lived JWT access tokens and rotating opaque refresh tokens whose
hashes are stored in PostgreSQL.

## Rationale

Access tokens support stateless API authorization while stored refresh-token
families provide logout, rotation, revocation, and reuse detection.

## Consequences

JWT claims remain minimal and cannot replace current database membership
checks. The frontend keeps access tokens in memory where practical. Refresh
transport uses secure HTTP-only cookies when the deployment topology permits.
Key rotation and clock skew require explicit configuration and tests.
