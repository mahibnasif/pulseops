import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

export function ProtectedRoute() {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'loading') {
    return (
      <main className="session-loading">
        <div className="spinner" aria-hidden="true" />
        <p role="status">Restoring your secure session…</p>
      </main>
    )
  }

  if (auth.status !== 'authenticated') {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}
