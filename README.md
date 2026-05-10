# PulseOps

PulseOps is a cloud-based service monitoring and incident-management platform
that checks application health, detects confirmed outages, alerts engineering
teams, and tracks incidents through resolution.

> Project status: Phase 9 (Security Hardening) is implemented. Notifications remain a
> planned milestone and are not represented as a completed feature.

## Architecture

```mermaid
flowchart LR
    Browser[React SPA] -->|REST + SSE| API[Spring Boot modular monolith]
    API --> DB[(PostgreSQL)]
    API --> Targets[HTTP/HTTPS targets]
    API --> Email[Email provider]
```

The application starts as a modular monolith. Domain modules will share one
deployment and database while keeping clear package and service boundaries.
This avoids distributed-system overhead before the product requires it.

See [docs/architecture.md](docs/architecture.md) for module boundaries and
runtime flows, and [docs/services.md](docs/services.md) for the Phase 4 service
lifecycle and authorization model.

## Technology stack

- Backend: Java 21, Spring Boot 4.1, Spring MVC, Spring Data JPA, Spring
  Security, Flyway, Actuator, Springdoc OpenAPI
- Frontend: React 19, TypeScript, Vite, React Router, TanStack Query, Recharts
- Database: PostgreSQL 17
- Testing: JUnit, Spring Boot Test, Testcontainers, Vitest, React Testing
  Library
- Infrastructure: Docker, Docker Compose, GitHub Actions
- Production target: Vercel, AWS ECS Fargate, and AWS RDS PostgreSQL

## Implemented capabilities

- Java and Node applications with reproducible dependency wrappers/lockfiles
- PostgreSQL configuration through environment variables
- Flyway-controlled schema history
- PostgreSQL-backed integration testing with Testcontainers
- Frontend unit test, type check, linter, and production build
- OpenAPI JSON and Swagger UI
- Health, readiness, and liveness endpoints
- Multi-stage, non-root application containers
- One-command local stack with health-gated startup
- User registration and login with normalized email addresses
- BCrypt password hashes and account status enforcement
- Short-lived JWT access tokens with issuer and audience validation
- Hashed, rotating refresh-token families with replay detection and revocation
- HttpOnly, SameSite refresh cookies and in-memory frontend access tokens
- Protected React routes, session restoration, and logout
- Stable API error envelopes with safe authentication messages
- Organization creation, discovery, settings, and persistent workspace switching
- Active memberships with `ADMIN`, `ENGINEER`, and `VIEWER` roles
- Backend-enforced tenant isolation and role authorization
- Hashed organization invitation tokens with email-bound acceptance
- Member role changes, removal, leave, and ownership-transfer workflows
- Responsive organization onboarding and team administration UI
- Organization-scoped HTTP/HTTPS service creation, editing, filtering,
  pausing, resuming, and soft deletion
- Role-aware service inventory, configuration form, and service details UI
- Bounded on-demand HTTP checks with status, text, JSON, and latency validation
- URL validation and private/reserved target blocking by default
- Database-claimed scheduled monitoring with bounded batches and expiring leases
- Persisted manual and scheduled health-check history
- Consecutive failure/recovery thresholds with degraded-latency status
- Responsive check-history and threshold-progress views
- Automatic, duplicate-safe incident creation when a service enters `DOWN`
- Incident filtering, manual declaration, assignment, comments, and timeline
- Role-aware incident workflow, resolution documentation, and reopening
- Service-linked incident history in the response workspace
- Authenticated, organization-scoped server-sent event streams
- Live dashboard, service, health-check, incident, and comment refreshes
- Event deduplication, session refresh, connection state, and exponential reconnects
- PostgreSQL-aggregated uptime, latency percentiles, and incident metrics
- Responsive Recharts analytics with UTC presets and custom date ranges
- Per-service reliability comparisons and live analytics invalidation
- Transport-pinned SSRF address validation with private/reserved range blocking
- Authentication rate limits with bounded, privacy-preserving keys
- Reviewed and integration-tested tenant and role access boundaries
- Random local secret generation and tracked-file secret scanning in CI
- Restrictive frontend browser security headers

## Planned MVP

The remaining MVP will add in-app notifications and delivery architecture.
See [docs/api.md](docs/api.md),
[docs/incidents.md](docs/incidents.md), and
[docs/analytics.md](docs/analytics.md).

## Prerequisites

- Git
- Java 21
- Node.js 24 and npm 11
- Docker Desktop with Linux containers

Maven does not need to be installed globally. The repository includes
`backend\mvnw.cmd`.

## Local setup

