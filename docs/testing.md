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

Coverage is used to find untested risk, not as a vanity target. Security and
concurrency paths require explicit tests even if aggregate line coverage is
already high.
