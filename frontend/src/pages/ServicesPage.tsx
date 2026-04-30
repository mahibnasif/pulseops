import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { ServiceStatusBadge } from '../components/services/ServiceStatusBadge'
import { useAuth } from '../features/auth/useAuth'
import * as serviceApi from '../features/services/serviceApi'
import type { ServiceStatus } from '../features/services/types'
import { useOrganizations } from '../features/organizations/useOrganizations'

export function ServicesPage() {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<ServiceStatus | ''>('')
  const organizationId = currentOrganization?.id
  const services = useQuery({
    queryKey: ['services', organizationId, search, status],
    queryFn: () =>
      serviceApi.listServices(auth.accessToken!, organizationId!, {
        search,
        status,
      }),
    enabled: Boolean(organizationId),
  })
  const canManage = currentOrganization?.currentUserRole === 'ADMIN'

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main services-main">
        <section className="page-heading">
          <div>
            <p className="eyebrow">Service inventory</p>
            <h1>Monitored services</h1>
            <p>
              HTTP and HTTPS endpoints registered to{' '}
              {currentOrganization?.name ?? 'this organization'}.
            </p>
          </div>
          {canManage && (
            <Link className="button" to="/services/new">
              Add service
            </Link>
          )}
        </section>
        <section className="service-toolbar" aria-label="Service filters">
          <label>
            <span className="sr-only">Search services</span>
            <input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search by service name"
            />
          </label>
          <select
            aria-label="Filter by status"
            value={status}
            onChange={(event) =>
              setStatus(event.target.value as ServiceStatus | '')
            }
          >
            <option value="">All statuses</option>
            <option value="UNKNOWN">Unknown</option>
            <option value="OPERATIONAL">Operational</option>
            <option value="DEGRADED">Degraded</option>
            <option value="DOWN">Down</option>
            <option value="PAUSED">Paused</option>
          </select>
          <span>{services.data?.totalElements ?? 0} services</span>
        </section>
        {services.isLoading ? (
          <div className="loading-panel">Loading services…</div>
        ) : services.error ? (
          <div className="notice">The service inventory could not be loaded.</div>
        ) : services.data?.content.length ? (
          <div className="service-table-wrap">
            <table className="service-table">
              <thead>
                <tr>
                  <th>Service</th>
                  <th>Status</th>
                  <th>Method</th>
                  <th>Interval</th>
                  <th>Last checked</th>
                </tr>
              </thead>
              <tbody>
                {services.data.content.map((service) => (
                  <tr key={service.id}>
                    <td>
                      <Link to={`/services/${service.id}`}>
                        <strong>{service.name}</strong>
                        <span>{service.url}</span>
                      </Link>
                    </td>
                    <td>
                      <ServiceStatusBadge status={service.status} />
                    </td>
                    <td>{service.httpMethod}</td>
                    <td>{service.checkIntervalSeconds}s</td>
                    <td>
                      {service.lastCheckedAt
                        ? new Date(service.lastCheckedAt).toLocaleString()
                        : 'Never'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <section className="empty-services">
            <span aria-hidden="true">↗</span>
            <h2>No services match this view.</h2>
            <p>
              {canManage
                ? 'Register an HTTP or HTTPS endpoint to begin.'
                : 'An administrator has not registered a service yet.'}
            </p>
            {canManage && (
              <Link className="button-secondary" to="/services/new">
                Add the first service
              </Link>
            )}
          </section>
        )}
      </main>
    </div>
  )
}
