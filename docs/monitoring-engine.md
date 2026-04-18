# Monitoring engine

## Phase 4 manual checks

An administrator or engineer can request an immediate check for an active
HTTP/HTTPS service. The checker validates the destination, disables redirects,
applies the configured timeout and response-size cap, and evaluates status,
optional text, optional simple JSON path/value, and degraded-latency
expectations.

The result is returned directly and the service's latest check/success/failure
timestamp is updated. It is intentionally marked `affectsServiceStatus=false`.
Phase 4 does not persist check history, advance consecutive counters, schedule
work, or open incidents.

## State model

A service begins as `UNKNOWN`. In Phase 5, a successful scheduled check makes
it `OPERATIONAL` or
`DEGRADED` depending on latency. Consecutive failures must reach the configured
failure threshold before the service becomes `DOWN`. A down service must reach
its recovery threshold before returning to `OPERATIONAL`.

One transient failure therefore remains a recorded failed check without
immediately declaring an outage.

## Planned Phase 5 scheduled flow

1. Select active services whose `next_check_at` is due.
2. Atomically claim a bounded batch with a worker ID and lease expiry.
3. Validate the URL and resolved target against the SSRF policy.
4. Perform the request with strict time and body limits.
5. Persist the check result.
6. Update consecutive counters with optimistic concurrency control.
7. Calculate the new service state.
8. Create or update an incident within the transition transaction.
9. Persist notifications and publish an organization-scoped live event.
10. Set the next check time and release the claim.

## Availability

MVP uptime is a sampled estimate:

```text
successful completed checks / all completed checks * 100
```

It does not prove continuous availability between observations and will be
labeled accordingly. Missing or excessively delayed samples are reported
separately rather than assumed healthy.
