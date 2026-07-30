import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/ApiError'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { useAuth } from '../features/auth/useAuth'
import { useOrganizations } from '../features/organizations/useOrganizations'
import * as serviceApi from '../features/services/serviceApi'
import type { ServiceInput } from '../features/services/types'

const defaults: ServiceInput = {
  name: '',
  description: '',
  serviceType: 'HTTPS',
  url: 'https://',
  httpMethod: 'GET',
  expectedStatusCode: 200,
  expectedResponseText: '',
  expectedJsonPath: '',
  expectedJsonValue: '',
  timeoutMilliseconds: 5000,
  checkIntervalSeconds: 60,
  failureThreshold: 3,
  recoveryThreshold: 2,
  degradedLatencyThresholdMilliseconds: 1000,
}

export function ServiceFormPage() {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const { serviceId } = useParams()
  const navigate = useNavigate()
  const [form, setForm] = useState<ServiceInput>(defaults)
  const [error, setError] = useState('')
  const organizationId = currentOrganization?.id
  const existing = useQuery({
    queryKey: ['service', organizationId, serviceId],
    queryFn: () =>
      serviceApi.getService(auth.accessToken!, organizationId!, serviceId!),
    enabled: Boolean(organizationId && serviceId),
  })

  useEffect(() => {
    if (existing.data) {
      const service = existing.data
      setForm({
        name: service.name,
        description: service.description ?? '',
        serviceType: service.serviceType as 'HTTP' | 'HTTPS',
        url: service.url,
        httpMethod: service.httpMethod,
        expectedStatusCode: service.expectedStatusCode,
        expectedResponseText: service.expectedResponseText ?? '',
        expectedJsonPath: service.expectedJsonPath ?? '',
        expectedJsonValue: service.expectedJsonValue ?? '',
        timeoutMilliseconds: service.timeoutMilliseconds,
        checkIntervalSeconds: service.checkIntervalSeconds,
        failureThreshold: service.failureThreshold,
        recoveryThreshold: service.recoveryThreshold,
        degradedLatencyThresholdMilliseconds:
          service.degradedLatencyThresholdMilliseconds,
      })
    }
  }, [existing.data])

  const save = useMutation({
    mutationFn: () =>
      serviceId
        ? serviceApi.updateService(
            auth.accessToken!,
            organizationId!,
            serviceId,
            form,
          )
        : serviceApi.createService(auth.accessToken!, organizationId!, form),
    onSuccess: (service) => navigate(`/services/${service.id}`, { replace: true }),
    onError: (failure) =>
      setError(
        failure instanceof ApiError
          ? failure.message
          : 'The service could not be saved.',
      ),
  })

  function set<K extends keyof ServiceInput>(key: K, value: ServiceInput[K]) {
    setForm((current) => ({ ...current, [key]: value }))
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    save.mutate()
  }

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main service-form-main">
        <Link className="back-link" to={serviceId ? `/services/${serviceId}` : '/services'}>
          ← Back to services
        </Link>
        <div className="form-page-heading">
          <p className="eyebrow">{serviceId ? 'Edit service' : 'New service'}</p>
          <h1>{serviceId ? 'Update monitoring rules.' : 'Register an endpoint.'}</h1>
          <p>Configure safe HTTP expectations and outage thresholds.</p>
        </div>
        <form className="service-form" onSubmit={submit}>
          <FormSection title="Endpoint" description="What PulseOps will request.">
            <div className="form-grid">
              <label>
                Service name
                <input required maxLength={160} value={form.name} onChange={(e) => set('name', e.target.value)} />
              </label>
              <label>
                Protocol
                <select value={form.serviceType} onChange={(e) => set('serviceType', e.target.value as 'HTTP' | 'HTTPS')}>
                  <option value="HTTPS">HTTPS</option>
                  <option value="HTTP">HTTP</option>
                </select>
              </label>
              <label className="form-span-2">
                URL
                <input required type="url" value={form.url} onChange={(e) => set('url', e.target.value)} />
              </label>
              <label className="form-span-2">
                Description <span className="optional">Optional</span>
                <textarea maxLength={1000} value={form.description} onChange={(e) => set('description', e.target.value)} />
              </label>
            </div>
          </FormSection>
          <FormSection title="Response expectations" description="How a healthy response is recognized.">
            <div className="form-grid">
              <label>
                HTTP method
                <select value={form.httpMethod} onChange={(e) => set('httpMethod', e.target.value as 'GET' | 'HEAD')}>
                  <option value="GET">GET</option>
                  <option value="HEAD">HEAD</option>
                </select>
              </label>
              <NumberField label="Expected status" value={form.expectedStatusCode} min={100} max={599} onChange={(v) => set('expectedStatusCode', v)} />
              <label className="form-span-2">
                Expected response text <span className="optional">Optional</span>
                <input maxLength={500} value={form.expectedResponseText} onChange={(e) => set('expectedResponseText', e.target.value)} />
              </label>
              <label>
                JSON path <span className="optional">Optional</span>
                <input placeholder="$.status" value={form.expectedJsonPath} onChange={(e) => set('expectedJsonPath', e.target.value)} />
              </label>
              <label>
                Expected JSON value
                <input value={form.expectedJsonValue} onChange={(e) => set('expectedJsonValue', e.target.value)} />
              </label>
            </div>
          </FormSection>
          <FormSection title="Monitoring policy" description="Scheduled and manual checks apply these thresholds.">
            <div className="form-grid form-grid-3">
              <NumberField label="Timeout (ms)" value={form.timeoutMilliseconds} min={250} max={60000} onChange={(v) => set('timeoutMilliseconds', v)} />
              <NumberField label="Interval (seconds)" value={form.checkIntervalSeconds} min={30} max={86400} onChange={(v) => set('checkIntervalSeconds', v)} />
              <NumberField label="Degraded after (ms)" value={form.degradedLatencyThresholdMilliseconds} min={1} max={60000} onChange={(v) => set('degradedLatencyThresholdMilliseconds', v)} />
              <NumberField label="Failures before down" value={form.failureThreshold} min={1} max={20} onChange={(v) => set('failureThreshold', v)} />
              <NumberField label="Successes before recovery" value={form.recoveryThreshold} min={1} max={20} onChange={(v) => set('recoveryThreshold', v)} />
            </div>
          </FormSection>
          {error && <div className="form-error">{error}</div>}
          <div className="form-actions">
            <Link className="button-secondary" to="/services">Cancel</Link>
            <button className="button" disabled={save.isPending} type="submit">
              {save.isPending ? 'Saving…' : serviceId ? 'Save changes' : 'Create service'}
            </button>
          </div>
        </form>
      </main>
    </div>
  )
}

function FormSection({ title, description, children }: { title: string; description: string; children: React.ReactNode }) {
  return <section className="service-form-section"><div><h2>{title}</h2><p>{description}</p></div><div>{children}</div></section>
}

function NumberField({ label, value, min, max, onChange }: { label: string; value: number; min: number; max: number; onChange: (value: number) => void }) {
  return <label>{label}<input required type="number" value={value} min={min} max={max} onChange={(e) => onChange(Number(e.target.value))} /></label>
}
