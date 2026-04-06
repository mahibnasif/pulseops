# ADR 002: PostgreSQL

- Status: Accepted
- Date: 2026-07-30

## Decision

PostgreSQL is the primary system of record for transactional and monitoring
data.

## Rationale

PulseOps benefits from foreign keys, partial indexes, row locking, JSON
metadata, mature operational tooling, and AWS RDS support. These capabilities
fit tenant isolation, scheduler claims, and duplicate-incident prevention.

## Consequences

Integration tests use PostgreSQL rather than H2. Flyway owns schema evolution.
High-volume check retention may eventually require partitioning or archival,
but no separate time-series database is introduced before evidence demands it.
