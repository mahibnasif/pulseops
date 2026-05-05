import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { ServiceStatusBadge } from '../components/services/ServiceStatusBadge'
import * as analyticsApi from '../features/analytics/analyticsApi'
import type {
  AnalyticsRange,
  AnalyticsSnapshot,
} from '../features/analytics/types'
import { useAuth } from '../features/auth/useAuth'
import { useOrganizations } from '../features/organizations/useOrganizations'

const DAY_MILLISECONDS = 24 * 60 * 60 * 1000
const PRESETS = [
  { label: '24 hours', days: 1 },
  { label: '7 days', days: 7 },
  { label: '30 days', days: 30 },
  { label: '90 days', days: 90 },
] as const

export function AnalyticsPage() {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const organizationId = currentOrganization?.id
  const [selectedDays, setSelectedDays] = useState(30)
  const [range, setRange] = useState<AnalyticsRange>(() => rangeForDays(30))
  const [customFrom, setCustomFrom] = useState(() => dateInput(range.from))
  const [customThrough, setCustomThrough] = useState(() =>
    dateInput(new Date(Date.parse(range.to) - DAY_MILLISECONDS).toISOString()),
  )
  const [rangeError, setRangeError] = useState('')
  const analytics = useQuery({
    queryKey: ['analytics', organizationId, range.from, range.to],
    queryFn: () =>
      analyticsApi.getAnalytics(
        auth.accessToken!,
        organizationId!,
        range,
      ),
    enabled: Boolean(organizationId),
  })

  function selectPreset(days: number) {
    const nextRange = rangeForDays(days)
    setSelectedDays(days)
    setRange(nextRange)
    setCustomFrom(dateInput(nextRange.from))
    setCustomThrough(
      dateInput(
        new Date(Date.parse(nextRange.to) - DAY_MILLISECONDS).toISOString(),
      ),
    )
    setRangeError('')
  }

  function applyCustomRange() {
    const from = new Date(`${customFrom}T00:00:00.000Z`)
    const to = new Date(`${customThrough}T00:00:00.000Z`)
    to.setUTCDate(to.getUTCDate() + 1)
    const duration = to.getTime() - from.getTime()
    if (
      Number.isNaN(duration) ||
      duration <= 0 ||
      duration > 366 * DAY_MILLISECONDS
    ) {
      setRangeError('Choose a positive UTC range of 366 days or less.')
      return
    }
    setSelectedDays(0)
    setRange({ from: from.toISOString(), to: to.toISOString() })
    setRangeError('')
  }

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main analytics-main">
        <section className="page-heading analytics-heading">
          <div>
            <p className="eyebrow">Reliability analytics</p>
            <h1>Operational performance</h1>
            <p>
              Health, latency, and incident trends for{' '}
              {currentOrganization?.name ?? 'this organization'}.
            </p>
          </div>
          <span className="analytics-timezone">UTC reporting</span>
        </section>

        <section className="analytics-filters" aria-label="Analytics date range">
          <div className="preset-group" aria-label="Quick ranges">
            {PRESETS.map((preset) => (
              <button
                className={selectedDays === preset.days ? 'active' : ''}
                key={preset.days}
                type="button"
                onClick={() => selectPreset(preset.days)}
              >
                {preset.label}
              </button>
            ))}
          </div>
          <div className="custom-range">
            <label>
              From
              <input
                type="date"
                value={customFrom}
                onChange={(event) => setCustomFrom(event.target.value)}
              />
            </label>
            <label>
              Through
              <input
                type="date"
                value={customThrough}
                onChange={(event) => setCustomThrough(event.target.value)}
              />
            </label>
            <button
              className="button-secondary"
              type="button"
              onClick={applyCustomRange}
            >
              Apply
            </button>
          </div>
          {rangeError && <p className="field-error">{rangeError}</p>}
        </section>

        {analytics.isLoading ? (
          <div className="loading-panel">Calculating analytics…</div>
        ) : analytics.error ? (
          <div className="notice">
            Analytics could not be loaded for this range.
          </div>
        ) : analytics.data ? (
          <AnalyticsContent snapshot={analytics.data} />
        ) : null}
      </main>
    </div>
  )
}

