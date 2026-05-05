import { afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './features/auth/AuthProvider'
import type { AuthSession } from './features/auth/types'
import type { AnalyticsSnapshot } from './features/analytics/types'
import type { Organization } from './features/organizations/types'

const session: AuthSession = {
  accessToken: 'analytics-test-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: '11111111-1111-4111-8111-111111111111',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.com',
    emailVerified: true,
    accountStatus: 'ACTIVE',
  },
}

const organization: Organization = {
  id: '22222222-2222-4222-8222-222222222222',
  name: 'Platform Team',
  slug: 'platform-team',
  description: null,
  ownerId: session.user.id,
  currentUserRole: 'ENGINEER',
  createdAt: '2026-07-01T00:00:00Z',
  updatedAt: '2026-07-01T00:00:00Z',
}

const snapshot: AnalyticsSnapshot = {
  range: {
    from: '2026-07-01T00:00:00Z',
    to: '2026-07-31T00:00:00Z',
    bucket: 'DAY',
    timezone: 'UTC',
    uptimeMethod:
      'successful health-check samples / total health-check samples',
  },
  services: {
    total: 3,
    operational: 2,
    degraded: 0,
    down: 1,
    paused: 0,
    unknown: 0,
  },
  reliability: {
    totalChecks: 120,
    successfulChecks: 118,
    failedChecks: 2,
    uptimePercentage: 98.33,
    averageResponseTimeMilliseconds: 142,
    p50ResponseTimeMilliseconds: 120,
    p95ResponseTimeMilliseconds: 310,
  },
  incidents: {
    total: 4,
    active: 1,
    critical: 1,
    meanTimeToAcknowledgeMinutes: 7,
    meanTimeToResolveMinutes: 85,
    longestIncidentMinutes: 180,
  },
  healthTrend: [
    {
      bucketStart: '2026-07-29T00:00:00Z',
      totalChecks: 60,
      successfulChecks: 59,
      failedChecks: 1,
      uptimePercentage: 98.33,
      averageResponseTimeMilliseconds: 130,
    },
    {
      bucketStart: '2026-07-30T00:00:00Z',
      totalChecks: 60,
      successfulChecks: 59,
      failedChecks: 1,
      uptimePercentage: 98.33,
      averageResponseTimeMilliseconds: 154,
    },
  ],
  incidentTrend: [
    {
      bucketStart: '2026-07-30T00:00:00Z',
      totalIncidents: 4,
      resolvedIncidents: 3,
    },
  ],
  incidentsBySeverity: [
    { severity: 'CRITICAL', count: 1 },
    { severity: 'HIGH', count: 2 },
    { severity: 'MEDIUM', count: 1 },
  ],
  serviceReliability: [
    {
      serviceId: '33333333-3333-4333-8333-333333333333',
      serviceName: 'Checkout API',
      status: 'DOWN',
      totalChecks: 120,
      failedChecks: 2,
      uptimePercentage: 98.33,
      averageResponseTimeMilliseconds: 142,
      p95ResponseTimeMilliseconds: 310,
      incidentCount: 4,
    },
  ],
}

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

function renderApp() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/analytics']}>
        <AuthProvider initialSession={session}>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function stubAnalytics() {
  const analyticsRequests: string[] = []
  const fetchMock = vi.fn(async (input: string | URL | Request) => {
    const path = String(input)
    if (path === '/api/v1/organizations') {
      return jsonResponse([organization])
    }
    if (path.includes(`/organizations/${organization.id}/analytics?`)) {
      analyticsRequests.push(path)
      return jsonResponse(snapshot)
    }
    if (path.endsWith(`/organizations/${organization.id}/events`)) {
      return new Response('', {
        headers: { 'Content-Type': 'text/event-stream' },
      })
    }
    throw new Error(`Unexpected request: ${path}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return analyticsRequests
}

describe('PulseOps analytics workspace', () => {
  it('renders reliability, incident metrics, charts, and service detail', async () => {
    stubAnalytics()
    renderApp()

    expect(
      await screen.findByRole('heading', {
        name: /operational performance/i,
      }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Checkout API')).toBeInTheDocument()
    expect(screen.getAllByText('98.33%')).toHaveLength(2)
    expect(screen.getAllByText('142 ms')).toHaveLength(2)
    expect(
      screen.getByRole('region', { name: /uptime trend/i }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('region', { name: /incidents by severity/i }),
    ).toBeInTheDocument()
    expect(screen.getByText(/observation-based approximation/i)).toBeVisible()
  })

  it('requests a new bounded range when a preset changes', async () => {
    const requests = stubAnalytics()
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('Checkout API')
    expect(requests).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: /90 days/i }))
    await waitFor(() => expect(requests).toHaveLength(2))
    const url = new URL(requests[1], 'http://pulseops.test')
    const from = Date.parse(url.searchParams.get('from')!)
    const to = Date.parse(url.searchParams.get('to')!)
    expect(Math.round((to - from) / 86_400_000)).toBe(90)
  })

  it('validates custom date ranges before requesting the API', async () => {
    const requests = stubAnalytics()
    const user = userEvent.setup()
    renderApp()
    await screen.findByText('Checkout API')

    await user.clear(screen.getByLabelText(/^from$/i))
    await user.type(screen.getByLabelText(/^from$/i), '2026-08-01')
    await user.clear(screen.getByLabelText(/^through$/i))
    await user.type(screen.getByLabelText(/^through$/i), '2026-07-01')
    await user.click(screen.getByRole('button', { name: /^apply$/i }))

    expect(
      screen.getByText(/positive UTC range of 366 days or less/i),
    ).toBeVisible()
    expect(requests).toHaveLength(1)
  })
})

function jsonResponse(value: unknown) {
  return new Response(JSON.stringify(value), {
    headers: { 'Content-Type': 'application/json' },
  })
}
