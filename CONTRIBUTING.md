# Contributing to PulseOps

## Workflow

1. Start from an up-to-date `main` branch.
2. Create a focused branch such as `feature/authentication` or
   `fix/duplicate-incidents`.
3. Keep changes small enough to review.
4. Add or update tests and documentation.
5. Run `.\scripts\verify.ps1`.
6. Open a pull request and describe behavior, risks, and verification.

## Commit messages

Use Conventional Commits:

```text
feat(monitoring): add scheduled HTTP health checks
fix(security): block private health-check targets
test(incidents): prevent duplicate active incidents
docs(architecture): describe scheduler claim leases
```

Each commit should represent one coherent, verified change. Do not commit
secrets, generated build output, IDE settings, or local `.env` files.

## Code expectations

- Controllers handle HTTP concerns; application services own business logic.
- JPA entities are not returned directly from APIs.
- Organization access is checked on the backend for every scoped operation.
- Flyway migrations are immutable after they have been shared.
- User-controlled monitoring targets must pass SSRF validation.
- Tests should demonstrate behavior and failure cases, not chase coverage
  percentages.

## Pull request checklist

- [ ] Scope is explained.
- [ ] Backend authorization is enforced.
- [ ] Inputs and error responses are validated.
- [ ] Tests pass locally.
- [ ] Database migrations are backward-aware.
- [ ] Logs contain no secrets or tokens.
- [ ] Documentation reflects user-visible or architectural changes.
