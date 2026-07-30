import { afterEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router'
import App from './App'
import { AuthProvider } from './features/auth/AuthProvider'
import type { AuthSession } from './features/auth/types'
import { LiveStreamUnauthorizedError } from './features/live/liveEventStream'
import type { Organization } from './features/organizations/types'
import type { MonitoredService, ServicePage } from './features/services/types'

const session: AuthSession = {
  accessToken: 'live-test-token',
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
  currentUserRole: 'ADMIN',
  createdAt: '2026-07-30T00:00:00Z',
  updatedAt: '2026-07-30T00:00:00Z',
}

const service: MonitoredService = {
  id: '33333333-3333-4333-8333-333333333333',
  organizationId: organization.id,
  name: 'Checkout API',
  description: null,
  serviceType: 'HTTPS',
  url: 'https://example.com',
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
  status: 'UNKNOWN',
  active: true,
  createdBy: session.user.id,
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

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

function page(status: MonitoredService['status']): ServicePage {
  return {
    content: [{ ...service, status }],
    page: 0,
    size: 50,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
  }
}

function renderServices() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/services']}>
        <AuthProvider initialSession={session}>
          <App />
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('PulseOps live organization updates', () => {
  it('reconnects and refreshes authoritative service data once per event ID', async () => {
    let eventRequests = 0
    let serviceRequests = 0
    const encoder = new TextEncoder()
    const event = {
      id: '44444444-4444-4444-8444-444444444444',
      organizationId: organization.id,
      type: 'SERVICE_STATUS_CHANGED',
      entityType: 'service',
      entityId: service.id,
      occurredAt: '2026-07-30T00:02:00Z',
    }

    const fetchMock = vi.fn(
      async (input: string | URL | Request, options?: RequestInit) => {
        const path = String(input)
        if (path === '/api/v1/organizations') {
          return jsonResponse([organization])
        }
        if (path.startsWith(`/api/v1/organizations/${organization.id}/services?`)) {
          serviceRequests += 1
          return jsonResponse(page(serviceRequests < 3 ? 'UNKNOWN' : 'DOWN'))
        }
        if (path === `/api/v1/organizations/${organization.id}/events`) {
          eventRequests += 1
          expect(options?.headers).toEqual(
            expect.objectContaining({
              Authorization: `Bearer ${session.accessToken}`,
            }),
          )
          expect(path).not.toContain(session.accessToken)
          if (eventRequests === 1) {
            return new Response('', { status: 503 })
          }
          const signal = options?.signal
          const stream = new ReadableStream<Uint8Array>({
            start(controller) {
              controller.enqueue(
                encoder.encode(
                  `event:pulseops-ready\ndata:${JSON.stringify({
                    ...event,
                    id: '55555555-5555-4555-8555-555555555555',
                    type: 'CONNECTION_READY',
                  })}\n\n`,
                ),
              )
              window.setTimeout(() => {
                controller.enqueue(
                  encoder.encode(
                    `event:pulseops-update\r\ndata:${JSON.stringify(event)}\r\n\r\n`,
                  ),
                )
              }, 30)
              window.setTimeout(() => {
                controller.enqueue(
                  encoder.encode(
                    `event:pulseops-update\ndata:${JSON.stringify(event)}\n\n`,
                  ),
                )
                controller.enqueue(
                  encoder.encode(
                    `event:pulseops-update\ndata:${JSON.stringify({
                      ...event,
                      id: '66666666-6666-4666-8666-666666666666',
                      organizationId: '77777777-7777-4777-8777-777777777777',
                    })}\n\n`,
                  ),
                )
              }, 120)
              signal?.addEventListener(
                'abort',
                () => {
                  try {
                    controller.close()
                  } catch {
                    // The test stream may already be closed.
                  }
                },
                { once: true },
              )
            },
          })
          return new Response(stream, {
            headers: { 'Content-Type': 'text/event-stream' },
          })
        }
        throw new Error(`Unexpected request: ${path}`)
      },
    )
    vi.stubGlobal('fetch', fetchMock)
    renderServices()

    expect(await screen.findByText('unknown')).toBeInTheDocument()
    expect(await screen.findByText('Reconnecting')).toBeInTheDocument()
    expect(await screen.findByText('down', {}, { timeout: 2500 }))
      .toBeInTheDocument()
    expect(screen.getByText('Live')).toBeInTheDocument()
    await new Promise((resolve) => window.setTimeout(resolve, 180))
    await waitFor(
      () => {
        expect(eventRequests).toBe(2)
        expect(serviceRequests).toBe(3)
      },
      { timeout: 2500 },
    )
  })

  it('identifies an expired stream token without exposing it in the URL', async () => {
    const { consumeLiveEventStream } = await import(
      './features/live/liveEventStream'
    )
    const fetchMock = vi.fn(async (_input: RequestInfo | URL) =>
      new Response('', { status: 401 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    await expect(
      consumeLiveEventStream({
        organizationId: organization.id,
        accessToken: session.accessToken,
        signal: new AbortController().signal,
        onOpen: vi.fn(),
        onEvent: vi.fn(),
      }),
    ).rejects.toBeInstanceOf(LiveStreamUnauthorizedError)
    expect(String(fetchMock.mock.calls[0][0])).not.toContain(session.accessToken)
  })
})

function jsonResponse(value: unknown) {
  return new Response(JSON.stringify(value), {
    headers: { 'Content-Type': 'application/json' },
  })
}
