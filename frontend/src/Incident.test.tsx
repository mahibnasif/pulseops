import { afterEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './features/auth/AuthProvider'
import type { AuthSession } from './features/auth/types'
import type { IncidentDetails, IncidentPage } from './features/incidents/types'
import type {
  Organization,
  OrganizationMember,
} from './features/organizations/types'
import type { MonitoredService, ServicePage } from './features/services/types'

const session: AuthSession = {
  accessToken: 'incident-test-token',
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
  createdAt: '2026-07-30T00:00:00Z',
  updatedAt: '2026-07-30T00:00:00Z',
}

const service: MonitoredService = {
  id: '33333333-3333-4333-8333-333333333333',
  organizationId: organization.id,
  name: 'Checkout API',
  description: null,
  serviceType: 'HTTPS',
  url: 'https://example.com/health',
  httpMethod: 'GET',
  expectedStatusCode: 200,
  expectedResponseText: null,
  expectedJsonPath: null,
  expectedJsonValue: null,
  timeoutMilliseconds: 5000,
  checkIntervalSeconds: 60,
  failureThreshold: 3,
  recoveryThreshold: 2,
  degradedLatencyThresholdMilliseconds: 1000,
  status: 'DOWN',
  active: true,
  createdBy: session.user.id,
  createdAt: '2026-07-30T00:00:00Z',
  updatedAt: '2026-07-30T00:00:00Z',
  lastCheckedAt: '2026-07-30T00:05:00Z',
  lastSuccessfulCheckAt: null,
  lastFailureAt: '2026-07-30T00:05:00Z',
  nextCheckAt: '2026-07-30T00:06:00Z',
  consecutiveFailures: 3,
  consecutiveSuccesses: 0,
  lastStatusChangedAt: '2026-07-30T00:05:00Z',
}

const incident = {
  id: '44444444-4444-4444-8444-444444444444',
  organizationId: organization.id,
  serviceId: service.id,
  title: 'Checkout API is down',
  description: 'Monitoring crossed the configured failure threshold.',
  severity: 'HIGH' as const,
  status: 'OPEN' as const,
  source: 'AUTOMATIC_MONITORING' as const,
  assignedUserId: null,
  detectedAt: '2026-07-30T00:05:00Z',
  acknowledgedAt: null,
  resolvedAt: null,
  rootCause: null,
  resolutionSummary: null,
  createdBy: session.user.id,
  createdAt: '2026-07-30T00:05:00Z',
  updatedAt: '2026-07-30T00:05:00Z',
}

const incidentPage: IncidentPage = {
  content: [incident],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

const servicePage: ServicePage = {
  content: [service],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

const members: OrganizationMember[] = [
  {
    membershipId: '55555555-5555-4555-8555-555555555555',
    userId: session.user.id,
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: session.user.email,
    role: 'ENGINEER',
    status: 'ACTIVE',
    joinedAt: '2026-07-30T00:00:00Z',
  },
]

const details: IncidentDetails = {
  incident,
  comments: [],
  timeline: [
    {
      id: '66666666-6666-4666-8666-666666666666',
      eventType: 'INCIDENT_CREATED',
      actorUserId: null,
      message: 'Incident created after the service entered DOWN.',
      oldValue: null,
      newValue: 'OPEN',
      createdAt: incident.createdAt,
    },
  ],
}

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

function renderApp(path: string) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider initialSession={session}>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function response(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function stubIncidentWorkspace(currentOrganization = organization) {
  const root = `/api/v1/organizations/${currentOrganization.id}`
  const fetchMock = vi.fn(
    async (input: string | URL | Request, options?: RequestInit) => {
      const path = String(input)
      if (path === '/api/v1/organizations') return response([currentOrganization])
      if (path === '/api/v1/invitations') return response([])
      if (path === `${root}/members`) return response(members)
      if (path.startsWith(`${root}/services?`)) return response(servicePage)
      if (path === `${root}/services/${service.id}`) return response(service)
      if (path === `${root}/incidents/${incident.id}`) return response(details)
      if (path.startsWith(`${root}/incidents?`)) return response(incidentPage)
      if (path === `${root}/incidents` && options?.method === 'POST') {
        return response({ ...incident, source: 'MANUAL' }, 201)
      }
      throw new Error(`Unexpected request: ${path}`)
    },
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('PulseOps incident response workspace', () => {
  it('shows the searchable incident register with operational context', async () => {
    stubIncidentWorkspace()
    const user = userEvent.setup()
    renderApp('/incidents')

    expect(
      await screen.findByRole('heading', { name: /^incidents$/i }),
    ).toBeInTheDocument()
    expect(await screen.findByText('Checkout API is down')).toBeInTheDocument()
    expect(screen.getByText(/created by monitoring/i)).toBeInTheDocument()
    await user.selectOptions(
      screen.getByRole('combobox', { name: /filter by severity/i }),
      'HIGH',
    )
    expect(screen.getByText('Checkout API is down')).toBeInTheDocument()
  })

  it('renders the response timeline and engineer controls', async () => {
    stubIncidentWorkspace()
    renderApp(`/incidents/${incident.id}`)

    expect(
      await screen.findByRole('heading', { name: incident.title }),
    ).toBeInTheDocument()
    expect(
      screen.getByText(/incident created after the service entered down/i),
    ).toBeInTheDocument()
    expect(screen.getByLabelText(/incident assignee/i)).toBeEnabled()
    expect(screen.getByRole('button', { name: /mark resolved/i })).toBeEnabled()
    expect(screen.getByLabelText(/add response note/i)).toBeInTheDocument()
  })

  it('keeps incident details read-only for viewers', async () => {
    const viewerOrganization = {
      ...organization,
      currentUserRole: 'VIEWER' as const,
    }
    stubIncidentWorkspace(viewerOrganization)
    renderApp(`/incidents/${incident.id}`)

    expect(
      await screen.findByRole('heading', { name: incident.title }),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText(/incident assignee/i)).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /mark resolved/i }),
    ).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/add response note/i)).not.toBeInTheDocument()
  })
})
