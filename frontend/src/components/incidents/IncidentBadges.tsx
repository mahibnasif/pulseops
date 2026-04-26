import type {
  IncidentSeverity,
  IncidentStatus,
} from '../../features/incidents/types'

export function IncidentStatusBadge({ status }: { status: IncidentStatus }) {
  return (
    <span className={`incident-badge incident-status-${status.toLowerCase()}`}>
      {status.replace('_', ' ').toLowerCase()}
    </span>
  )
}

export function IncidentSeverityBadge({
  severity,
}: {
  severity: IncidentSeverity
}) {
  return (
    <span
      className={`incident-badge incident-severity-${severity.toLowerCase()}`}
    >
      {severity.toLowerCase()}
    </span>
  )
}
