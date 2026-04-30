# Live events

## Transport and endpoint

PulseOps uses server-sent events because Phase 7 updates flow from the backend
to the browser while commands continue to use REST.

```text
GET /api/v1/organizations/{organizationId}/events
Accept: text/event-stream, application/json
Authorization: Bearer <access token>
```

The controller validates the JWT, active account, and active organization
membership before registering the connection. Non-members receive the same
tenant-safe not-found response used by other organization APIs.

## Event envelope

Every `pulseops-update` event carries:

```json
{
  "id": "event-uuid",
  "organizationId": "organization-uuid",
  "type": "SERVICE_STATUS_CHANGED",
  "entityType": "service",
  "entityId": "entity-uuid",
  "occurredAt": "2026-07-30T12:00:00Z"
}
```

Supported types are:

- `SERVICE_CREATED`, `SERVICE_UPDATED`, `SERVICE_DELETED`
- `SERVICE_STATUS_CHANGED`, `HEALTH_CHECK_RECORDED`
- `INCIDENT_CREATED`, `INCIDENT_UPDATED`, `INCIDENT_COMMENT_ADDED`
- `NOTIFICATION_CREATED`, reserved for the notification phase

The initial `pulseops-ready` event uses `CONNECTION_READY`. Heartbeats are SSE
comments and carry no domain data.

## Consistency model

Events are invalidation hints. They intentionally do not duplicate full
service, health-check, incident, or comment DTOs. Application services publish
inside their database transaction, and broadcasting occurs only after commit.
The client then invalidates the affected TanStack Query keys and reloads
authoritative REST data.

This model also handles missed events: every successful connection or
reconnection performs an organization-scoped cache resynchronization.

## Client recovery

The client:

1. opens one stream for the selected organization;
2. rejects events whose organization ID does not match;
3. remembers the latest 500 event IDs and ignores duplicates;
4. sends `Last-Event-ID` when reconnecting for future replay compatibility;
5. retries with exponential delays from one to 30 seconds;
6. reports connecting, live, reconnecting, or offline state;
7. rotates the refresh cookie after an expired access-token response; and
8. aborts the old stream immediately when the organization or session changes.

Phase 7 does not persist an event log, so `Last-Event-ID` is not replayed by the
server yet. Reconnection resynchronization prevents stale UI despite that
limitation.

## Capacity and scaling

Each backend instance permits up to 100 concurrent streams per organization,
times each stream out when its access token expires (with a 30-minute upper
bound), and sends 15-second keep-alives. Failed, completed, and timed-out
emitters are removed.

The broker is intentionally in memory for the modular-monolith MVP. Before
running multiple backend tasks, add a shared pub/sub adapter (for example
Redis, PostgreSQL `LISTEN/NOTIFY`, or a managed message service) so events
published on one task reach streams connected to another.
