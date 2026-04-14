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
