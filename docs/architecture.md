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

Phase 4 implements the `monitoredservice` module and the immediate HTTP checker.
Controllers handle organization-scoped DTOs; the application service enforces
membership, roles, lifecycle, and duplicate-name rules; the repository owns
tenant-filtered persistence; and the checker performs one bounded request.
Manual checks update last-check timestamps but do not invoke the future
threshold state machine.

## Authentication flow

The SPA sends credentials only to the authentication API. The backend verifies
BCrypt hashes and returns a signed, short-lived access JWT while setting an
opaque refresh token in an HttpOnly cookie. PostgreSQL stores only the refresh
token hash. Refresh requests rotate the opaque token; bearer JWTs authorize
protected API requests without server-side access-token sessions.

## Organization boundary

An organization is the tenant root. Creation atomically writes the organization
and an `ADMIN` membership for its owner. Every organization query first resolves
an active membership from the JWT subject; administrative commands then verify
the membership role. Ownership is an additional invariant for transfer and
leave operations, not a hidden fourth role.

The SPA loads all active memberships after authentication and stores only the
selected organization ID in local storage. Organization details and roles are
always refreshed from the API. Service queries accept the selected organization
as context and independently enforce the same backend membership boundary.
Future incidents, analytics, audit records, and live-event subscriptions must
do the same.

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
