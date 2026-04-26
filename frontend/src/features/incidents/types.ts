export type IncidentSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type IncidentStatus =
  | 'OPEN'
  | 'ACKNOWLEDGED'
  | 'INVESTIGATING'
  | 'IDENTIFIED'
  | 'MONITORING'
  | 'RESOLVED'
export type IncidentSource = 'MANUAL' | 'AUTOMATIC_MONITORING'

export interface Incident {
  id: string
  organizationId: string
  serviceId: string
  title: string
  description: string | null
  severity: IncidentSeverity
  status: IncidentStatus
  source: IncidentSource
  assignedUserId: string | null
  detectedAt: string
  acknowledgedAt: string | null
  resolvedAt: string | null
  rootCause: string | null
  resolutionSummary: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface IncidentPage {
  content: Incident[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface IncidentComment {
  id: string
  authorId: string
  content: string
  createdAt: string
  updatedAt: string
}

export interface TimelineEvent {
  id: string
  eventType: string
  actorUserId: string | null
  message: string
  oldValue: string | null
  newValue: string | null
  createdAt: string
}

export interface IncidentDetails {
  incident: Incident
  comments: IncidentComment[]
  timeline: TimelineEvent[]
}

export interface CreateIncidentInput {
  serviceId: string
  title: string
  description?: string
  severity: IncidentSeverity
}

export interface IncidentFilters {
  search?: string
  status?: IncidentStatus | ''
  severity?: IncidentSeverity | ''
  serviceId?: string
  assigneeId?: string
}
