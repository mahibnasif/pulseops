# Monitoring engine

## Check execution

An administrator or engineer can request an immediate check for an active
HTTP/HTTPS service. The checker validates the destination, disables redirects,
applies the configured timeout and response-size cap, and evaluates status,
optional text, optional simple JSON path/value, and degraded-latency
expectations.

Manual and scheduled checks use the same execution client and completion
transaction. Every accepted completion persists a history row and updates the
service's latest check/success/failure timestamp, consecutive counters, and
status. Check history records the source, status before and after, and whether
the result was applied.

## State model

A service begins as `UNKNOWN`. A successful check makes it `OPERATIONAL` or
`DEGRADED` depending on latency. Consecutive failures must reach the configured
failure threshold before the service becomes `DOWN`. A down service must reach
its recovery threshold before returning to `OPERATIONAL`.

One transient failure therefore remains a recorded failed check without
immediately declaring an outage.

Successful degraded checks recover a down service to `DEGRADED` once the
recovery threshold is met. Pausing and resuming reset counters; resuming sets
the status to `UNKNOWN` and makes the service immediately due.

## Scheduled flow

1. Select active services whose `next_check_at` is due.
2. Atomically claim a bounded batch with a worker ID and lease expiry using
   `FOR UPDATE SKIP LOCKED`.
3. Validate the URL and resolved target against the SSRF policy.
4. Perform the request with strict time and body limits.
5. Persist the check result.
6. Lock the service and verify that the completing worker still owns its claim.
7. Update consecutive counters and calculate the new service state.
8. Set the next due time and release the claim in the same transaction.

Network requests happen outside database transactions. If an application stops
after claiming work, another instance can reclaim it after the lease expires.
A stale completion cannot apply because its worker ID no longer matches.

The scheduler currently executes each claimed batch sequentially. This is a
deliberate bounded MVP design; parallel executors require measured capacity,
back-pressure, and shutdown behavior.

## Incident integration

An applied transition into `DOWN` creates a `HIGH` severity automatic incident
in the same transaction as the health result and service state. A service lock
serializes completions, and PostgreSQL also enforces one unresolved automatic
incident per service. Repeated failures while already down do not create
duplicates.

When a down service reaches its recovery threshold, the automatic incident
moves to `MONITORING` and records a `SERVICE_RECOVERED` timeline event. A human
still documents the resolution and closes the incident. Later phases will
persist notifications and publish organization-scoped live events for these
changes.

## Availability

MVP uptime is a sampled estimate:

```text
successful completed checks / all completed checks * 100
```

It does not prove continuous availability between observations and will be
labeled accordingly. Missing or excessively delayed samples are reported
separately rather than assumed healthy.
