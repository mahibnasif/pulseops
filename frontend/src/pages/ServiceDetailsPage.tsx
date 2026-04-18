import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { ServiceStatusBadge } from '../components/services/ServiceStatusBadge'
import { useAuth } from '../features/auth/useAuth'
import { useOrganizations } from '../features/organizations/useOrganizations'
import * as serviceApi from '../features/services/serviceApi'

export function ServiceDetailsPage() {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const { serviceId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const organizationId = currentOrganization?.id
  const service = useQuery({
    queryKey: ['service', organizationId, serviceId],
    queryFn: () => serviceApi.getService(auth.accessToken!, organizationId!, serviceId!),
    enabled: Boolean(organizationId && serviceId),
  })
  const canManage = currentOrganization?.currentUserRole === 'ADMIN'
  const canCheck = currentOrganization?.currentUserRole !== 'VIEWER'
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['service', organizationId, serviceId] })
  const pause = useMutation({ mutationFn: () => serviceApi.pauseService(auth.accessToken!, organizationId!, serviceId!), onSuccess: refresh })
  const resume = useMutation({ mutationFn: () => serviceApi.resumeService(auth.accessToken!, organizationId!, serviceId!), onSuccess: refresh })
  const check = useMutation({ mutationFn: () => serviceApi.runManualCheck(auth.accessToken!, organizationId!, serviceId!), onSuccess: refresh })
  const remove = useMutation({
    mutationFn: () => serviceApi.deleteService(auth.accessToken!, organizationId!, serviceId!),
    onSuccess: () => navigate('/services', { replace: true }),
  })

  if (service.isLoading) return <div className="app-shell"><WorkspaceHeader /><main className="workspace-main"><div className="loading-panel">Loading service…</div></main></div>
  if (!service.data) return <div className="app-shell"><WorkspaceHeader /><main className="workspace-main"><div className="notice">This service is unavailable.</div></main></div>
  const value = service.data

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main service-details-main">
        <Link className="back-link" to="/services">← All services</Link>
        <section className="service-detail-heading">
          <div>
            <div className="service-title-row"><ServiceStatusBadge status={value.status} /><span>{value.serviceType}</span></div>
            <h1>{value.name}</h1>
            <a href={value.url} rel="noreferrer" target="_blank">{value.url}</a>
          </div>
          <div className="detail-actions">
            {canCheck && <button className="button" disabled={check.isPending} onClick={() => check.mutate()}>{check.isPending ? 'Checking…' : 'Run manual check'}</button>}
            {canManage && <Link className="button-secondary" to={`/services/${value.id}/edit`}>Edit</Link>}
            {canManage && (value.active
              ? <button className="button-secondary" onClick={() => pause.mutate()}>Pause</button>
              : <button className="button-secondary" onClick={() => resume.mutate()}>Resume</button>)}
          </div>
        </section>
        {check.data && (
          <section className={`check-result ${check.data.success ? 'check-success' : 'check-failure'}`}>
            <div><strong>{check.data.success ? 'Check passed' : 'Check failed'}</strong><span>{check.data.responseTimeMilliseconds} ms · HTTP {check.data.statusCode ?? '—'}</span></div>
            <p>{check.data.errorMessage ?? (check.data.degraded ? 'Healthy, but above the degraded latency threshold.' : 'All configured expectations passed.')}</p>
            <small>Manual checks do not change threshold-based service status.</small>
          </section>
        )}
        {check.error && <div className="notice">The manual check could not be completed.</div>}
        <section className="detail-grid">
          <article className="workspace-card">
            <p className="eyebrow">Request</p><h2>Endpoint configuration</h2>
            <dl className="detail-list">
              <div><dt>Method</dt><dd>{value.httpMethod}</dd></div>
              <div><dt>Expected status</dt><dd>{value.expectedStatusCode}</dd></div>
              <div><dt>Timeout</dt><dd>{value.timeoutMilliseconds} ms</dd></div>
              <div><dt>Response text</dt><dd>{value.expectedResponseText || 'Not configured'}</dd></div>
              <div><dt>JSON expectation</dt><dd>{value.expectedJsonPath ? `${value.expectedJsonPath} = ${value.expectedJsonValue}` : 'Not configured'}</dd></div>
            </dl>
          </article>
          <article className="workspace-card">
            <p className="eyebrow">Thresholds</p><h2>Monitoring policy</h2>
            <dl className="detail-list">
              <div><dt>Check interval</dt><dd>{value.checkIntervalSeconds} seconds</dd></div>
              <div><dt>Failure threshold</dt><dd>{value.failureThreshold}</dd></div>
              <div><dt>Recovery threshold</dt><dd>{value.recoveryThreshold}</dd></div>
              <div><dt>Degraded latency</dt><dd>{value.degradedLatencyThresholdMilliseconds} ms</dd></div>
              <div><dt>Last checked</dt><dd>{value.lastCheckedAt ? new Date(value.lastCheckedAt).toLocaleString() : 'Never'}</dd></div>
            </dl>
          </article>
        </section>
        {value.description && <section className="workspace-card service-description"><p className="eyebrow">Description</p><p>{value.description}</p></section>}
        {canManage && <section className="danger-zone"><div><h2>Delete service</h2><p>Soft-delete this service from the active inventory.</p></div><button className="button-danger" disabled={remove.isPending} onClick={() => remove.mutate()}>Delete service</button></section>}
      </main>
    </div>
  )
}
