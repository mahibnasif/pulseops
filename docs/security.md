# Security

## Tenant isolation

Every organization-scoped command and query verifies active membership.
Role checks run in backend application services. Related IDs—such as service,
incident, and assignee—must belong to the same organization.

## Authentication direction

Phase 2 will use BCrypt password hashes, short-lived JWT access tokens, hashed
rotating refresh tokens, revocation, safe authentication errors, and rate
limits. Secrets come from environment or managed secret stores.

The foundation security chain permits health and API documentation only and
denies all other routes. It contains no temporary default user.

## SSRF policy

Before monitoring is enabled, the HTTP checker must:

- allow only `http` and `https`;
- reject credentials and unsupported URL schemes;
- block loopback, link-local, private, multicast, and reserved addresses in
  production;
- block cloud metadata endpoints;
- resolve DNS and validate every result;
- reconnect only to the validated destination;
- disable or manually validate redirects with a strict redirect limit;
- enforce connect/read/overall timeouts;
- cap response bytes and sanitize stored excerpts.

DNS is revalidated for redirects and future checks. PulseOps must not become an
internal network scanner.

## Dependency advisory record

As of 2026-07-30, npm reports a high-severity React Router advisory for RSC
action handling in all currently published affected releases. PulseOps uses a
client-only Vite SPA and Spring REST APIs: it does not enable React Server
Components, server actions, SSR, or React Router framework mode. The package is
pinned to the current 7.x release and must be upgraded when an applicable
patched release is available. CI should continue reporting dependency audits;
this exception does not cover future use of the affected server features.
