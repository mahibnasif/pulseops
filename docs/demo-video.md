# Demo video package

This package supports a concise recruiter walkthrough and a more technical
project demonstration. The recording should show the real application using
synthetic data; it should not imply that the Terraform stack has been applied
to a live AWS account.

## Prepare the deterministic demo

Start Docker Desktop, then run from Windows PowerShell:

```powershell
Set-Location -LiteralPath 'D:\Code\vibing\pulseops'
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\capture-portfolio.ps1' `
  -KeepRunning
```

This starts an isolated Compose project, runs Flyway, creates the portfolio
organization and monitoring history, and leaves the application at
<http://127.0.0.1:5280>.

Use these synthetic credentials:

```text
Email:    avery.morgan@example.com
Password: Portfolio-Demo-Only-2026
```

Stop and delete the isolated database when recording is complete:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\scripts\capture-portfolio.ps1' `
  -Stop
```

## Recommended recording setup

- Record at 1440 x 900 or 1920 x 1080 with browser zoom at 100%.
- Hide bookmarks, personal tabs, desktop notifications, and password-manager
  overlays.
- Keep the pointer still while explaining a screen.
- Use short, deliberate transitions instead of rapid scrolling.
- Record narration separately if live narration reduces clarity.
- Export an H.264 MP4 at 1080p and verify text remains readable.
- Never show AWS credentials, `.env`, Terraform state, or real email addresses.

## Three-minute walkthrough

### 0:00-0:20 — Problem and product

**Screen:** Landing page.

**Narration:**

> PulseOps is a production-style monitoring and incident-management platform
> for engineering teams. Teams register HTTP services, PulseOps performs safe
> scheduled checks, confirms repeated failures, opens incidents, and keeps the
> response workspace and reliability analytics current in real time.

### 0:20-0:40 — Multi-tenant workspace

**Screen:** Sign in, then open the Northstar Engineering dashboard.

**Narration:**

> Authentication uses short-lived JWT access tokens and rotating opaque refresh
> tokens. Organizations are the tenant boundary, and backend authorization
> enforces administrator, engineer, and viewer permissions independently of the
> interface.

### 0:40-1:10 — Monitoring inventory

**Screen:** Services list.

**Narration:**

> This synthetic workspace demonstrates all current states: operational,
> degraded because of latency, down after a confirmed threshold, and manually
> paused. Service configuration includes status and content expectations,
> timeouts, intervals, and separate failure and recovery thresholds.

### 1:10-1:40 — Confirmed outage

**Screen:** Checkout API details.

**Narration:**

> The Checkout API required two failures before transitioning to down, avoiding
> an incident after one transient error. Every check is persisted with latency,
> status code, failure classification, source, and the resulting status
> transition. Scheduled workers claim due rows in PostgreSQL so overlapping
> application instances do not check the same service simultaneously.

### 1:40-2:15 — Incident response

**Screen:** Checkout API incident.

**Narration:**

> PulseOps automatically created one incident when the service entered down.
> Responders can classify, assign, investigate, comment, document root cause,
> and resolve the incident. The append-only timeline records both monitoring
> actions and human changes. A partial unique database index prevents duplicate
> active incidents for the same outage.

### 2:15-2:40 — Analytics and live updates

**Screen:** Analytics.

**Narration:**

> PostgreSQL computes sampled uptime, average and percentile latency, incident
> metrics, trends, and per-service comparisons. Authenticated server-sent
> events invalidate TanStack Query data after committed changes, keeping the
> dashboard current without treating the event stream as another source of
> truth.

### 2:40-3:10 — Engineering depth

**Screen:** Repository architecture diagram followed by the test and deployment
sections of the README.

**Narration:**

> The application is a Spring Boot modular monolith with a React TypeScript
> frontend and PostgreSQL. The test suite covers backend integration boundaries,
> frontend behavior, the full browser journey, secret scanning, static analysis,
> and bounded load smoke tests. Terraform defines private ECS Fargate services,
> RDS, an HTTPS load balancer, CloudWatch monitoring, and an OIDC-based GitHub
> release pipeline that runs Flyway before deploying application revisions.

### 3:10-3:20 — Close

**Screen:** Dashboard or repository title.

**Narration:**

> PulseOps demonstrates full-stack product development, security-focused backend
> design, database concurrency, automated testing, containers, and AWS delivery
> in one coherent portfolio project.

## Sixty-second version

Use five quick scenes:

1. **Landing, 8 seconds:** Define the monitoring and incident problem.
2. **Services, 12 seconds:** Show four service states and configurable
   thresholds.
3. **Service details, 12 seconds:** Show two failures and the persisted status
   transition.
4. **Incident, 15 seconds:** Show automatic creation, assignment, severity, and
   timeline.
5. **Analytics and architecture, 13 seconds:** Close with reliability metrics,
   the technology stack, tests, and Terraform-defined AWS target.

Suggested narration:

> PulseOps is a multi-tenant monitoring and incident-response platform built
> with React, TypeScript, Java 21, Spring Boot, and PostgreSQL. It performs
> bounded HTTP checks, applies failure and recovery thresholds, and
> automatically opens a duplicate-safe incident after a confirmed outage.
> Responders can assign, investigate, comment, resolve, and review an immutable
> timeline while SSE keeps the interface current. PostgreSQL analytics summarize
> sampled uptime, latency percentiles, and incident performance. The repository
> includes integration, frontend, browser, security, and load-smoke tests,
> hardened containers, and Terraform for an HTTPS ECS and RDS deployment with
> GitHub OIDC releases.

## Final review checklist

- The title and repository URL appear in the first or final frame.
- Synthetic credentials and endpoints are the only data visible.
- Operational, degraded, down, and paused states are legible.
- The incident is visibly monitoring-created, assigned, and critical.
- The narration says "sampled uptime," not continuous or SLA-grade uptime.
- AWS is described as the Terraform-defined production target unless a live
  deployment has actually been completed.
- Notification delivery is not presented as implemented.
- Audio is normalized, captions are corrected, and the final link works in a
  private browser window.
