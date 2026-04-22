# API

## Conventions

- Base path: `/api/v1`
- JSON request and response bodies
- UUID resource identifiers
- UTC timestamps in ISO 8601 format
- Pagination for collections
- DTOs at the HTTP boundary
- Organization ID in scoped resource paths

## Endpoint groups

```text
/api/v1/auth
/api/v1/users
/api/v1/organizations
/api/v1/organizations/{organizationId}/members
/api/v1/organizations/{organizationId}/services
/api/v1/organizations/{organizationId}/incidents
/api/v1/organizations/{organizationId}/analytics
/api/v1/organizations/{organizationId}/audit-logs
/api/v1/notifications
```

OpenAPI JSON is served at `/v3/api-docs`; Swagger UI is served at
`/swagger-ui.html`.

## Authentication endpoints

| Method | Path | Authentication | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Public | Create an account and session |
| `POST` | `/api/v1/auth/login` | Public | Authenticate and create a session |
| `POST` | `/api/v1/auth/refresh` | Refresh cookie | Rotate the refresh token and issue an access token |
| `POST` | `/api/v1/auth/logout` | Refresh cookie when present | Revoke the session and clear the cookie |
| `GET` | `/api/v1/users/me` | Bearer JWT | Return the current safe user profile |

Registration and login return the access token and safe user DTO in JSON. The
opaque refresh value is never included in JSON; it is transported in an
HttpOnly, SameSite cookie. Clients send access tokens as
`Authorization: Bearer <token>`.

## Errors

Errors use one stable envelope containing timestamp, HTTP status, stable
application code, safe message, request path, optional field errors, and a
correlation/trace ID. Internal exception messages and stack traces are not
returned to clients.

Authorization performs membership and role checks before resource lookup
results are exposed. A UUID is an identifier, not an authorization mechanism.

Organization and membership discovery collections are intentionally
unpaginated because they are bounded team/workspace lists. Service collections
are paginated. Incident, notification, analytics, and audit collections will
be paginated when introduced.

## Organization endpoints

All endpoints below require a bearer JWT.

| Method | Path | Required access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/organizations` | Authenticated user | List active organization memberships |
| `POST` | `/api/v1/organizations` | Authenticated user | Create an organization and become its owner/admin |
| `GET` | `/api/v1/organizations/{id}` | Active member | View organization settings |
| `PATCH` | `/api/v1/organizations/{id}` | `ADMIN` | Update name, slug, or description |
| `GET` | `/api/v1/organizations/{id}/members` | Active member | List active members |
| `PATCH` | `/api/v1/organizations/{id}/members/{userId}` | `ADMIN` | Change a non-owner member role |
| `DELETE` | `/api/v1/organizations/{id}/members/{userId}` | `ADMIN` | Remove a non-owner member |
| `POST` | `/api/v1/organizations/{id}/leave` | Active non-owner member | Leave an organization |
| `POST` | `/api/v1/organizations/{id}/transfer-ownership` | Owner | Transfer ownership to an active member |
| `GET` | `/api/v1/organizations/{id}/invitations` | `ADMIN` | List organization invitations |
| `POST` | `/api/v1/organizations/{id}/invitations` | `ADMIN` | Create an email-bound invitation |
| `DELETE` | `/api/v1/organizations/{id}/invitations/{invitationId}` | `ADMIN` | Cancel an unaccepted invitation |
| `GET` | `/api/v1/invitations` | Authenticated user | List unexpired invitations matching the user's email |
| `POST` | `/api/v1/invitations/{invitationId}/accept` | Invited user | Accept an in-app invitation |
| `POST` | `/api/v1/invitations/accept` | Invited user + token | Accept an invitation link token |

Creation returns the raw invitation token once so a future delivery adapter can
construct an acceptance link. Lists never expose the token or its hash.
PostgreSQL stores only the SHA-256 hash. In the current local product, a
registered user can accept a pending invitation from the dashboard without
email delivery.

Non-members receive the same `ORGANIZATION_NOT_FOUND` response whether the
organization does not exist or is outside their tenant boundary. This prevents
organization identifier probing. Authenticated members without sufficient
permissions receive `INSUFFICIENT_ORGANIZATION_ROLE`.

## Monitored service endpoints

All paths are organization scoped. Any active member can view services.
Administrators manage configuration and lifecycle. Engineers and
administrators can run manual checks; viewers cannot.

| Method | Path | Required access | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/organizations/{id}/services` | Active member | List, filter, search, sort, and paginate services |
| `POST` | `/api/v1/organizations/{id}/services` | `ADMIN` | Create an HTTP/HTTPS service |
| `GET` | `/api/v1/organizations/{id}/services/{serviceId}` | Active member | View one active service |
| `PATCH` | `/api/v1/organizations/{id}/services/{serviceId}` | `ADMIN` | Update service configuration |
| `DELETE` | `/api/v1/organizations/{id}/services/{serviceId}` | `ADMIN` | Soft-delete a service |
| `POST` | `/api/v1/organizations/{id}/services/{serviceId}/pause` | `ADMIN` | Pause scheduled monitoring |
| `POST` | `/api/v1/organizations/{id}/services/{serviceId}/resume` | `ADMIN` | Resume with `UNKNOWN` status |
| `POST` | `/api/v1/organizations/{id}/services/{serviceId}/check` | `ADMIN` or `ENGINEER` | Run, persist, and apply an immediate check |
| `GET` | `/api/v1/organizations/{id}/services/{serviceId}/checks` | Active member | View paginated check history |

List query parameters include `search`, `status`, `active`, `page`, `size`,
`sort`, and `direction`. Active service names are unique within an
organization, case-insensitively.

Phase 4 accepts absolute HTTP/HTTPS URLs and `GET` or `HEAD`. Optional response
expectations support text containment or a paired simple JSON path/value such
as `$.status = healthy`. Manual-check responses report success, degradation,
HTTP status, elapsed time, validation outcome, a bounded excerpt, source,
before/after service status, and safe failure details. Manual and scheduled
checks use the same persisted threshold transition path.

Check-history parameters include `source`, `success`, `page`, and `size`.
Results are ordered newest first. A result records whether it was applied to
status; a scheduled result that finishes after monitoring was paused is retained
without changing `PAUSED`.
