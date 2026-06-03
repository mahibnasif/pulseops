# Testing

## Layers

- Unit tests cover state transitions, authorization, validation, incident
  rules, production configuration, and analytics formulas.
- Spring integration tests use PostgreSQL through Testcontainers for mappings,
  Flyway, repositories, security, and API flows.
- Frontend component tests use Vitest, jsdom, and React Testing Library.
- The native Node.js test runner verifies the deterministic failure simulator.
- Playwright drives the full MVP journey against an isolated production-style
  Compose stack.
- A dependency-free Node.js runner provides a bounded health-endpoint load
  smoke with explicit latency and error-rate thresholds.

## Verification command

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
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
and cross-organization event rejection.

Phase 8 PostgreSQL integration coverage verifies sample uptime, averages, P50,
P95, incident cohorts, severity groups, service rollups, null behavior,
bounded-range validation, and tenant isolation. Frontend coverage verifies
metric rendering, chart sections, preset date requests, and custom-range
validation.

Phase 9 adds SSRF address-policy, authentication rate-limit, JWT configuration,
authorization-boundary, secret-scanning, and browser-header coverage. Phase 10
adds a PostgreSQL regression for unfiltered service listings, three
failure-simulator tests, and one Playwright journey covering registration,
organization creation, monitored-service setup, outage thresholds, automatic
incident creation, assignment, response notes, recovery, and resolution. The
current Maven gate executes 87 backend cases from 50 JUnit test methods, with
20 frontend component tests plus the simulator and browser suites.

Coverage is used to find untested risk, not as a vanity target. Security and
concurrency paths require explicit tests even if aggregate line coverage is
already high.

## Failure simulation

The optional `demo-service` Compose profile exposes deterministic targets on
port `8091` by default:

- `/demo/healthy` always returns HTTP 200.
- `/demo/failing` always returns HTTP 503.
- `/demo/flaky` alternates between HTTP 200 and 503.
- `/demo/slow?delay=1000` returns after a bounded delay.
- `/demo/json/healthy` and `/demo/json/unhealthy` exercise JSON expectations.
- `/demo/controlled` returns the state last written to `POST /demo/control`.

The controlled endpoint exists for automated tests. The profile is not started
by the normal `docker compose up` command.

Run its unit tests:

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops\demo-service'
npm.cmd test
```

## End-to-end journey

Install Chromium once, then run the self-cleaning isolated stack:

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops\frontend'
npx.cmd playwright install chromium
Set-Location -LiteralPath 'D:\Code\vibing\pulseops'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e.ps1
```

The runner uses the Compose project `pulseops-e2e` and alternate loopback
ports, so it does not reuse the developer database. It enables private
monitoring targets only inside that controlled test network, prints container
logs on failure, and always removes its containers and volume.

## Load smoke

With the backend running, execute:

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops'
node .\scripts\load-test.mjs
```

The defaults send 250 requests to `/actuator/health` with concurrency 20 and
fail if p95 exceeds 750 milliseconds or more than 1% of requests fail.

| Variable | Default |
|---|---:|
| `LOAD_BASE_URL` | `http://127.0.0.1:8080` |
| `LOAD_PATH` | `/actuator/health` |
| `LOAD_REQUESTS` | `250` |
| `LOAD_CONCURRENCY` | `20` |
| `LOAD_TIMEOUT_MS` | `5000` |
| `LOAD_EXPECTED_STATUS` | `200` |
| `LOAD_MAX_P95_MS` | `750` |
| `LOAD_MAX_ERROR_RATE` | `0.01` |

This is a repeatable regression smoke, not a production capacity benchmark.
Capacity claims require a production-like environment, representative
authenticated scenarios, sustained arrival rates, database sizing, and
observability review.
