import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { ApiError } from '../api/ApiError'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { useAuth } from '../features/auth/useAuth'
import * as organizationApi from '../features/organizations/organizationApi'
import { useOrganizations } from '../features/organizations/useOrganizations'
import * as serviceApi from '../features/services/serviceApi'
import * as incidentApi from '../features/incidents/incidentApi'

export function DashboardPage() {
  const auth = useAuth()
  const organizations = useOrganizations()
  const queryClient = useQueryClient()
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [formError, setFormError] = useState('')
  const accessToken = auth.accessToken!
  const organizationId = organizations.currentOrganization?.id

  const pendingInvitations = useQuery({
    queryKey: ['invitations', 'pending'],
    queryFn: () => organizationApi.listPendingInvitations(accessToken),
  })
  const services = useQuery({
    queryKey: ['services', organizationId, 'dashboard-total'],
    queryFn: () => serviceApi.listServices(accessToken, organizationId!),
    enabled: Boolean(organizationId),
  })
  const downServices = useQuery({
    queryKey: ['services', organizationId, 'dashboard-down'],
    queryFn: () =>
      serviceApi.listServices(accessToken, organizationId!, { status: 'DOWN' }),
    enabled: Boolean(organizationId),
  })
  const openIncidents = useQuery({
    queryKey: ['incidents', organizationId, 'dashboard-open'],
    queryFn: () =>
      incidentApi.listIncidents(accessToken, organizationId!, {
        status: 'OPEN',
      }),
    enabled: Boolean(organizationId),
  })
  const acceptInvitation = useMutation({
    mutationFn: (invitationId: string) =>
      organizationApi.acceptInvitation(accessToken, invitationId),
    onSuccess: async (organization) => {
      await Promise.all([
        organizations.refreshOrganizations(),
        queryClient.invalidateQueries({ queryKey: ['invitations', 'pending'] }),
      ])
      organizations.selectOrganization(organization.id)
    },
  })

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError('')
    try {
      await organizations.createOrganization({ name, description })
      setName('')
      setDescription('')
    } catch (error) {
      setFormError(
        error instanceof ApiError
          ? error.message
          : 'The organization could not be created.',
      )
    }
  }

  if (organizations.loading) {
    return (
      <div className="app-shell">
        <WorkspaceHeader />
        <main className="workspace-main">
          <div className="loading-panel">Loading your organizations…</div>
        </main>
      </div>
    )
  }

  if (organizations.error) {
    return (
      <div className="app-shell">
        <WorkspaceHeader />
        <main className="workspace-main">
          <section className="empty-workspace">
            <p className="eyebrow">Workspace unavailable</p>
            <h1>We couldn’t load your organizations.</h1>
            <button
              className="button"
              onClick={() => void organizations.refreshOrganizations()}
            >
              Try again
            </button>
          </section>
        </main>
      </div>
    )
  }

  const current = organizations.currentOrganization

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main">
        {!current ? (
          <div className="onboarding-grid">
            <section className="empty-workspace">
              <p className="eyebrow">Create your first workspace</p>
              <h1>Bring your team into PulseOps.</h1>
              <p>
                Organizations are secure boundaries for members, services, and
                incidents. You’ll become the owner and first administrator.
              </p>
              <form className="organization-form" onSubmit={createOrganization}>
                <label>
                  Organization name
                  <input
                    required
                    maxLength={120}
                    value={name}
                    onChange={(event) => setName(event.target.value)}
                    placeholder="Acme Engineering"
                  />
                </label>
                <label>
                  Description <span className="optional">Optional</span>
                  <textarea
                    maxLength={1000}
                    value={description}
                    onChange={(event) => setDescription(event.target.value)}
                    placeholder="The team responsible for our production services."
                  />
                </label>
                {formError && <div className="form-error">{formError}</div>}
                <button
                  className="button"
                  disabled={organizations.loading}
                  type="submit"
                >
                  Create organization
                </button>
              </form>
            </section>
            <PendingInvitations
              accepting={acceptInvitation.isPending}
              invitations={pendingInvitations.data ?? []}
              onAccept={(id) => acceptInvitation.mutate(id)}
            />
          </div>
        ) : (
          <>
            <section className="workspace-hero">
              <div>
                <p className="eyebrow">Organization workspace</p>
                <h1>{current.name}</h1>
                <p>
                  {current.description ||
                    'Your organization is ready for monitored services.'}
                </p>
              </div>
              <div className="role-badge">
                <span>Your role</span>
                <strong>{current.currentUserRole}</strong>
              </div>
            </section>
            <section className="foundation-grid" aria-label="Workspace status">
              <article>
                <span className="status-dot status-dot-green" />
                <p>Monitored services</p>
                <strong>{services.data?.totalElements ?? '—'}</strong>
                <small>Live inventory for {current.slug}.</small>
              </article>
              <article>
                <span className="status-dot status-dot-red" />
                <p>Services down</p>
                <strong>{downServices.data?.totalElements ?? '—'}</strong>
                <small>Confirmed outages refresh automatically.</small>
              </article>
              <article>
                <span className="status-dot status-dot-amber" />
                <p>Open incidents</p>
                <strong>{openIncidents.data?.totalElements ?? '—'}</strong>
                <small>New incidents appear without a page reload.</small>
              </article>
            </section>
            <div className="workspace-columns">
              <section className="workspace-card">
                <p className="eyebrow">Team administration</p>
                <h2>Build the response team.</h2>
                <p>
                  Invite teammates, assign roles, and manage ownership from one
                  protected settings area.
                </p>
                <Link
                  className="button-secondary inline-button"
                  to={`/organizations/${current.id}/settings`}
                >
                  Open organization settings
                </Link>
              </section>
              <PendingInvitations
                accepting={acceptInvitation.isPending}
                invitations={pendingInvitations.data ?? []}
                onAccept={(id) => acceptInvitation.mutate(id)}
              />
            </div>
          </>
        )}
      </main>
    </div>
  )
}

function PendingInvitations({
  accepting,
  invitations,
  onAccept,
}: {
  accepting: boolean
  invitations: Array<{
    id: string
    organizationName: string
    role: string
    expiresAt: string
  }>
  onAccept: (id: string) => void
}) {
  return (
    <section className="workspace-card invitations-card">
      <p className="eyebrow">Pending invitations</p>
      <h2>Teams waiting for you</h2>
      {invitations.length === 0 ? (
        <p className="muted-copy">You don’t have any pending invitations.</p>
      ) : (
        <ul className="invitation-list">
          {invitations.map((invitation) => (
            <li key={invitation.id}>
              <div>
                <strong>{invitation.organizationName}</strong>
                <span>
                  {invitation.role.toLowerCase()} · expires{' '}
                  {new Date(invitation.expiresAt).toLocaleDateString()}
                </span>
              </div>
              <button
                className="button-secondary"
                disabled={accepting}
                onClick={() => onAccept(invitation.id)}
              >
                Accept
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
