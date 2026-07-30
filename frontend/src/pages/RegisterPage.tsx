import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/ApiError'
import { useAuth } from '../features/auth/useAuth'
import { AuthPage } from './LoginPage'

export function RegisterPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setFieldErrors({})

    if (password !== confirmPassword) {
      setFieldErrors({ confirmPassword: 'Passwords must match.' })
      return
    }

    setSubmitting(true)
    try {
      await auth.register({ firstName, lastName, email, password })
      navigate('/dashboard', { replace: true })
    } catch (caughtError) {
      if (caughtError instanceof ApiError) {
        setError(caughtError.message)
        setFieldErrors(caughtError.fieldErrors)
      } else {
        setError('Account creation is temporarily unavailable.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthPage
      eyebrow="Create your workspace identity"
      title="Start with a secure account"
      footer={
        <>
          Already registered? <Link to="/login">Sign in</Link>
        </>
      }
    >
      <form className="auth-form" onSubmit={handleSubmit}>
        {error && (
          <p className="form-alert" role="alert">
            {error}
          </p>
        )}
        <div className="form-row">
          <label>
            First name
            <input
              autoComplete="given-name"
              name="firstName"
              required
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
            />
            {fieldErrors.firstName && (
              <span className="field-error">{fieldErrors.firstName}</span>
            )}
          </label>
          <label>
            Last name
            <input
              autoComplete="family-name"
              name="lastName"
              required
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
            />
            {fieldErrors.lastName && (
              <span className="field-error">{fieldErrors.lastName}</span>
            )}
          </label>
        </div>
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
          {fieldErrors.email && (
            <span className="field-error">{fieldErrors.email}</span>
          )}
        </label>
        <label>
          Password
          <input
            autoComplete="new-password"
            minLength={12}
            name="password"
            type="password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          <span className="field-hint">Use at least 12 characters.</span>
          {fieldErrors.password && (
            <span className="field-error">{fieldErrors.password}</span>
          )}
        </label>
        <label>
          Confirm password
          <input
            autoComplete="new-password"
            minLength={12}
            name="confirmPassword"
            type="password"
            required
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
          {fieldErrors.confirmPassword && (
            <span className="field-error">{fieldErrors.confirmPassword}</span>
          )}
        </label>
        <button className="button button-full" disabled={submitting}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
    </AuthPage>
  )
}
