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
