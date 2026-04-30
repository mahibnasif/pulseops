# Testing

## Layers

- Unit tests cover state transitions, authorization, validation, incident
  rules, notification generation, and analytics formulas.
- Spring integration tests use PostgreSQL through Testcontainers for mappings,
  Flyway, repositories, security, and API flows.
- Frontend component tests use Vitest, jsdom, and React Testing Library.
- Playwright will cover the full MVP journey after the corresponding features
  exist.

## Verification command

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops'
.\scripts\verify.ps1
```

Backend tests require a running Docker engine. Tests must create their own
isolated state and cannot depend on the developer's Compose database.

Phase 2 backend integration tests cover registration, normalized emails, BCrypt
storage, protected routes, safe login failures, refresh rotation and replay,
logout, and duplicate registration. Frontend tests cover public routing,
protected-route redirects, login, authenticated rendering, and registration
confirmation validation.

Phase 3 backend integration tests cover owner membership creation, slug
uniqueness, cross-tenant denial, viewer authorization, invitation hashing and
email binding, role changes, removal, leave rules, and ownership transfer.
Frontend tests cover empty-state creation, persistent organization switching,
pending invitation acceptance, and read-only viewer settings. The integration
base owns one PostgreSQL Testcontainer for the full JVM so Spring's cached
application context never points at a container stopped between test classes.

Phase 4 backend integration tests cover service CRUD, validation,
case-insensitive tenant-scoped uniqueness, cross-tenant isolation, role
authorization, pause/resume/delete lifecycle, list filters, and manual-check
result semantics. Focused checker tests cover status, text, JSON, latency,
response caps, private destinations, redirects, and safe network failures.
Frontend tests cover the service inventory, creation-to-details flow, and
engineer manual checks without administrator controls.

Phase 5 tests cover failure hysteresis, recovery hysteresis, degradation,
transient failures, scheduled next-run calculation, paused-result handling,
atomic due-service claims, stale-worker rejection, persisted transition
history, and the Flyway V5 schema. Frontend coverage verifies that manual
results refresh persisted history.

Phase 6 integration tests cover duplicate-safe automatic creation, recovery
timeline behavior, the complete manual workflow, assignment validation,
comments, resolution, reopening, viewer authorization, and cross-tenant
isolation. Frontend tests cover the filtered register, engineer response
controls, timeline rendering, and the viewer's read-only workspace. The
Phase 7 integration tests cover JWT enforcement, membership isolation,
post-commit SSE delivery, and service, health-check, incident, and comment
events. Frontend coverage verifies authenticated headers, token-safe URLs,
connection-state recovery, authoritative cache refresh, event deduplication,
and cross-organization event rejection. The current gate contains 28 backend
tests and 17 frontend tests.

Coverage is used to find untested risk, not as a vanity target. Security and
concurrency paths require explicit tests even if aggregate line coverage is
already high.
