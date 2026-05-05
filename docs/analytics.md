# Analytics

Phase 8 provides organization-scoped reliability analytics for bounded UTC
date ranges. The backend performs aggregation in PostgreSQL and returns compact
DTOs suitable for summary cards, charts, and a service comparison table.

## Range contract

- `from` is inclusive and `to` is exclusive: `[from, to)`.
- Both values are ISO 8601 instants.
- The default range is the 30 days ending at request time.
- The maximum range is 366 days.
- Ranges of 48 hours or less use hourly trend buckets; longer ranges use daily
  buckets.
- Bucket boundaries use UTC. The browser may format labels for readability,
  but it does not change aggregation boundaries.

The frontend offers 24-hour, 7-day, 30-day, and 90-day presets plus inclusive
calendar-date inputs. A selected "through" date is converted to the next UTC
midnight for the exclusive API boundary.

## Reliability formulas

PulseOps currently estimates availability from health-check observations:

```text
sample uptime percentage =
  successful health-check samples / all health-check samples * 100
```

A successful degraded check counts as available because the endpoint still met
its correctness expectations. Failed checks count as unavailable. A time bucket
or service with no checks returns `null` uptime; it does not imply either 0% or
100% availability.

Response-time metrics use all health-check observations in the selected range:

```text
average response time = arithmetic mean(response_time_milliseconds)
P50 = continuous 50th percentile(response_time_milliseconds)
P95 = continuous 95th percentile(response_time_milliseconds)
```

Failure durations, including timeouts, are deliberately retained because they
describe user-observed monitoring latency. PostgreSQL `PERCENTILE_CONT`
interpolates between samples when the requested percentile falls between rows.

This is an observation-based approximation, not continuous SLA accounting.
Check frequency affects sample density, intervals between checks are not
reconstructed as exact uptime, and monitoring gaps are not treated as success.
A later reporting model could construct status intervals for contractual SLO
calculations.

## Incident formulas

Incidents form a cohort when `detected_at` is inside the selected range:

```text
MTTA = average(acknowledged_at - detected_at)
       for cohort incidents that were acknowledged

MTTR = average(resolved_at - detected_at)
       for cohort incidents that were resolved

longest duration = max(resolved_at - detected_at)
                   or, for active incidents, range end - detected_at
```

Incidents without the relevant lifecycle timestamp are excluded from that
average instead of being treated as zero. Active and severity counts reflect
the current state of incidents in the detected cohort. The incident trend
groups the cohort by detection time; its resolved value means those detected
incidents are now resolved, not that resolution occurred in that bucket.

## Service status and isolation

Service status counts are a current snapshot of non-deleted services; they are
not reconstructed historical states. Per-service reliability combines that
current status with checks and detected incidents from the selected range.

Every analytics request authenticates the JWT subject and requires an active
membership before any aggregate query runs. Every SQL statement also filters
by `organization_id`, preserving defense in depth. Cross-organization requests
use the same non-disclosing `ORGANIZATION_NOT_FOUND` behavior as other tenant
resources.

## Live updates and scaling

Health-check, service, and incident events invalidate the active analytics
query. Event payloads remain hints; the REST snapshot is authoritative.

The existing organization/time indexes support the bounded aggregation model.
If history volume makes on-demand percentiles too expensive, the next step is
measured rollup tables or materialized views—not client-side raw-history
processing. Cache policy and retention should be chosen from observed query
plans and product reporting requirements.
