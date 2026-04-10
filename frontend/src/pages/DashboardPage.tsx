import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../features/auth/useAuth'

export function DashboardPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [signingOut, setSigningOut] = useState(false)

  async function signOut() {
    setSigningOut(true)
    try {
      await auth.logout()
    } finally {
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            P
          </span>
          PulseOps
        </div>
        <div className="user-menu">
          <span>
            {auth.user?.firstName} {auth.user?.lastName}
          </span>
          <button
            className="button-secondary"
            disabled={signingOut}
            onClick={signOut}
          >
            {signingOut ? 'Signing out…' : 'Sign out'}
          </button>
        </div>
      </header>
      <main className="dashboard">
        <p className="eyebrow">Authenticated workspace</p>
        <h1>Welcome, {auth.user?.firstName}.</h1>
        <p className="dashboard-summary">
          Your account is secure and ready. Organization setup arrives in
          Phase 3.
        </p>
        <section className="phase-card">
          <span className="status-pill status-operational">Session active</span>
          <h2>Phase 2 authentication</h2>
          <p>
            Access tokens stay in memory. Your rotating refresh session is
            protected by an HttpOnly cookie.
          </p>
        </section>
      </main>
    </div>
  )
}
