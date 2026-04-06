# Testing

## Layers

- Unit tests cover state transitions, authorization, validation, incident
  rules, notification generation, and analytics formulas.
- Spring integration tests use PostgreSQL through Testcontainers for mappings,
  Flyway, repositories, security, and API flows.
- Frontend component tests use Vitest, jsdom, and React Testing Library.
- Playwright will cover the full MVP journey after the corresponding features
  exist.

## Foundation commands

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops'
.\scripts\verify.ps1
```

Backend tests require a running Docker engine. Tests must create their own
isolated state and cannot depend on the developer's Compose database.

Coverage is used to find untested risk, not as a vanity target. Security and
concurrency paths require explicit tests even if aggregate line coverage is
already high.
