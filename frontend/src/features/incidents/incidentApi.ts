import { ApiError, type ApiErrorBody } from '../../api/ApiError'
import type {
  CreateIncidentInput,
  Incident,
  IncidentComment,
  IncidentDetails,
  IncidentFilters,
  IncidentPage,
  IncidentSeverity,
  IncidentStatus,
} from './types'

async function request<T>(
  path: string,
  accessToken: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(path, {
    ...options,
    credentials: 'include',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
      'X-Requested-With': 'PulseOps',
      ...options.headers,
    },
  })
  if (!response.ok) {
    let body: ApiErrorBody = {}
    try {
      body = (await response.json()) as ApiErrorBody
    } catch {
      body = {}
    }
    throw new ApiError(response.status, body)
  }
  return (await response.json()) as T
}

function base(organizationId: string) {
  return `/api/v1/organizations/${organizationId}/incidents`
}

export function listIncidents(
  accessToken: string,
  organizationId: string,
  filters: IncidentFilters = {},
) {
  const query = new URLSearchParams({ size: '50' })
  Object.entries(filters).forEach(([key, value]) => {
    if (value) query.set(key, value)
  })
  return request<IncidentPage>(
    `${base(organizationId)}?${query.toString()}`,
    accessToken,
  )
}

export function getIncident(
  accessToken: string,
  organizationId: string,
  incidentId: string,
) {
  return request<IncidentDetails>(
    `${base(organizationId)}/${incidentId}`,
    accessToken,
  )
}

export function createIncident(
  accessToken: string,
  organizationId: string,
  input: CreateIncidentInput,
) {
  return request<Incident>(base(organizationId), accessToken, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateIncident(
  accessToken: string,
  organizationId: string,
  incidentId: string,
  input: Partial<{
    title: string
    description: string
    severity: IncidentSeverity
    status: IncidentStatus
    rootCause: string
    resolutionSummary: string
  }>,
) {
  return request<Incident>(
    `${base(organizationId)}/${incidentId}`,
    accessToken,
    { method: 'PATCH', body: JSON.stringify(input) },
  )
}

export function assignIncident(
  accessToken: string,
  organizationId: string,
  incidentId: string,
  userId: string | null,
) {
  return request<Incident>(
    `${base(organizationId)}/${incidentId}/assign`,
    accessToken,
    { method: 'POST', body: JSON.stringify({ userId }) },
  )
}

export function addComment(
  accessToken: string,
  organizationId: string,
  incidentId: string,
  content: string,
) {
  return request<IncidentComment>(
    `${base(organizationId)}/${incidentId}/comments`,
    accessToken,
    { method: 'POST', body: JSON.stringify({ content }) },
  )
}

export function resolveIncident(
  accessToken: string,
  organizationId: string,
  incidentId: string,
  rootCause: string,
  resolutionSummary: string,
) {
  return request<Incident>(
    `${base(organizationId)}/${incidentId}/resolve`,
    accessToken,
    {
      method: 'POST',
      body: JSON.stringify({ rootCause, resolutionSummary }),
    },
  )
}

export function reopenIncident(
  accessToken: string,
  organizationId: string,
  incidentId: string,
) {
  return request<Incident>(
    `${base(organizationId)}/${incidentId}/reopen`,
    accessToken,
    { method: 'POST' },
  )
}
