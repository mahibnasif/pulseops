# API

## Conventions

- Base path: `/api/v1`
- JSON request and response bodies
- UUID resource identifiers
- UTC timestamps in ISO 8601 format
- Pagination for collections
- DTOs at the HTTP boundary
- Organization ID in scoped resource paths

## Planned groups

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

## Errors

Errors will use one stable envelope containing timestamp, HTTP status, stable
application code, safe message, request path, optional field errors, and a
correlation/trace ID. Internal exception messages and stack traces are not
returned to clients.

Authorization performs membership and role checks before resource lookup
results are exposed. A UUID is an identifier, not an authorization mechanism.
