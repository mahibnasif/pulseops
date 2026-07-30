import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
} from '../components/incidents/IncidentBadges'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { useAuth } from '../features/auth/useAuth'
import * as incidentApi from '../features/incidents/incidentApi'
import type {
  IncidentSeverity,
  IncidentStatus,
} from '../features/incidents/types'
import { listMembers } from '../features/organizations/organizationApi'
import { useOrganizations } from '../features/organizations/useOrganizations'
import { listServices } from '../features/services/serviceApi'

export function IncidentsPage() {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const [searchParams] = useSearchParams()
  const organizationId = currentOrganization?.id
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<IncidentStatus | ''>('')
  const [severity, setSeverity] = useState<IncidentSeverity | ''>('')
  const [serviceId, setServiceId] = useState(
    searchParams.get('serviceId') ?? '',
  )
  const [assigneeId, setAssigneeId] = useState('')
  const canManage = currentOrganization?.currentUserRole !== 'VIEWER'

  const incidents = useQuery({
    queryKey: [
      'incidents',
      organizationId,
      search,
      status,
      severity,
      serviceId,
      assigneeId,
    ],
    queryFn: () =>
      incidentApi.listIncidents(auth.accessToken!, organizationId!, {
        search,
        status,
        severity,
        serviceId,
        assigneeId,
      }),
    enabled: Boolean(organizationId),
  })
  const services = useQuery({
    queryKey: ['services', organizationId, 'incident-options'],
    queryFn: () =>
      listServices(auth.accessToken!, organizationId!, {}),
    enabled: Boolean(organizationId),
  })
  const members = useQuery({
    queryKey: ['members', organizationId],
    queryFn: () => listMembers(auth.accessToken!, organizationId!),
    enabled: Boolean(organizationId),
  })
  const serviceNames = new Map(
    services.data?.content.map((service) => [service.id, service.name]),
  )
  const memberNames = new Map(
    members.data?.map((member) => [
      member.userId,
      `${member.firstName} ${member.lastName}`,
    ]),
  )

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main incidents-main">
        <section className="page-heading">
          <div>
            <p className="eyebrow">Incident response</p>
            <h1>Incidents</h1>
            <p>
              Coordinate active response and preserve a complete operational
              record.
            </p>
          </div>
          {canManage && (
            <Link className="button" to="/incidents/new">
              Declare incident
            </Link>
          )}
        </section>

        <section className="incident-toolbar" aria-label="Incident filters">
          <label>
            <span className="sr-only">Search incidents</span>
            <input
              placeholder="Search incident titles"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </label>
          <Filter
            label="Filter by status"
            value={status}
            onChange={(value) => setStatus(value as IncidentStatus | '')}
            options={[
              'OPEN',
              'ACKNOWLEDGED',
              'INVESTIGATING',
              'IDENTIFIED',
              'MONITORING',
              'RESOLVED',
            ]}
          />
          <Filter
            label="Filter by severity"
            value={severity}
            onChange={(value) => setSeverity(value as IncidentSeverity | '')}
            options={['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']}
          />
          <select
            aria-label="Filter by service"
            value={serviceId}
            onChange={(event) => setServiceId(event.target.value)}
          >
            <option value="">All services</option>
            {services.data?.content.map((service) => (
              <option key={service.id} value={service.id}>
                {service.name}
              </option>
            ))}
          </select>
          <select
            aria-label="Filter by assignee"
            value={assigneeId}
            onChange={(event) => setAssigneeId(event.target.value)}
          >
            <option value="">All assignees</option>
            {members.data?.map((member) => (
              <option key={member.userId} value={member.userId}>
                {member.firstName} {member.lastName}
              </option>
            ))}
          </select>
        </section>

        {incidents.isLoading ? (
          <div className="loading-panel">Loading incidents…</div>
        ) : incidents.error ? (
          <div className="notice">The incident register could not be loaded.</div>
        ) : incidents.data?.content.length ? (
          <div className="service-table-wrap">
            <table className="service-table incident-table">
              <thead>
                <tr>
                  <th>Incident</th>
                  <th>Severity</th>
                  <th>Status</th>
                  <th>Service</th>
                  <th>Assignee</th>
                  <th>Detected</th>
                </tr>
              </thead>
              <tbody>
                {incidents.data.content.map((incident) => (
                  <tr key={incident.id}>
                    <td>
                      <Link to={`/incidents/${incident.id}`}>
                        <strong>{incident.title}</strong>
                        <span>
                          {incident.source === 'AUTOMATIC_MONITORING'
                            ? 'Created by monitoring'
                            : 'Declared manually'}
                        </span>
                      </Link>
                    </td>
                    <td>
                      <IncidentSeverityBadge severity={incident.severity} />
                    </td>
                    <td>
                      <IncidentStatusBadge status={incident.status} />
                    </td>
                    <td>{serviceNames.get(incident.serviceId) ?? 'Service'}</td>
                    <td>
                      {incident.assignedUserId
                        ? memberNames.get(incident.assignedUserId) ?? 'Member'
                        : 'Unassigned'}
                    </td>
                    <td>{new Date(incident.detectedAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <section className="empty-services">
            <span aria-hidden="true">✓</span>
            <h2>No incidents match this view.</h2>
            <p>
              Monitoring incidents will appear automatically when a service
              crosses its failure threshold.
            </p>
          </section>
        )}
      </main>
    </div>
  )
}

function Filter({
  label,
  value,
  options,
  onChange,
}: {
  label: string
  value: string
  options: string[]
  onChange: (value: string) => void
}) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="">All {label.replace('Filter by ', '')}es</option>
      {options.map((option) => (
        <option key={option} value={option}>
          {option.replace('_', ' ').toLowerCase()}
        </option>
      ))}
    </select>
  )
}
