# Security

## Tenant isolation

Every organization-scoped command and query verifies active membership.
Role checks run in backend application services. Related IDs—such as service,
incident, and assignee—must belong to the same organization.

Non-members receive a tenant-safe not-found response instead of confirmation
that an organization exists. `ADMIN` is required for organization updates,
invitations, role changes, removal, and invitation inspection. Only the current
owner can transfer ownership. Owners cannot leave, be removed, or be demoted
until ownership has moved to another active member.

Organization invitations contain 256-bit opaque random tokens. Only SHA-256
hashes are persisted. Acceptance requires a valid authenticated account whose
normalized email matches the invitation, even when the raw token is presented.
The invitation and membership rows are locked during acceptance to prevent
duplicate/concurrent joins. Invitation list DTOs never expose tokens or hashes.
Raw tokens are returned only in the creation response for a future email
delivery adapter; the current in-app acceptance path uses the invitation ID
after matching the authenticated email.

## Authentication

Phase 2 uses BCrypt password hashes and exchanges credentials for 15-minute JWT
access tokens. JWT verification requires the configured issuer, audience, and
HS256 signature. The Base64 signing key must decode to at least 256 bits and
comes from environment or a managed secret store.

Refresh tokens are 256-bit opaque random values. Only their SHA-256 hashes are
stored. Every refresh rotates the token under a stable family ID. Replaying a
revoked token invalidates all active tokens in that family. Logout revokes the
presented refresh token.

The browser keeps access tokens in memory. Refresh values use HttpOnly,
SameSite=Strict cookies scoped to `/api/v1/auth`; production also requires the
Secure flag. CORS allows only configured origins and credentials. The API is
stateless and has no default user.

CSRF protection is disabled for bearer-token API calls. The only credential
automatically attached by browsers is the Strict refresh cookie, scoped to the
authentication endpoints. Login and registration require JSON, so a cross-site
form cannot submit a valid request; a scripted cross-origin request is subject
to the exact-origin CORS allowlist.

Login is limited by both client address and a SHA-256 fingerprint of the
normalized email. Registration and refresh are limited by client address.
Limits use bounded, in-memory fixed-window counters and return `429` with
`Retry-After`. Raw emails and addresses are not retained as keys. A trusted
reverse proxy may supply `X-Real-IP` only when
`AUTH_RATE_LIMIT_TRUST_PROXY_CLIENT_IP=true`; that proxy must overwrite the
header and the backend must not be directly internet-accessible. Distributed
deployments require a shared rate-limit store or equivalent edge control.

Login failures use the same response for an unknown email and a wrong password.
Password hashes never cross the DTO boundary.

Live streams use the same bearer JWT validation and active organization
membership checks as REST endpoints. The SPA opens them with streaming
`fetch`, because browser `EventSource` cannot attach the authorization header.
Access tokens therefore remain in memory and never appear in event-stream
URLs, logs, or browser history. A `401` triggers the existing refresh-cookie
rotation flow before reconnecting.

## SSRF policy

The HTTP checker used by manual and scheduled monitoring:

- allows only absolute `http` and `https` URLs;
- rejects credentials, fragments, malformed URLs, and unsupported schemes;
- resolves DNS inside the HTTP connection manager and rejects the entire answer
  if any address is loopback, link-local, private, multicast, documentation,
  translation, or otherwise reserved;
- disables redirects;
- applies a finite request timeout;
- caps response bytes and the returned excerpt; and
- returns categorized, safe failure messages rather than transport internals.

`MONITORING_ALLOW_PRIVATE_TARGETS` defaults to `false` in the application.
Local Docker Compose also defaults it to `false`. A developer may explicitly
enable it for a controlled demo network, but must return it to `false` before
checking untrusted URLs. The custom transport resolver supplies the same
validated address set used for connection establishment, closing the earlier
DNS-rebinding validation/connect window. Redirects remain disabled; any future
redirect implementation must validate every hop. PulseOps must not become an
internal network scanner.

## Authorization review

All organization-scoped object lookups include the organization identifier.
Authorization runs before object access so a non-member receives
`ORGANIZATION_NOT_FOUND`, while a member using an object ID from another tenant
receives an object-specific not-found response.

| Capability | ADMIN | ENGINEER | VIEWER |
|---|---:|---:|---:|
| View organization, services, incidents, analytics, live events | Yes | Yes | Yes |
| Manage organization, members, roles, and invitations | Yes | No | No |
| Create, edit, pause, resume, or delete monitored services | Yes | No | No |
| Trigger a manual health check | Yes | Yes | No |
| Create or update incidents, assign, comment, resolve, reopen | Yes | Yes | No |

Integration tests exercise outsider, cross-tenant-ID, engineer, and viewer
paths. UI visibility remains a usability feature; backend checks are the
security boundary.

## Secret and browser controls

`.env` files are ignored. The setup script independently generates the JWT
signing key and local database password. Compose refuses to start without those
values and binds PostgreSQL and the direct backend port to loopback. Production
must inject secrets from a managed store, disable OpenAPI with
`OPENAPI_ENABLED=false`, and never bake secrets into an image.

CI scans tracked text for common private-key, cloud-token, JWT-secret, and
database-secret signatures. This is a guardrail, not a replacement for provider
secret scanning and immediate rotation after any suspected disclosure.

The frontend proxy emits a restrictive content security policy, denies framing,
disables MIME sniffing, avoids referrer disclosure, and disables unneeded
browser permissions. HSTS belongs at the production TLS load balancer.

## Dependency advisory record

As of 2026-07-30, npm reports a high-severity React Router advisory for RSC
action handling in all currently published affected releases. PulseOps uses a
client-only Vite SPA and Spring REST APIs: it does not enable React Server
Components, server actions, SSR, or React Router framework mode. The package is
pinned to the current 7.x release and must be upgraded when an applicable
patched release is available. CI should continue reporting dependency audits;
this exception does not cover future use of the affected server features.