Run from `D:\Code\vibing\pulseops` in Windows PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\setup.ps1
docker compose up --build
```

When all health checks pass:

- Frontend: <http://localhost:5173>
- Backend health: <http://localhost:8080/actuator/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Stop the stack without deleting database data:

```powershell
docker compose down
```

Delete local database data only when a clean database is intentionally needed:

```powershell
docker compose down --volumes
```

## Environment variables

| Variable | Local default | Purpose |
|---|---|---|
| `POSTGRES_DB` | `pulseops` | Local database name |
| `POSTGRES_USER` | `pulseops` | Local database user |
| `POSTGRES_PASSWORD` | generated; required | Local database password |
| `POSTGRES_PORT` | `5432` | Host PostgreSQL port |
| `BACKEND_PORT` | `8080` | Host backend port |
| `FRONTEND_PORT` | `5173` | Host frontend port |
| `DATABASE_URL` | local JDBC URL | Direct backend JDBC URL |
| `DATABASE_USERNAME` | `pulseops` | Direct backend database user |
| `DATABASE_PASSWORD` | none; required | Direct backend database password |
| `JWT_SECRET` | none; required | Base64-encoded signing key of at least 256 bits |
| `JWT_ISSUER` | `pulseops` | Required JWT issuer |
| `JWT_AUDIENCE` | `pulseops-web` | Required JWT audience |
| `JWT_ACCESS_TOKEN_TTL` | `15m` | Access-token lifetime |
| `JWT_REFRESH_TOKEN_TTL` | `30d` | Refresh-session lifetime |
| `REFRESH_COOKIE_SECURE` | `false` in Compose | Require HTTPS for refresh cookie |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Credentialed browser origins |
| `OPENAPI_ENABLED` | `true` locally | Enable OpenAPI JSON and Swagger UI |
| `AUTH_RATE_LIMIT_ENABLED` | `true` | Enable authentication endpoint throttling |
| `AUTH_RATE_LIMIT_LOGIN_ATTEMPTS` | `10` | Login attempts per client and identity window |
| `AUTH_RATE_LIMIT_LOGIN_WINDOW` | `5m` | Login rate-limit window |
| `AUTH_RATE_LIMIT_REGISTRATION_ATTEMPTS` | `5` | Registrations per client window |
| `AUTH_RATE_LIMIT_REGISTRATION_WINDOW` | `1h` | Registration rate-limit window |
| `AUTH_RATE_LIMIT_REFRESH_ATTEMPTS` | `30` | Refresh attempts per client window |
| `AUTH_RATE_LIMIT_REFRESH_WINDOW` | `5m` | Refresh rate-limit window |
| `AUTH_RATE_LIMIT_MAX_TRACKED_KEYS` | `10000` | Bound for in-memory limiter keys |
| `AUTH_RATE_LIMIT_TRUST_PROXY_CLIENT_IP` | `true` in Compose | Trust proxy-overwritten `X-Real-IP` |
| `MONITORING_ALLOW_PRIVATE_TARGETS` | `false` | Allow checks to private network destinations |
| `MONITORING_MAX_RESPONSE_BYTES` | `65536` | Maximum response bytes read by a manual check |
| `MONITORING_SCHEDULER_ENABLED` | `true` | Enable the scheduled monitoring worker |
| `MONITORING_POLL_INTERVAL_MILLISECONDS` | `5000` | Delay between due-service claim batches |
| `MONITORING_BATCH_SIZE` | `10` | Maximum services claimed per scheduler pass |
| `MONITORING_CLAIM_LEASE_SECONDS` | `120` | Time before an unfinished claim can be recovered |
| `LIVE_EVENT_HEARTBEAT_MILLISECONDS` | `15000` | Interval between SSE keep-alive comments |

The setup script creates an ignored `.env` and independently generates a
256-bit JWT key and random local database password.
Production secrets must come from a managed secret store, never committed files.
Production must set `REFRESH_COOKIE_SECURE=true` and
`MONITORING_ALLOW_PRIVATE_TARGETS=false`. Only enable private targets
temporarily when testing services on a controlled local Docker network.

## Run tests

Backend tests require Docker because Testcontainers starts PostgreSQL:

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops\backend'
.\mvnw.cmd test
```

Frontend checks:

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops\frontend'
npm.cmd ci
npm.cmd run lint
npm.cmd test
npm.cmd run build
```

Run the combined verification:

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
```

## Database design

Flyway owns all schema changes. Hibernate validates mappings but never changes
the schema. Phase 6 adds tenant-owned incidents, comments, and an immutable
response timeline. See
[docs/database.md](docs/database.md).

## Security

Registration, login, refresh, and logout are public API operations. All other
application routes require a valid bearer JWT. Organization-scoped operations
also require an active membership and the appropriate role. The manual checker
rejects unsafe URL forms, disables redirects, pins validated DNS answers at
connection time, limits time and response size, and blocks private/reserved
targets by default. Authentication endpoints are rate limited, and tracked
files are checked for common secret signatures in CI.
See
[docs/security.md](docs/security.md).

## Deployment

The production target uses a Vercel-hosted SPA, an ECS Fargate backend, and RDS
PostgreSQL across private subnets. The current Docker Compose stack is intended
for development, not production. See [docs/deployment.md](docs/deployment.md).

## Screenshots and demo

Screenshots and a hosted demo are intentionally deferred until the MVP user
journey exists. Publishing scaffold screenshots would misrepresent project
progress.

## Known limitations

- Invitation email delivery is not implemented; matching registered users see
  pending invitations in the application.
- Password reset and email verification delivery remain future work.
- Email and in-app notifications are not implemented.
- Incident notifications and external delivery are not implemented yet. The
  live-event protocol reserves `NOTIFICATION_CREATED` for that phase.
- The live-event broker is in memory and reaches clients connected to the same
  backend instance. A shared broker or load-balancer affinity is required
  before horizontally scaling the backend.
- The scheduler processes a bounded batch sequentially per application
  instance. Database claims support multiple instances, but higher-throughput
  worker pools are deferred until measurements justify them.
- TCP and JSON API service types are reserved for later phases; Phase 4 accepts
  HTTP and HTTPS services only.
- Authentication rate limits are instance-local; horizontally scaled
  deployments require shared or edge rate limiting.
- Uptime metrics are sampled estimates, not continuous SLA measurements. The
  exact calculation contract is documented in [docs/analytics.md](docs/analytics.md).
- npm currently reports a React Router advisory affecting RSC action handling.
  PulseOps is a client-only SPA and does not use RSC or server actions; the
  exception is tracked in [docs/security.md](docs/security.md).

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). The project uses short-lived feature
branches and Conventional Commits.

## License

PulseOps is available under the [MIT License](LICENSE).
