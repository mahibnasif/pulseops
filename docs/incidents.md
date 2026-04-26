# Incident management

## Creation

PulseOps supports two incident sources:

- `AUTOMATIC_MONITORING` is created when an applied health-check transition
  moves a service into `DOWN`.
- `MANUAL` is declared by an administrator or engineer for an organization
  service.

Automatic creation shares the health-result transaction. If incident
persistence fails, the service transition and check result roll back as well.
The service row lock and a partial unique database index guarantee that a
service has at most one unresolved automatic incident.

## Workflow

Active incidents move through:

```text
OPEN -> ACKNOWLEDGED -> INVESTIGATING -> IDENTIFIED -> MONITORING
```

The workflow allows practical forward movement and controlled returns from
`IDENTIFIED` or `MONITORING` to investigation. Resolution is a separate
operation requiring a summary; it sets `RESOLVED` and `resolvedAt` together.
A resolved incident can be reopened to `OPEN`.

Service recovery does not resolve an incident. For an automatic outage,
recovery moves the record to `MONITORING` and records `SERVICE_RECOVERED`, so a
responder can verify stability and document the outcome.

## Collaboration and audit history

An incident can be assigned to any active organization member. Administrators
and engineers can change severity and status, assign responders, add comments,
resolve, and reopen. Viewers can read the incident, comments, and timeline but
cannot mutate it.

Every meaningful response action appends a timeline event with its actor,
timestamp, message, and old/new values where applicable. Comments remain
separate response notes and also create a timeline marker.

## Tenant boundary

Every incident lookup starts with an active membership check and includes the
organization ID in resource queries. A manual incident can reference only an
active service in the same organization, and an assignee must be an active
member of that organization.
