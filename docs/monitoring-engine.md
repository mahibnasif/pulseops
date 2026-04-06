# Monitoring engine

## State model

A service begins as `UNKNOWN`. A successful check makes it `OPERATIONAL` or
`DEGRADED` depending on latency. Consecutive failures must reach the configured
failure threshold before the service becomes `DOWN`. A down service must reach
its recovery threshold before returning to `OPERATIONAL`.

One transient failure therefore remains a recorded failed check without
immediately declaring an outage.

## Scheduled flow

1. Select active services whose `next_check_at` is due.
2. Atomically claim a bounded batch with a worker ID and lease expiry.
3. Validate the URL and resolved target against the SSRF policy.
4. Perform the request with strict time and body limits.
5. Persist the check result.
6. Update consecutive counters with optimistic concurrency control.
7. calculate the new service state.
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
