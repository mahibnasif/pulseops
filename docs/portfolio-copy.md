# Portfolio copy

Use the language below as a starting point and keep only claims that remain true
when the application is published. Replace bracketed placeholders before
posting.

## One-line project description

PulseOps is a multi-tenant service-monitoring and incident-management platform
that confirms repeated HTTP failures, opens duplicate-safe incidents, streams
live updates, and reports sampled reliability metrics.

## Portfolio website description

PulseOps is a production-style full-stack platform for monitoring HTTP and
HTTPS services and coordinating incident response. Engineering teams create
organization workspaces, configure health expectations and outage thresholds,
review persisted check history, and respond to incidents that PulseOps creates
after confirmed failures.

The application combines a React and TypeScript interface with a Java 21 Spring
Boot modular monolith and PostgreSQL. It includes rotating refresh tokens,
backend-enforced tenant roles, transport-pinned SSRF defenses, database-claimed
scheduled work, post-commit server-sent events, sampled uptime and latency
analytics, Testcontainers integration coverage, Playwright browser testing,
hardened Docker images, and Terraform for an HTTPS AWS ECS and RDS production
target.

## Recommended resume bullets

Choose three or four based on the role:

- Built PulseOps, a multi-tenant monitoring and incident-response platform with
  React, TypeScript, Java 21, Spring Boot, PostgreSQL, and Flyway, covering the
  workflow from endpoint registration through confirmed outage, incident
  response, recovery, and reliability analytics.
- Designed a restart-safe monitoring scheduler using PostgreSQL row locking,
  `SKIP LOCKED`, expiring claim leases, optimistic status updates, and separate
  failure and recovery thresholds to prevent overlapping checks and noisy
  one-sample outages.
- Implemented secure authentication and authorization with BCrypt, short-lived
  JWTs, hashed rotating refresh-token families, backend-enforced tenant roles,
  bounded auth rate limits, strict CORS and cookie settings, and transport-pinned
  SSRF protections.
- Created duplicate-safe automatic incidents, assignments, comments,
  resolution documentation, and append-only timelines, then delivered
  authenticated organization-scoped live refreshes through post-commit SSE and
  TanStack Query invalidation.
- Built PostgreSQL analytics for sampled uptime, latency percentiles, incident
  response times, time-series trends, and per-service comparisons, presented in
  a responsive React and Recharts dashboard.
- Established automated quality gates with 87 JUnit and Spring integration
  tests, 20 Vitest component tests, Testcontainers PostgreSQL, Playwright E2E,
  SpotBugs, secret scanning, container builds, and bounded latency/error load
  smoke thresholds.
- Defined a production AWS target with Terraform across two availability zones,
  including private ECS Fargate services, isolated RDS PostgreSQL, ALB and ACM
  HTTPS, Route 53, ECR, Secrets Manager, CloudWatch alarms, VPC flow logs, and
  encrypted access logs.
- Automated migration-gated ECS releases with immutable images and GitHub OIDC
  credentials, running Flyway as a private one-off task before rolling backend
  and frontend services with deployment circuit-breaker rollback.

## Role-specific selections

### Full-stack software engineer

Use the product-workflow, incident/live-update, analytics, and testing bullets.

### Backend software engineer

Use the scheduler, security, incident consistency, and integration-testing
bullets.

### Cloud or DevOps engineer

Use the AWS topology, deployment automation, testing gates, and hardened
container bullets.

### Security-focused software engineer

Use the authentication and SSRF bullet, add the tenant-isolation integration
coverage, and mention short-lived GitHub OIDC credentials.

## LinkedIn launch post

I built **PulseOps**, a production-style service-monitoring and
incident-management platform for engineering teams.

PulseOps registers HTTP and HTTPS services, performs bounded scheduled health
checks, waits for configurable failure thresholds, automatically creates a
duplicate-safe incident, and keeps the response workspace current through
authenticated server-sent events. The analytics workspace reports sampled
uptime, response-time percentiles, incident trends, and per-service
reliability.

The stack includes:

- React, TypeScript, TanStack Query, Recharts, and Vite
- Java 21, Spring Boot, Spring Security, JPA, and Flyway
- PostgreSQL with locking, claim leases, constraints, and analytics queries
- JUnit, Testcontainers, Vitest, Playwright, SpotBugs, and load-smoke gates
- Docker, GitHub Actions, Terraform, AWS ECS Fargate, RDS, ALB, ACM, ECR,
  Secrets Manager, and CloudWatch

Some of my favorite engineering decisions were using a modular monolith,
publishing live invalidation events only after database commit, pinning validated
DNS addresses for outbound checks, and running database migrations before ECS
service rollout.

Repository: [REPOSITORY URL]

Demo video: [DEMO URL]

#Java #SpringBoot #React #TypeScript #PostgreSQL #AWS #Terraform #Docker
#SoftwareEngineering #DevOps

## Short LinkedIn version

I built **PulseOps**, a multi-tenant monitoring and incident-response platform
with React, TypeScript, Java 21, Spring Boot, and PostgreSQL.

It performs scheduled HTTP checks, confirms failures before declaring an
outage, automatically opens duplicate-safe incidents, streams authenticated
live updates, and calculates sampled reliability analytics. The repository also
includes Testcontainers and Playwright coverage, hardened Docker images, and a
Terraform-defined AWS ECS/RDS target with migration-gated GitHub OIDC releases.

Repository: [REPOSITORY URL]

#SpringBoot #React #PostgreSQL #AWS #Terraform

## Interview introduction

> PulseOps is the project I use to demonstrate end-to-end software engineering.
> The visible workflow is monitoring and incident response, but the difficult
> parts are underneath: tenant-safe authorization, SSRF-safe outbound requests,
> restart-safe scheduling, consistent outage transitions, post-commit live
> events, database analytics, and a release process that applies migrations
> before ECS rollout. I kept it as a modular monolith because those consistency
> boundaries matter more than adding network hops at this stage.

## Technical interview prompts

Be prepared to explain:

1. Why a modular monolith was a better first architecture than microservices.
2. How `SKIP LOCKED` and expiring claims prevent duplicate scheduled checks.
3. Why network target validation alone is insufficient without transport
   pinning.
4. How refresh-token rotation detects replay without storing raw tokens.
5. Why SSE messages invalidate queries instead of carrying authoritative
   domain state.
6. How the database prevents duplicate automatic incidents.
7. Why sampled uptime is not equivalent to continuous SLA availability.
8. Why Flyway runs as a separate release task instead of at application startup.
9. Why the backend task count remains one until live events and rate limits use
   shared state.
10. How GitHub OIDC removes long-lived AWS credentials from the repository.

## Accuracy checklist

Before publishing:

- Do not say the AWS environment is live until Terraform has actually been
  applied and the public endpoint has been tested.
- Do not claim notification or email delivery is implemented.
- Describe uptime as sampled or observation-based.
- Keep the test counts synchronized with current verification output.
- Replace repository and video placeholders.
- Remove any bullet you cannot explain comfortably in an interview.
