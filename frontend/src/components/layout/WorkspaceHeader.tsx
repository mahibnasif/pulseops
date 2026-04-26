import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../../features/auth/useAuth'
import { useOrganizations } from '../../features/organizations/useOrganizations'

export function WorkspaceHeader() {
  const auth = useAuth()
  const organizations = useOrganizations()
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
    <header className="workspace-header">
      <Link className="brand workspace-brand" to="/dashboard">
        <span className="brand-mark" aria-hidden="true">
          P
        </span>
        PulseOps
      </Link>
      <nav className="workspace-nav" aria-label="Workspace navigation">
        <Link to="/dashboard">Overview</Link>
        {organizations.currentOrganization && (
          <Link to="/services">Services</Link>
        )}
        {organizations.currentOrganization && (
          <Link to="/incidents">Incidents</Link>
        )}
        {organizations.currentOrganization && (
          <Link
            to={`/organizations/${organizations.currentOrganization.id}/settings`}
          >
            Settings
          </Link>
        )}
      </nav>
      <div className="workspace-actions">
        {organizations.organizations.length > 0 && (
          <label className="organization-picker">
            <span>Organization</span>
            <select
              aria-label="Organization"
              value={organizations.currentOrganization?.id ?? ''}
              onChange={(event) => {
                organizations.selectOrganization(event.target.value)
                navigate('/dashboard')
              }}
            >
              {organizations.organizations.map((organization) => (
                <option key={organization.id} value={organization.id}>
                  {organization.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <span className="user-identity">
          {auth.user?.firstName} {auth.user?.lastName}
        </span>
        <button
          className="button-quiet"
          disabled={signingOut}
          onClick={signOut}
        >
          {signingOut ? 'Signing out…' : 'Sign out'}
        </button>
      </div>
    </header>
  )
}
