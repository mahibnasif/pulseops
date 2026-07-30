import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/ApiError'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { useAuth } from '../features/auth/useAuth'
import * as organizationApi from '../features/organizations/organizationApi'
import type { MembershipRole } from '../features/organizations/types'
import { useOrganizations } from '../features/organizations/useOrganizations'

export function OrganizationSettingsPage() {
  const auth = useAuth()
  const organizations = useOrganizations()
  const { organizationId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const accessToken = auth.accessToken!
  const selected = organizations.organizations.find(
    (organization) => organization.id === organizationId,
  )
  const [inviteEmail, setInviteEmail] = useState('')
  const [inviteRole, setInviteRole] = useState<MembershipRole>('ENGINEER')
  const [feedback, setFeedback] = useState('')
  const isAdmin = selected?.currentUserRole === 'ADMIN'

  useEffect(() => {
    if (selected && selected.id !== organizations.currentOrganization?.id) {
      organizations.selectOrganization(selected.id)
    }
  }, [organizations, selected])

  const members = useQuery({
    queryKey: ['organizations', organizationId, 'members'],
    queryFn: () => organizationApi.listMembers(accessToken, organizationId!),
    enabled: Boolean(organizationId && selected),
  })
  const invitations = useQuery({
    queryKey: ['organizations', organizationId, 'invitations'],
    queryFn: () =>
      organizationApi.listOrganizationInvitations(accessToken, organizationId!),
    enabled: Boolean(organizationId && isAdmin),
  })

  async function refreshTeam() {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'members'],
      }),
      queryClient.invalidateQueries({
        queryKey: ['organizations', organizationId, 'invitations'],
      }),
    ])
  }

  const invite = useMutation({
    mutationFn: () =>
      organizationApi.createInvitation(
        accessToken,
        organizationId!,
        inviteEmail,
        inviteRole,
      ),
    onSuccess: async () => {
      setInviteEmail('')
      setFeedback('Invitation created. The secure token is available to the delivery workflow.')
      await refreshTeam()
    },
    onError: (error) => {
      setFeedback(
        error instanceof ApiError
          ? error.message
          : 'The invitation could not be created.',
      )
    },
  })

  const changeRole = useMutation({
    mutationFn: ({
      userId,
      role,
    }: {
      userId: string
      role: MembershipRole
    }) =>
      organizationApi.changeMemberRole(
        accessToken,
        organizationId!,
        userId,
        role,
      ),
    onSuccess: refreshTeam,
  })

  const removeMember = useMutation({
    mutationFn: (userId: string) =>
      organizationApi.removeMember(accessToken, organizationId!, userId),
    onSuccess: refreshTeam,
  })

  const transferOwnership = useMutation({
    mutationFn: (newOwnerId: string) =>
      organizationApi.transferOwnership(
        accessToken,
        organizationId!,
        newOwnerId,
      ),
    onSuccess: async () => {
      await organizations.refreshOrganizations()
      await refreshTeam()
    },
  })

  const leave = useMutation({
    mutationFn: () =>
      organizationApi.leaveOrganization(accessToken, organizationId!),
    onSuccess: async () => {
      await organizations.refreshOrganizations()
      navigate('/dashboard', { replace: true })
    },
  })

  if (!selected) {
    return (
      <div className="app-shell">
        <WorkspaceHeader />
        <main className="workspace-main">
          <section className="empty-workspace">
            <p className="eyebrow">Organization unavailable</p>
            <h1>This workspace isn’t available to your account.</h1>
          </section>
        </main>
      </div>
    )
  }

  function submitInvitation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFeedback('')
    invite.mutate()
  }

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main settings-main">
        <section className="settings-heading">
          <div>
            <p className="eyebrow">Organization settings</p>
            <h1>{selected.name}</h1>
            <p>
              Manage membership and access for the{' '}
              <strong>{selected.slug}</strong> tenant.
            </p>
          </div>
          <span className="role-badge compact">
            {selected.currentUserRole}
          </span>
        </section>

        {!isAdmin && (
          <div className="notice">
            Your role has read-only access to organization membership. An
            administrator must make access changes.
          </div>
        )}

        <section className="settings-grid">
          <div className="workspace-card team-card">
            <div className="section-heading">
              <div>
                <p className="eyebrow">Members</p>
                <h2>Team access</h2>
              </div>
              <span>{members.data?.length ?? 0} active</span>
            </div>
            {members.isLoading ? (
              <p className="muted-copy">Loading members…</p>
            ) : (
              <ul className="member-list">
                {(members.data ?? []).map((member) => {
                  const isOwner = member.userId === selected.ownerId
                  const isCurrentUser = member.userId === auth.user?.id
                  return (
                    <li key={member.membershipId}>
                      <div className="member-avatar" aria-hidden="true">
                        {member.firstName[0]}
                        {member.lastName[0]}
                      </div>
                      <div className="member-details">
                        <strong>
                          {member.firstName} {member.lastName}
                          {isCurrentUser && <small> You</small>}
                        </strong>
                        <span>{member.email}</span>
                      </div>
                      {isOwner ? (
                        <span className="owner-pill">Owner</span>
                      ) : (
                        <select
                          aria-label={`Role for ${member.firstName} ${member.lastName}`}
                          disabled={!isAdmin || changeRole.isPending}
                          value={member.role}
                          onChange={(event) =>
                            changeRole.mutate({
                              userId: member.userId,
                              role: event.target.value as MembershipRole,
                            })
                          }
                        >
                          <option value="ADMIN">Admin</option>
                          <option value="ENGINEER">Engineer</option>
                          <option value="VIEWER">Viewer</option>
                        </select>
                      )}
                      {isAdmin && !isOwner && (
                        <div className="member-actions">
                          <button
                            className="button-danger-quiet"
                            disabled={removeMember.isPending}
                            onClick={() => removeMember.mutate(member.userId)}
                          >
                            Remove
                          </button>
                          {auth.user?.id === selected.ownerId && (
                            <button
                              className="button-quiet"
                              disabled={transferOwnership.isPending}
                              onClick={() =>
                                transferOwnership.mutate(member.userId)
                              }
                            >
                              Make owner
                            </button>
                          )}
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </div>

          <aside className="settings-sidebar">
            {isAdmin && (
              <section className="workspace-card">
                <p className="eyebrow">Invite a teammate</p>
                <h2>Extend the response team</h2>
                <form className="compact-form" onSubmit={submitInvitation}>
                  <label>
                    Email address
                    <input
                      required
                      type="email"
                      value={inviteEmail}
                      onChange={(event) => setInviteEmail(event.target.value)}
                      placeholder="engineer@example.com"
                    />
                  </label>
                  <label>
                    Organization role
                    <select
                      value={inviteRole}
                      onChange={(event) =>
                        setInviteRole(event.target.value as MembershipRole)
                      }
                    >
                      <option value="ADMIN">Admin</option>
                      <option value="ENGINEER">Engineer</option>
                      <option value="VIEWER">Viewer</option>
                    </select>
                  </label>
                  {feedback && <p className="form-feedback">{feedback}</p>}
                  <button
                    className="button"
                    disabled={invite.isPending}
                    type="submit"
                  >
                    {invite.isPending ? 'Creating…' : 'Create invitation'}
                  </button>
                </form>
              </section>
            )}
            {isAdmin && (
              <section className="workspace-card">
                <p className="eyebrow">Pending</p>
                <h2>Open invitations</h2>
                <ul className="compact-invitation-list">
                  {(invitations.data ?? [])
                    .filter((invitation) => !invitation.acceptedAt)
                    .map((invitation) => (
                      <li key={invitation.id}>
                        <strong>{invitation.email}</strong>
                        <span>{invitation.role.toLowerCase()}</span>
                      </li>
                    ))}
                </ul>
                {!invitations.isLoading &&
                  !(invitations.data ?? []).some(
                    (invitation) => !invitation.acceptedAt,
                  ) && (
                    <p className="muted-copy">No invitations are pending.</p>
                  )}
              </section>
            )}
            <section className="workspace-card leave-card">
              <p className="eyebrow">Membership</p>
              <h2>Leave organization</h2>
              <p>
                Owners must transfer ownership before leaving the workspace.
              </p>
              <button
                className="button-danger"
                disabled={leave.isPending}
                onClick={() => leave.mutate()}
              >
                Leave organization
              </button>
            </section>
          </aside>
        </section>
      </main>
    </div>
  )
}
