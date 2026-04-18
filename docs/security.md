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
authentication endpoints. Phase 9 will add explicit authentication rate limits
and repeat the CSRF/CORS threat-model review before production deployment.

Login failures use the same response for an unknown email and a wrong password.
Password hashes never cross the DTO boundary.

## SSRF policy

The Phase 4 manual HTTP checker:

- allows only absolute `http` and `https` URLs;
- rejects credentials, fragments, malformed URLs, and unsupported schemes;
- resolves DNS and rejects loopback, link-local, private, multicast,
  documentation, and reserved address ranges by default;
- disables redirects;
- applies a finite request timeout;
- caps response bytes and the returned excerpt; and
- returns categorized, safe failure messages rather than transport internals.

`MONITORING_ALLOW_PRIVATE_TARGETS` defaults to `false` in the application.
Local Docker Compose deliberately defaults it to `true` so the backend
container can check another development container. Production must keep it
`false`.

The DNS validation preflight and Java HTTP connection currently resolve
separately. That leaves a DNS-rebinding time-of-check/time-of-use window. The
checker must pin the validated address, or validate the connected peer through
an equivalent transport, before untrusted production targets are enabled.
Redirect support must likewise revalidate every destination if introduced.
PulseOps must not become an internal network scanner.

## Dependency advisory record

As of 2026-07-30, npm reports a high-severity React Router advisory for RSC
action handling in all currently published affected releases. PulseOps uses a
client-only Vite SPA and Spring REST APIs: it does not enable React Server
Components, server actions, SSR, or React Router framework mode. The package is
pinned to the current 7.x release and must be upgraded when an applicable
patched release is available. CI should continue reporting dependency audits;
this exception does not cover future use of the affected server features.
