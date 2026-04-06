# Architecture

## Context

PulseOps monitors user-configured HTTP/HTTPS services and coordinates the
incident response that follows a confirmed outage. It is multi-tenant:
organizations own services, incidents, memberships, and analytics.

## Modular monolith

The first production version is one Spring Boot deployment organized into:

- `auth` and `user`
- `organization` and `membership`
- `monitoredservice`
- `healthcheck`
- `incident`
- `notification`
- `analytics`
- `audit`
- `common` and `config`

Modules may call another module's application service or publish an internal
domain event. They should not reach directly into another module's repository.
This preserves boundaries without network calls or distributed transactions.

## Runtime responsibilities

The React SPA issues versioned REST commands and queries. Server-sent events
will notify the client that organization-scoped data changed; TanStack Query
will then refresh authoritative state.

PostgreSQL is the system of record. Flyway owns schema changes. The monitoring
scheduler claims due service rows in bounded batches, performs checks outside
long database transactions, and commits results through state-transition
services.

## Reliability boundaries

- Requests use correlation IDs.
- Health checks use finite connect/read timeouts and bounded response bodies.
- A database claim lease prevents overlapping checks and survives worker loss.
- Optimistic locking protects status counters.
- A partial unique index prevents duplicate active automatic incidents.
- Notification records are persisted before asynchronous delivery.

## Production topology

The target topology is a Vercel SPA, ECS Fargate tasks in private subnets, an
Application Load Balancer, and RDS PostgreSQL. The backend needs controlled
egress for health checks. RDS is never publicly reachable.
