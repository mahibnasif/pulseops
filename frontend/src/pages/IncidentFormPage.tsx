import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { useAuth } from '../features/auth/useAuth'
import * as incidentApi from '../features/incidents/incidentApi'
import type { IncidentSeverity } from '../features/incidents/types'
import { useOrganizations } from '../features/organizations/useOrganizations'
import { listServices } from '../features/services/serviceApi'

export function IncidentFormPage() {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const organizationId = currentOrganization?.id
  const navigate = useNavigate()
  const [serviceId, setServiceId] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [severity, setSeverity] = useState<IncidentSeverity>('HIGH')
  const [error, setError] = useState('')
  const services = useQuery({
    queryKey: ['services', organizationId, 'incident-options'],
    queryFn: () => listServices(auth.accessToken!, organizationId!, {}),
    enabled: Boolean(organizationId),
  })
  const create = useMutation({
    mutationFn: () =>
      incidentApi.createIncident(auth.accessToken!, organizationId!, {
        serviceId,
        title,
        description,
        severity,
      }),
    onSuccess: (incident) =>
      navigate(`/incidents/${incident.id}`, { replace: true }),
    onError: (failure) =>
      setError(
        failure instanceof ApiError
          ? failure.message
          : 'The incident could not be declared.',
      ),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    create.mutate()
  }

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main service-form-main">
        <Link className="back-link" to="/incidents">
          ← Back to incidents
        </Link>
        <div className="form-page-heading">
          <p className="eyebrow">Manual incident</p>
          <h1>Declare an incident.</h1>
          <p>
            Open a coordinated response record for a service degradation or
            outage.
          </p>
        </div>
        <form className="service-form" onSubmit={submit}>
          <section className="service-form-section">
            <div>
              <h2>Incident report</h2>
              <p>Capture the first known impact and response priority.</p>
            </div>
            <div className="form-grid">
              <label>
                Service
                <select
                  required
                  value={serviceId}
                  onChange={(event) => setServiceId(event.target.value)}
                >
                  <option value="">Select a service</option>
                  {services.data?.content.map((service) => (
                    <option key={service.id} value={service.id}>
                      {service.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Severity
                <select
                  value={severity}
                  onChange={(event) =>
                    setSeverity(event.target.value as IncidentSeverity)
                  }
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </label>
              <label className="form-span-2">
                Title
                <input
                  required
                  maxLength={200}
                  value={title}
                  onChange={(event) => setTitle(event.target.value)}
                />
              </label>
              <label className="form-span-2">
                Description <span className="optional">Optional</span>
                <textarea
                  maxLength={4000}
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </label>
            </div>
          </section>
          {error && <div className="form-error">{error}</div>}
          <div className="form-actions">
            <Link className="button-secondary" to="/incidents">
              Cancel
            </Link>
            <button className="button" type="submit" disabled={create.isPending}>
              {create.isPending ? 'Declaring…' : 'Declare incident'}
            </button>
          </div>
        </form>
      </main>
    </div>
  )
}
