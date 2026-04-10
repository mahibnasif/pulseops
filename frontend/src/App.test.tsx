import { afterEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './features/auth/AuthProvider'
import type { AuthSession } from './features/auth/types'

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

afterEach(() => {
  vi.unstubAllGlobals()
})

function renderApp(path: string, session: AuthSession | null = null) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider initialSession={session}>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  )
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
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify(authenticatedSession), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(screen.getByLabelText(/email address/i), 'ada@example.com')
    await user.type(screen.getByLabelText(/^password$/i), 'Correct-Horse-42')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(
      await screen.findByRole('heading', { name: /welcome, ada/i }),
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
    renderApp('/dashboard', authenticatedSession)

    expect(
      screen.getByRole('heading', { name: /welcome, ada/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign out/i })).toBeEnabled()
    expect(screen.queryByText('test-access-token')).not.toBeInTheDocument()
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
