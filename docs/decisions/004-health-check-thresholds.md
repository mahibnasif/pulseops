# ADR 004: Health-check thresholds

- Status: Accepted for Phase 5
- Date: 2026-07-30

## Decision

Service outage and recovery transitions require configurable consecutive
failure and success thresholds.

## Rationale

Networks produce transient errors. Declaring an outage after one sample causes
alert fatigue, while recovery after one success can make status flap. Separate
thresholds provide simple, explainable hysteresis.

## Consequences

Every completed check updates consecutive counters atomically. Manual and
scheduled checks use the same transition service. Tests must cover failure,
recovery, degradation, concurrency, and restart behavior.
