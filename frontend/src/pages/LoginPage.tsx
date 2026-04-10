import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/ApiError'
import { useAuth } from '../features/auth/useAuth'

interface ReturnLocationState {
  from?: {
    pathname?: string
  }
}

export function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSubmitting(true)

    try {
      await auth.login({ email, password })
      const state = location.state as ReturnLocationState | null
      navigate(state?.from?.pathname ?? '/dashboard', { replace: true })
    } catch (caughtError) {
      setError(
        caughtError instanceof ApiError
          ? caughtError.message
          : 'Sign in is temporarily unavailable.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthPage
      eyebrow="Welcome back"
      title="Sign in to PulseOps"
      footer={
        <>
          New to PulseOps? <Link to="/register">Create an account</Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={handleSubmit}>
        {error && (
          <p className="form-alert" role="alert">
            {error}
          </p>
        )}
        <label>
          Email address
          <input
            autoComplete="email"
            name="email"
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
        </label>
        <label>
          Password
          <input
            autoComplete="current-password"
            name="password"
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </label>
        <button className="button button-full" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </AuthPage>
  )
}

interface AuthPageProps {
  eyebrow: string
  title: string
  footer: ReactNode
  children: ReactNode
}

export function AuthPage({
  eyebrow,
  title,
  footer,
  children,
}: AuthPageProps) {
  return (
    <main className="auth-page">
      <section className="auth-panel">
        <Link className="brand brand-centered" to="/">
          <span className="brand-mark" aria-hidden="true">
            P
          </span>
          PulseOps
        </Link>
        <div className="auth-heading">
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
        </div>
        {children}
        <p className="auth-footer">{footer}</p>
      </section>
      <aside className="auth-context" aria-label="PulseOps product summary">
        <div>
          <p className="eyebrow eyebrow-light">Built for response teams</p>
          <h2>Every signal, incident, and decision in one timeline.</h2>
          <ul className="feature-list">
            <li>Automated service health checks</li>
            <li>Failure thresholds that reduce false alarms</li>
            <li>Incident ownership from detection to resolution</li>
          </ul>
        </div>
      </aside>
    </main>
  )
}
