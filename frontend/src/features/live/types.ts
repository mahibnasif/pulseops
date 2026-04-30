export type LiveEventType =
  | 'CONNECTION_READY'
  | 'SERVICE_CREATED'
  | 'SERVICE_UPDATED'
  | 'SERVICE_DELETED'
  | 'SERVICE_STATUS_CHANGED'
  | 'HEALTH_CHECK_RECORDED'
  | 'INCIDENT_CREATED'
  | 'INCIDENT_UPDATED'
  | 'INCIDENT_COMMENT_ADDED'
  | 'NOTIFICATION_CREATED'

export interface OrganizationLiveEvent {
  id: string
  organizationId: string
  type: LiveEventType
  entityType: string
  entityId: string
  occurredAt: string
}

export type LiveConnectionStatus =
  | 'idle'
  | 'connecting'
  | 'live'
  | 'reconnecting'
  | 'offline'
