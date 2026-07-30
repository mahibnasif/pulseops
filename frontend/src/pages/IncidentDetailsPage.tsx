import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/ApiError'
import {
  IncidentSeverityBadge,
  IncidentStatusBadge,
} from '../components/incidents/IncidentBadges'
import { WorkspaceHeader } from '../components/layout/WorkspaceHeader'
import { useAuth } from '../features/auth/useAuth'
import * as incidentApi from '../features/incidents/incidentApi'
import type {
  IncidentSeverity,
  IncidentStatus,
} from '../features/incidents/types'
import { listMembers } from '../features/organizations/organizationApi'
import { useOrganizations } from '../features/organizations/useOrganizations'
import { getService } from '../features/services/serviceApi'

export function IncidentDetailsPage() {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const { incidentId } = useParams()
  const organizationId = currentOrganization?.id
  const queryClient = useQueryClient()
  const canManage = currentOrganization?.currentUserRole !== 'VIEWER'
  const [comment, setComment] = useState('')
  const [rootCause, setRootCause] = useState('')
  const [resolutionSummary, setResolutionSummary] = useState('')
  const [error, setError] = useState('')

  const details = useQuery({
    queryKey: ['incident', organizationId, incidentId],
    queryFn: () =>
      incidentApi.getIncident(
        auth.accessToken!,
        organizationId!,
        incidentId!,
      ),
    enabled: Boolean(organizationId && incidentId),
  })
  const service = useQuery({
    queryKey: ['service', organizationId, details.data?.incident.serviceId],
    queryFn: () =>
      getService(
        auth.accessToken!,
        organizationId!,
        details.data!.incident.serviceId,
      ),
    enabled: Boolean(organizationId && details.data?.incident.serviceId),
  })
  const members = useQuery({
    queryKey: ['members', organizationId],
    queryFn: () => listMembers(auth.accessToken!, organizationId!),
    enabled: Boolean(organizationId),
  })
  const refresh = async () => {
    setError('')
    await queryClient.invalidateQueries({
      queryKey: ['incident', organizationId, incidentId],
    })
    await queryClient.invalidateQueries({
      queryKey: ['incidents', organizationId],
    })
  }
  const mutationError = (failure: Error) =>
    setError(
      failure instanceof ApiError
        ? failure.message
        : 'The incident could not be updated.',
    )

  const update = useMutation({
    mutationFn: (input: {
      severity?: IncidentSeverity
      status?: IncidentStatus
    }) =>
      incidentApi.updateIncident(
        auth.accessToken!,
        organizationId!,
        incidentId!,
        input,
      ),
    onSuccess: refresh,
    onError: mutationError,
  })
  const assign = useMutation({
    mutationFn: (userId: string | null) =>
      incidentApi.assignIncident(
        auth.accessToken!,
        organizationId!,
        incidentId!,
        userId,
      ),
    onSuccess: refresh,
    onError: mutationError,
  })
  const addComment = useMutation({
    mutationFn: () =>
      incidentApi.addComment(
        auth.accessToken!,
        organizationId!,
        incidentId!,
        comment,
      ),
    onSuccess: async () => {
      setComment('')
      await refresh()
    },
    onError: mutationError,
  })
  const resolve = useMutation({
    mutationFn: () =>
      incidentApi.resolveIncident(
        auth.accessToken!,
        organizationId!,
        incidentId!,
        rootCause,
        resolutionSummary,
      ),
    onSuccess: refresh,
    onError: mutationError,
  })
  const reopen = useMutation({
    mutationFn: () =>
      incidentApi.reopenIncident(
        auth.accessToken!,
        organizationId!,
        incidentId!,
      ),
    onSuccess: refresh,
    onError: mutationError,
  })

  if (details.isLoading) {
    return <PageState>Loading incident…</PageState>
  }
  if (details.error || !details.data) {
    return <PageState>The incident could not be loaded.</PageState>
  }

  const incident = details.data.incident
  const memberNames = new Map(
    members.data?.map((member) => [
      member.userId,
      `${member.firstName} ${member.lastName}`,
    ]),
  )

  function submitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    addComment.mutate()
  }

  function submitResolution(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    resolve.mutate()
  }

  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main incident-detail-main">
        <Link className="back-link" to="/incidents">
          ← Back to incidents
        </Link>
        <section className="service-detail-heading">
          <div>
            <div className="incident-heading-badges">
              <IncidentSeverityBadge severity={incident.severity} />
              <IncidentStatusBadge status={incident.status} />
              <span className="incident-source">
                {incident.source === 'AUTOMATIC_MONITORING'
                  ? 'Monitoring'
                  : 'Manual'}
              </span>
            </div>
            <h1>{incident.title}</h1>
            <p className="incident-service-link">
              Affecting{' '}
              <Link to={`/services/${incident.serviceId}`}>
                {service.data?.name ?? 'service'}
              </Link>
            </p>
          </div>
          {canManage && incident.status === 'RESOLVED' && (
            <button
              className="button-secondary"
              disabled={reopen.isPending}
              onClick={() => reopen.mutate()}
            >
              Reopen incident
            </button>
          )}
        </section>

        {error && <div className="notice">{error}</div>}

        <div className="incident-layout">
          <div className="incident-primary">
            <section className="workspace-card incident-overview">
              <h2>Situation</h2>
              <p>{incident.description || 'No description was provided.'}</p>
              <dl className="detail-list">
                <div>
                  <dt>Detected</dt>
                  <dd>{new Date(incident.detectedAt).toLocaleString()}</dd>
                </div>
                <div>
                  <dt>Acknowledged</dt>
                  <dd>
                    {incident.acknowledgedAt
                      ? new Date(incident.acknowledgedAt).toLocaleString()
                      : 'Not yet'}
                  </dd>
                </div>
                <div>
                  <dt>Resolved</dt>
                  <dd>
                    {incident.resolvedAt
                      ? new Date(incident.resolvedAt).toLocaleString()
                      : 'Active'}
                  </dd>
                </div>
              </dl>
            </section>

            {incident.status === 'RESOLVED' && (
              <section className="workspace-card resolution-card">
                <h2>Resolution</h2>
                <h3>Root cause</h3>
                <p>{incident.rootCause || 'Not documented.'}</p>
                <h3>Resolution summary</h3>
                <p>{incident.resolutionSummary}</p>
              </section>
            )}

            <section className="workspace-card">
              <div className="history-heading">
                <h2>Response timeline</h2>
                <span>{details.data.timeline.length} events</span>
              </div>
              <ol className="incident-timeline">
                {[...details.data.timeline].reverse().map((event) => (
                  <li key={event.id}>
                    <span className="timeline-dot" aria-hidden="true" />
                    <div>
                      <strong>{event.message}</strong>
                      {event.oldValue && event.newValue && (
                        <p>
                          {humanize(event.oldValue)} → {humanize(event.newValue)}
                        </p>
                      )}
                      <small>
                        {event.actorUserId
                          ? memberNames.get(event.actorUserId) ?? 'Team member'
                          : 'PulseOps monitoring'}{' '}
                        · {new Date(event.createdAt).toLocaleString()}
                      </small>
                    </div>
                  </li>
                ))}
              </ol>
            </section>

            <section className="workspace-card">
              <div className="history-heading">
                <h2>Comments</h2>
                <span>{details.data.comments.length}</span>
              </div>
              <div className="incident-comments">
                {details.data.comments.length ? (
                  details.data.comments.map((item) => (
                    <article key={item.id}>
                      <div>
                        <strong>
                          {memberNames.get(item.authorId) ?? 'Team member'}
                        </strong>
                        <time>{new Date(item.createdAt).toLocaleString()}</time>
                      </div>
                      <p>{item.content}</p>
                    </article>
                  ))
                ) : (
                  <p className="muted-copy">No response notes yet.</p>
                )}
              </div>
              {canManage && (
                <form className="comment-form" onSubmit={submitComment}>
                  <label>
                    Add response note
                    <textarea
                      required
                      maxLength={4000}
                      value={comment}
                      onChange={(event) => setComment(event.target.value)}
                    />
                  </label>
                  <button
                    className="button"
                    disabled={addComment.isPending}
                    type="submit"
                  >
                    Add comment
                  </button>
                </form>
              )}
            </section>
          </div>

          <aside className="incident-sidebar">
            <section className="workspace-card">
              <h2>Ownership</h2>
              {canManage ? (
                <label className="incident-control">
                  Assignee
                  <select
                    aria-label="Incident assignee"
                    value={incident.assignedUserId ?? ''}
                    disabled={assign.isPending}
                    onChange={(event) =>
                      assign.mutate(event.target.value || null)
                    }
                  >
                    <option value="">Unassigned</option>
                    {members.data?.map((member) => (
                      <option key={member.userId} value={member.userId}>
                        {member.firstName} {member.lastName}
                      </option>
                    ))}
                  </select>
                </label>
              ) : (
                <p>
                  {incident.assignedUserId
                    ? memberNames.get(incident.assignedUserId) ?? 'Team member'
                    : 'Unassigned'}
                </p>
              )}
            </section>

            {canManage && incident.status !== 'RESOLVED' && (
              <section className="workspace-card">
                <h2>Classification</h2>
                <label className="incident-control">
                  Severity
                  <select
                    aria-label="Incident severity"
                    value={incident.severity}
                    disabled={update.isPending}
                    onChange={(event) =>
                      update.mutate({
                        severity: event.target.value as IncidentSeverity,
                      })
                    }
                  >
                    {['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((value) => (
                      <option key={value}>{value}</option>
                    ))}
                  </select>
                </label>
                <label className="incident-control">
                  Response status
                  <select
                    aria-label="Incident status"
                    value={incident.status}
                    disabled={update.isPending}
                    onChange={(event) =>
                      update.mutate({
                        status: event.target.value as IncidentStatus,
                      })
                    }
                  >
                    {nextStatuses(incident.status).map((value) => (
                      <option key={value}>{value}</option>
                    ))}
                  </select>
                </label>
              </section>
            )}

            {canManage && incident.status !== 'RESOLVED' && (
              <form
                className="workspace-card resolution-form"
                onSubmit={submitResolution}
              >
                <h2>Resolve incident</h2>
                <label>
                  Root cause <span className="optional">Optional</span>
                  <textarea
                    maxLength={4000}
                    value={rootCause}
                    onChange={(event) => setRootCause(event.target.value)}
                  />
                </label>
                <label>
                  Resolution summary
                  <textarea
                    required
                    maxLength={4000}
                    value={resolutionSummary}
                    onChange={(event) =>
                      setResolutionSummary(event.target.value)
                    }
                  />
                </label>
                <button
                  className="button"
                  disabled={resolve.isPending}
                  type="submit"
                >
                  Mark resolved
                </button>
              </form>
            )}
          </aside>
        </div>
      </main>
    </div>
  )
}

function PageState({ children }: { children: React.ReactNode }) {
  return (
    <div className="app-shell">
      <WorkspaceHeader />
      <main className="workspace-main">
        <div className="loading-panel">{children}</div>
      </main>
    </div>
  )
}

function nextStatuses(status: IncidentStatus): IncidentStatus[] {
  const transitions: Record<IncidentStatus, IncidentStatus[]> = {
    OPEN: ['OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING'],
    ACKNOWLEDGED: ['ACKNOWLEDGED', 'INVESTIGATING', 'IDENTIFIED', 'MONITORING'],
    INVESTIGATING: ['INVESTIGATING', 'IDENTIFIED', 'MONITORING'],
    IDENTIFIED: ['IDENTIFIED', 'INVESTIGATING', 'MONITORING'],
    MONITORING: ['MONITORING', 'INVESTIGATING', 'IDENTIFIED'],
    RESOLVED: ['RESOLVED'],
  }
  return transitions[status]
}

function humanize(value: string) {
  return value.replaceAll('_', ' ').toLowerCase()
}
