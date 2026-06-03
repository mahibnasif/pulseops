# Service management

## Scope

Phase 4 provides organization-scoped configuration and on-demand validation for
HTTP and HTTPS endpoints. TCP and specialized JSON API service types remain
reserved for later work.

Each service defines:

- a tenant-unique active name and optional description;
- protocol, absolute URL, and `GET` or `HEAD`;
- expected HTTP status;
- optional response-text containment;
- an optional paired simple JSON path/value expectation;
- request timeout and maximum degraded latency;
- scheduled interval, failure threshold, and recovery threshold; and
- lifecycle status and latest check timestamps.

## Roles

Active organization members can view the service inventory and details.
Administrators can create, update, pause, resume, and soft-delete services.
Administrators and engineers can run manual checks. Viewers have read-only
access.

These rules are enforced in the backend. Hidden or disabled frontend controls
are only a usability aid.

## Lifecycle

New services start active with `UNKNOWN` status. Pausing makes a service
inactive and `PAUSED`. Resuming makes it active and returns it to `UNKNOWN`;
the next completed checks establish a threshold-based status.
Deletion is soft deletion, preserving the row for future audit relationships
while excluding it from active resource endpoints.

## Manual checks

A manual check performs one bounded request and evaluates the configured
expectations. It persists the sample, updates the latest timestamps and
consecutive counters, and applies the same status transition rules as scheduled
checks. A manual result that confirms the failure threshold can therefore open
the same duplicate-safe automatic incident as a scheduled result.

The application blocks private and reserved destinations by default, including
local Compose. Isolated E2E and portfolio scripts explicitly enable private
targets only inside their disposable Docker networks. The checker pins the
validated DNS address set to the outbound transport to close the
validation/connect rebinding window. See [security.md](security.md).

See [api.md](api.md), [database.md](database.md), and
[monitoring-engine.md](monitoring-engine.md) for the HTTP contract, persistence
model, and scheduled-monitoring boundary.