function AnalyticsContent({ snapshot }: { snapshot: AnalyticsSnapshot }) {
  const healthTrend = snapshot.healthTrend.map((point) => ({
    ...point,
    label: bucketLabel(point.bucketStart, snapshot.range.bucket),
  }))
  const incidentTrend = snapshot.incidentTrend.map((point) => ({
    ...point,
    label: bucketLabel(point.bucketStart, snapshot.range.bucket),
  }))

  return (
    <>
      <section className="analytics-summary" aria-label="Analytics summary">
        <MetricCard
          label="Sample uptime"
          value={percentage(snapshot.reliability.uptimePercentage)}
          detail={`${snapshot.reliability.successfulChecks.toLocaleString()} of ${snapshot.reliability.totalChecks.toLocaleString()} checks succeeded`}
          tone="green"
        />
        <MetricCard
          label="Average response"
          value={milliseconds(
            snapshot.reliability.averageResponseTimeMilliseconds,
          )}
          detail={`P95 ${milliseconds(snapshot.reliability.p95ResponseTimeMilliseconds)}`}
          tone="violet"
        />
        <MetricCard
          label="Active incidents"
          value={snapshot.incidents.active.toLocaleString()}
          detail={`${snapshot.incidents.critical.toLocaleString()} critical in range`}
          tone="red"
        />
        <MetricCard
          label="Mean time to acknowledge"
          value={duration(snapshot.incidents.meanTimeToAcknowledgeMinutes)}
          detail={`${snapshot.incidents.total.toLocaleString()} incidents detected`}
          tone="amber"
        />
        <MetricCard
          label="Mean time to resolve"
          value={duration(snapshot.incidents.meanTimeToResolveMinutes)}
          detail={`Longest ${duration(snapshot.incidents.longestIncidentMinutes)}`}
          tone="blue"
        />
        <MetricCard
          label="Services monitored"
          value={snapshot.services.total.toLocaleString()}
          detail={`${snapshot.services.down} down · ${snapshot.services.degraded} degraded`}
          tone="slate"
        />
      </section>

      <section className="status-distribution" aria-label="Current service status">
        <span>Current status</span>
        <strong>{snapshot.services.operational} operational</strong>
        <strong>{snapshot.services.degraded} degraded</strong>
        <strong>{snapshot.services.down} down</strong>
        <strong>{snapshot.services.paused} paused</strong>
        <strong>{snapshot.services.unknown} unknown</strong>
      </section>

      <div className="analytics-grid">
        <ChartPanel
          title="Uptime trend"
          detail="Successful samples as a percentage of all check samples."
          empty={healthTrend.length === 0}
        >
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={healthTrend}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="label" minTickGap={24} />
              <YAxis domain={[0, 100]} unit="%" width={42} />
              <Tooltip />
              <Line
                dataKey="uptimePercentage"
                name="Uptime"
                stroke="#067647"
                strokeWidth={3}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </ChartPanel>
        <ChartPanel
          title="Response time"
          detail="Average observed latency per reporting bucket."
          empty={healthTrend.length === 0}
        >
          <ResponsiveContainer width="100%" height={280}>
            <LineChart data={healthTrend}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="label" minTickGap={24} />
              <YAxis unit="ms" width={54} />
              <Tooltip />
              <Line
                dataKey="averageResponseTimeMilliseconds"
                name="Average response"
                stroke="#6941c6"
                strokeWidth={3}
                dot={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </ChartPanel>
        <ChartPanel
          title="Incidents by severity"
          detail="Incidents detected during the selected range."
          empty={snapshot.incidentsBySeverity.length === 0}
        >
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={snapshot.incidentsBySeverity}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="severity" />
              <YAxis allowDecimals={false} width={36} />
              <Tooltip />
              <Bar
                dataKey="count"
                name="Incidents"
                fill="#d92d20"
                radius={[6, 6, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>
        <ChartPanel
          title="Incident trend"
          detail="Detected and resolved incidents by reporting bucket."
          empty={incidentTrend.length === 0}
        >
          <ResponsiveContainer width="100%" height={280}>
            <BarChart data={incidentTrend}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="label" minTickGap={24} />
              <YAxis allowDecimals={false} width={36} />
              <Tooltip />
              <Legend />
              <Bar
                dataKey="totalIncidents"
                name="Detected"
                fill="#f79009"
                radius={[5, 5, 0, 0]}
              />
              <Bar
                dataKey="resolvedIncidents"
                name="Resolved"
                fill="#12b76a"
                radius={[5, 5, 0, 0]}
              />
            </BarChart>
          </ResponsiveContainer>
        </ChartPanel>
      </div>

      <section className="analytics-table-panel">
        <div className="chart-heading">
          <div>
            <h2>Service reliability</h2>
            <p>Sample availability and latency by active service.</p>
          </div>
        </div>
        {snapshot.serviceReliability.length ? (
          <div className="service-table-wrap">
            <table className="service-table analytics-table">
              <thead>
                <tr>
                  <th>Service</th>
                  <th>Status</th>
                  <th>Uptime</th>
                  <th>Checks</th>
                  <th>Failures</th>
                  <th>Average</th>
                  <th>P95</th>
                  <th>Incidents</th>
                </tr>
              </thead>
              <tbody>
                {snapshot.serviceReliability.map((service) => (
                  <tr key={service.serviceId}>
                    <td>
                      <Link to={`/services/${service.serviceId}`}>
                        <strong>{service.serviceName}</strong>
                      </Link>
                    </td>
                    <td>
                      <ServiceStatusBadge status={service.status} />
                    </td>
                    <td>{percentage(service.uptimePercentage)}</td>
                    <td>{service.totalChecks.toLocaleString()}</td>
                    <td>{service.failedChecks.toLocaleString()}</td>
                    <td>
                      {milliseconds(service.averageResponseTimeMilliseconds)}
                    </td>
                    <td>
                      {milliseconds(service.p95ResponseTimeMilliseconds)}
                    </td>
                    <td>{service.incidentCount.toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="chart-empty">No services are registered yet.</div>
        )}
        <p className="analytics-method">
          Uptime is an observation-based approximation:{' '}
          {snapshot.range.uptimeMethod}. Buckets and date boundaries use UTC.
        </p>
      </section>
    </>
  )
}

function MetricCard({
  label,
  value,
  detail,
  tone,
}: {
  label: string
  value: string
  detail: string
  tone: string
}) {
  return (
    <article className={`metric-card metric-card-${tone}`}>
      <p>{label}</p>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  )
}

function ChartPanel({
  title,
  detail,
  empty,
  children,
}: {
  title: string
  detail: string
  empty: boolean
  children: React.ReactNode
}) {
  return (
    <section className="chart-panel" aria-label={title}>
      <div className="chart-heading">
        <div>
          <h2>{title}</h2>
          <p>{detail}</p>
        </div>
      </div>
      {empty ? (
        <div className="chart-empty">
          No observations in this date range.
        </div>
      ) : (
        children
      )}
    </section>
  )
}

function rangeForDays(days: number): AnalyticsRange {
  const to = new Date()
  const from = new Date(to.getTime() - days * DAY_MILLISECONDS)
  return { from: from.toISOString(), to: to.toISOString() }
}

function dateInput(value: string) {
  return value.slice(0, 10)
}

function bucketLabel(value: string, bucket: 'HOUR' | 'DAY') {
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    ...(bucket === 'HOUR' ? { hour: 'numeric' } : {}),
    timeZone: 'UTC',
  }).format(new Date(value))
}

function percentage(value: number | null) {
  return value === null ? 'No data' : `${value.toFixed(2)}%`
}

function milliseconds(value: number | null) {
  return value === null ? 'No data' : `${Math.round(value).toLocaleString()} ms`
}

function duration(value: number | null) {
  if (value === null) return 'No data'
  if (value < 60) return `${Math.round(value)} min`
  if (value < 1440) return `${(value / 60).toFixed(1)} hr`
  return `${(value / 1440).toFixed(1)} days`
}
