import type { ServiceStatus } from '../../features/services/types'

export function ServiceStatusBadge({ status }: { status: ServiceStatus }) {
  return (
    <span className={`service-status service-status-${status.toLowerCase()}`}>
      <span aria-hidden="true" />
      {status.toLowerCase()}
    </span>
  )
}
