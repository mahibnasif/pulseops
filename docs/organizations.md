# Organizations and memberships

Organizations are PulseOps tenant boundaries. Services, incidents, analytics,
notifications, and audit records added in later phases will be scoped through
an organization membership.

## Roles

| Capability | `ADMIN` | `ENGINEER` | `VIEWER` |
|---|---:|---:|---:|
| View organization and members | Yes | Yes | Yes |
| Manage organization settings | Yes | No | No |
| Invite and remove members | Yes | No | No |
| Change member roles | Yes | No | No |
| Transfer ownership | Owner only | No | No |
| Manage monitored services | Yes | Yes in later phases | No |
| Manage incidents | Yes | Yes in later phases | No |
| View dashboards and analytics | Yes | Yes | Yes |

Ownership is not a role. The owner always holds an active `ADMIN` membership,
but only the owner can transfer ownership. Transferring ownership promotes the
recipient to `ADMIN`; the previous owner remains an administrator until their
role changes or they leave.

## Lifecycle

1. An authenticated user creates an organization.
2. PulseOps atomically creates the organization and owner membership.
3. An administrator creates an email-bound invitation with a role and
   seven-day expiry.
4. A user registered with the matching normalized email accepts the invitation.
5. PulseOps locks the invitation and durable membership row, then creates or
   reactivates the membership.
6. Administrators may change non-owner roles or mark memberships removed.
7. Non-owner members may leave. Owners must transfer ownership first.

Membership rows are retained with `LEFT` or `REMOVED` status so future audit
history does not lose identity. Rejoining reactivates the same
organization/user pair.

## Authorization rule

Frontend role-aware controls are only a usability layer. The backend checks the
JWT user, active membership, required role, organization relationship, and
ownership invariant for every command. A user outside the tenant receives
`ORGANIZATION_NOT_FOUND`, including when the organization UUID is valid.

See [api.md](api.md), [database.md](database.md), and
[security.md](security.md) for the HTTP, persistence, and threat-model details.
