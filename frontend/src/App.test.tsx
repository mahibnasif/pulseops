import { afterEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './features/auth/AuthProvider'
import type { AuthSession } from './features/auth/types'
import type { Organization } from './features/organizations/types'
import type {
  ManualCheckResult,
  MonitoredService,
  ServicePage,
} from './features/services/types'

const authenticatedSession: AuthSession = {
  accessToken: 'test-access-token',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: {
    id: 'f72bcf53-13db-4d6b-8a62-dd994e3184b7',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@example.com',
    emailVerified: false,
    accountStatus: 'ACTIVE',
  },
}

const platformOrganization: Organization = {
  id: '69100b0b-9d78-40f0-944b-659382381f64',
  name: 'Platform Team',
  slug: 'platform-team',
  description: 'Production engineering',
  ownerId: authenticatedSession.user.id,
  currentUserRole: 'ADMIN',
  createdAt: '2026-07-30T00:00:00Z',
  updatedAt: '2026-07-30T00:00:00Z',
}

const productOrganization: Organization = {
  ...platformOrganization,
  id: 'f3693c50-08d7-48cf-a310-d2c89e55338b',
  name: 'Product Team',
  slug: 'product-team',
  description: 'Customer product engineering',
  currentUserRole: 'ENGINEER',
}

const publicApiService: MonitoredService = {
  id: '203ec28e-a852-4931-b6ac-41575262ecbe',
  organizationId: platformOrganization.id,
  name: 'Public API',
  description: 'Customer-facing API health endpoint',
  serviceType: 'HTTPS',
  url: 'https://api.example.com/health',
  httpMethod: 'GET',
  expectedStatusCode: 200,
  expectedResponseText: 'healthy',
  expectedJsonPath: null,
  expectedJsonValue: null,
  timeoutMilliseconds: 5000,
  checkIntervalSeconds: 60,
  failureThreshold: 3,
  recoveryThreshold: 2,
  degradedLatencyThresholdMilliseconds: 1000,
  status: 'UNKNOWN',
  active: true,
  createdBy: authenticatedSession.user.id,
  createdAt: '2026-07-30T00:00:00Z',
  updatedAt: '2026-07-30T00:00:00Z',
  lastCheckedAt: null,
  lastSuccessfulCheckAt: null,
  lastFailureAt: null,
  nextCheckAt: '2026-07-30T00:01:00Z',
  consecutiveFailures: 0,
  consecutiveSuccesses: 0,
  lastStatusChangedAt: '2026-07-30T00:00:00Z',
}

const servicePage: ServicePage = {
  content: [publicApiService],
  page: 0,
  size: 50,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

const emptyCheckPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
}

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

function renderApp(path: string, session: AuthSession | null = null) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <AuthProvider initialSession={session}>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function jsonResponse(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function stubWorkspaceApi(organizations: Organization[] = []) {
  const fetchMock = vi.fn(
    async (input: string | URL | Request, options?: RequestInit) => {
      const path = String(input)
      if (path === '/api/v1/organizations' && options?.method !== 'POST') {
        return jsonResponse(organizations)
      }
      if (path === '/api/v1/invitations') {
        return jsonResponse([])
      }
      throw new Error(`Unexpected request: ${path}`)
    },
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('PulseOps authentication routes', () => {
  it('renders the public product landing page', () => {
    renderApp('/')

    expect(
      screen.getByRole('heading', {
        name: /know when services fail/i,
      }),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('link', { name: /start monitoring/i }),
    ).toHaveAttribute('href', '/register')
  })

  it('redirects an unauthenticated visitor from the dashboard to sign in', () => {
    renderApp('/dashboard')

    expect(
      screen.getByRole('heading', { name: /sign in to pulseops/i }),
    ).toBeInTheDocument()
  })

  it('submits credentials and opens the protected dashboard', async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request) => {
        const path = String(input)
        if (path === '/api/v1/auth/login') {
          return jsonResponse(authenticatedSession)
        }
        if (path === '/api/v1/organizations') {
          return jsonResponse([platformOrganization])
        }
        if (path === '/api/v1/invitations') {
          return jsonResponse([])
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(screen.getByLabelText(/email address/i), 'ada@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'Correct-Horse-42')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(
      await screen.findByRole('heading', { name: /platform team/i }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/auth/login',
      expect.objectContaining({
        method: 'POST',
        credentials: 'include',
      }),
    )
  })

  it('shows an authenticated user without exposing token storage controls', () => {
    stubWorkspaceApi([platformOrganization])
    renderApp('/dashboard', authenticatedSession)

    expect(
      screen.getByText(/loading your organizations/i),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign out/i })).toBeEnabled()
    expect(screen.queryByText('test-access-token')).not.toBeInTheDocument()
  })

  it('switches between organizations in the protected workspace', async () => {
    stubWorkspaceApi([platformOrganization, productOrganization])
    const user = userEvent.setup()
    renderApp('/dashboard', authenticatedSession)

    expect(
      await screen.findByRole('heading', { name: /platform team/i }),
    ).toBeInTheDocument()
    await user.selectOptions(
      screen.getByRole('combobox', { name: /organization/i }),
      productOrganization.id,
    )

    expect(
      screen.getByRole('heading', { name: /product team/i }),
    ).toBeInTheDocument()
    expect(window.localStorage.getItem('pulseops.selectedOrganizationId')).toBe(
      productOrganization.id,
    )
  })

  it('creates the first organization from the empty workspace', async () => {
    let organizations: Organization[] = []
    const fetchMock = vi.fn(
      async (input: string | URL | Request, options?: RequestInit) => {
        const path = String(input)
        if (path === '/api/v1/invitations') {
          return jsonResponse([])
        }
        if (path === '/api/v1/organizations' && options?.method === 'POST') {
          organizations = [platformOrganization]
          return jsonResponse(platformOrganization, 201)
        }
        if (path === '/api/v1/organizations') {
          return jsonResponse(organizations)
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp('/dashboard', authenticatedSession)

    expect(
      await screen.findByRole('heading', {
        name: /bring your team into pulseops/i,
      }),
    ).toBeInTheDocument()
    await user.type(
      screen.getByLabelText(/organization name/i),
      'Platform Team',
    )
    await user.click(
      screen.getByRole('button', { name: /create organization/i }),
    )

    expect(
      await screen.findByRole('heading', { name: /platform team/i }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/organizations',
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer test-access-token',
        }),
      }),
    )
  })

  it('accepts a pending invitation and enters the new organization', async () => {
    const invitation = {
      id: 'e73fa669-c670-4f5f-bded-d97fb1457e6b',
      organizationId: productOrganization.id,
      organizationName: productOrganization.name,
      email: authenticatedSession.user.email,
      role: 'ENGINEER',
      expiresAt: '2026-08-06T00:00:00Z',
      acceptedAt: null,
      createdAt: '2026-07-30T00:00:00Z',
    }
    let accepted = false
    const fetchMock = vi.fn(
      async (input: string | URL | Request, options?: RequestInit) => {
        const path = String(input)
        if (path === '/api/v1/organizations') {
          return jsonResponse(accepted ? [productOrganization] : [])
        }
        if (
          path === `/api/v1/invitations/${invitation.id}/accept` &&
          options?.method === 'POST'
        ) {
          accepted = true
          return jsonResponse(productOrganization)
        }
        if (path === '/api/v1/invitations') {
          return jsonResponse(accepted ? [] : [invitation])
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp('/dashboard', authenticatedSession)

    expect(
      await screen.findByText(productOrganization.name),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /accept/i }))

    expect(
      await screen.findByRole('heading', { name: /product team/i }),
    ).toBeInTheDocument()
  })

  it('shows membership settings as read-only for a viewer', async () => {
    const viewerOrganization: Organization = {
      ...platformOrganization,
      currentUserRole: 'VIEWER',
    }
    const fetchMock = vi.fn(
      async (input: string | URL | Request) => {
        const path = String(input)
        if (path === '/api/v1/organizations') {
          return jsonResponse([viewerOrganization])
        }
        if (
          path ===
          `/api/v1/organizations/${viewerOrganization.id}/members`
        ) {
          return jsonResponse([
            {
              membershipId: '5b7949e4-f0dc-45df-ad26-e7db924dd31e',
              userId: authenticatedSession.user.id,
              firstName: 'Ada',
              lastName: 'Lovelace',
              email: authenticatedSession.user.email,
              role: 'VIEWER',
              status: 'ACTIVE',
              joinedAt: '2026-07-30T00:00:00Z',
            },
          ])
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    renderApp(
      `/organizations/${viewerOrganization.id}/settings`,
      authenticatedSession,
    )

    expect(
      await screen.findByText(/read-only access to organization membership/i),
    ).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /create invitation/i }),
    ).not.toBeInTheDocument()
  })

  it('validates password confirmation before registration reaches the API', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp('/register')

    await user.type(screen.getByLabelText(/first name/i), 'Ada')
    await user.type(screen.getByLabelText(/last name/i), 'Lovelace')
    await user.type(screen.getByLabelText(/email address/i), 'ada@example.com')
    await user.type(
      screen.getByLabelText(/password/i, {
        selector: 'input[name="password"]',
      }),
      'Correct-Horse-42',
    )
    await user.type(
      screen.getByLabelText(/confirm password/i),
      'Different-Horse-42',
    )
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(screen.getByText(/passwords must match/i)).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

describe('PulseOps service management', () => {
  it('shows the organization service inventory to an administrator', async () => {
    const fetchMock = vi.fn(
      async (input: string | URL | Request) => {
        const path = String(input)
        if (path === '/api/v1/organizations') {
          return jsonResponse([platformOrganization])
        }
        if (path === '/api/v1/invitations') {
          return jsonResponse([])
        }
        if (
          path ===
          `/api/v1/organizations/${platformOrganization.id}/services?size=50`
        ) {
          return jsonResponse(servicePage)
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)

    renderApp('/services', authenticatedSession)

    expect(
      await screen.findByRole('heading', { name: /monitored services/i }),
    ).toBeInTheDocument()
    expect(
      await screen.findByRole('link', { name: /add service/i }),
    ).toBeVisible()
    expect(
      await screen.findByRole('link', { name: /public api/i }),
    ).toHaveAttribute(
      'href',
      `/services/${publicApiService.id}`,
    )
  })

  it('creates an HTTPS service and opens its details', async () => {
    const createdService = {
      ...publicApiService,
      name: 'Checkout API',
      url: 'https://checkout.example.com/health',
    }
    const fetchMock = vi.fn(
      async (input: string | URL | Request, options?: RequestInit) => {
        const path = String(input)
        if (path === '/api/v1/organizations') {
          return jsonResponse([platformOrganization])
        }
        if (path === '/api/v1/invitations') {
          return jsonResponse([])
        }
        if (
          path ===
            `/api/v1/organizations/${platformOrganization.id}/services` &&
          options?.method === 'POST'
        ) {
          return jsonResponse(createdService, 201)
        }
        if (
          path ===
          `/api/v1/organizations/${platformOrganization.id}/services/${publicApiService.id}`
        ) {
          return jsonResponse(createdService)
        }
        if (
          path ===
          `/api/v1/organizations/${platformOrganization.id}/services/${publicApiService.id}/checks?size=20`
        ) {
          return jsonResponse(emptyCheckPage)
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp('/services/new', authenticatedSession)

    await screen.findByRole('heading', { name: /register an endpoint/i })
    await user.type(screen.getByLabelText(/service name/i), 'Checkout API')
    await user.clear(screen.getByLabelText(/^url$/i))
    await user.type(
      screen.getByLabelText(/^url$/i),
      'https://checkout.example.com/health',
    )
    await user.click(screen.getByRole('button', { name: /create service/i }))

    expect(
      await screen.findByRole('heading', { name: /checkout api/i }),
    ).toBeInTheDocument()
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/v1/organizations/${platformOrganization.id}/services`,
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer test-access-token',
        }),
      }),
    )
  })

  it('allows an engineer to run a manual check without management actions', async () => {
    const engineerOrganization = {
      ...platformOrganization,
      currentUserRole: 'ENGINEER' as const,
    }
    const manualResult: ManualCheckResult = {
      id: '15808dd3-e772-4437-9164-cc4207993f7e',
      serviceId: publicApiService.id,
      checkedAt: '2026-07-30T01:00:00Z',
      success: true,
      degraded: false,
      statusCode: 200,
      responseTimeMilliseconds: 84,
      errorType: null,
      errorMessage: null,
      responseValidationPassed: true,
      responseExcerpt: 'healthy',
      checkSource: 'MANUAL',
      statusBefore: 'UNKNOWN',
      statusAfter: 'OPERATIONAL',
      affectsServiceStatus: true,
      createdAt: '2026-07-30T01:00:01Z',
    }
    let checked = false
    const fetchMock = vi.fn(
      async (input: string | URL | Request, options?: RequestInit) => {
        const path = String(input)
        if (path === '/api/v1/organizations') {
          return jsonResponse([engineerOrganization])
        }
        if (path === '/api/v1/invitations') {
          return jsonResponse([])
        }
        if (
          path ===
          `/api/v1/organizations/${engineerOrganization.id}/services/${publicApiService.id}/check`
        ) {
          expect(options?.method).toBe('POST')
          checked = true
          return jsonResponse(manualResult)
        }
        if (
          path ===
          `/api/v1/organizations/${engineerOrganization.id}/services/${publicApiService.id}/checks?size=20`
        ) {
          return jsonResponse(
            checked
              ? {
                  ...emptyCheckPage,
                  content: [manualResult],
                  totalElements: 1,
                  totalPages: 1,
                }
              : emptyCheckPage,
          )
        }
        if (
          path ===
          `/api/v1/organizations/${engineerOrganization.id}/services/${publicApiService.id}`
        ) {
          return jsonResponse(publicApiService)
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp(`/services/${publicApiService.id}`, authenticatedSession)

    await screen.findByRole('heading', { name: /public api/i })
    expect(screen.queryByRole('link', { name: /^edit$/i })).not.toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: /delete service/i }),
    ).not.toBeInTheDocument()
    await user.click(
      screen.getByRole('button', { name: /run manual check/i }),
    )

    expect(await screen.findByText(/check passed/i)).toBeInTheDocument()
    expect(screen.getAllByText(/84 ms/i)).not.toHaveLength(0)
    expect(await screen.findByText(/1 recorded/i)).toBeInTheDocument()
  })
})
