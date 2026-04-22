import { ApiError, type ApiErrorBody } from '../../api/ApiError'
import type {
  ManualCheckResult,
  HealthCheckPage,
  MonitoredService,
  ServiceInput,
  ServicePage,
  ServiceStatus,
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
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

function base(organizationId: string) {
  return `/api/v1/organizations/${organizationId}/services`
}

export function listServices(
  accessToken: string,
  organizationId: string,
  filters: { search?: string; status?: ServiceStatus | '' } = {},
) {
  const query = new URLSearchParams()
  if (filters.search) query.set('search', filters.search)
  if (filters.status) query.set('status', filters.status)
  query.set('size', '50')
  return request<ServicePage>(
    `${base(organizationId)}?${query.toString()}`,
    accessToken,
  )
}

export function getService(
  accessToken: string,
  organizationId: string,
  serviceId: string,
) {
  return request<MonitoredService>(
    `${base(organizationId)}/${serviceId}`,
    accessToken,
  )
}

export function createService(
  accessToken: string,
  organizationId: string,
  input: ServiceInput,
) {
  return request<MonitoredService>(base(organizationId), accessToken, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateService(
  accessToken: string,
  organizationId: string,
  serviceId: string,
  input: ServiceInput,
) {
  return request<MonitoredService>(
    `${base(organizationId)}/${serviceId}`,
    accessToken,
    { method: 'PATCH', body: JSON.stringify(input) },
  )
}

export function deleteService(
  accessToken: string,
  organizationId: string,
  serviceId: string,
) {
  return request<void>(`${base(organizationId)}/${serviceId}`, accessToken, {
    method: 'DELETE',
  })
}

export function pauseService(
  accessToken: string,
  organizationId: string,
  serviceId: string,
) {
  return request<MonitoredService>(
    `${base(organizationId)}/${serviceId}/pause`,
    accessToken,
    { method: 'POST' },
  )
}

export function resumeService(
  accessToken: string,
  organizationId: string,
  serviceId: string,
) {
  return request<MonitoredService>(
    `${base(organizationId)}/${serviceId}/resume`,
    accessToken,
    { method: 'POST' },
  )
}

export function runManualCheck(
  accessToken: string,
  organizationId: string,
  serviceId: string,
) {
  return request<ManualCheckResult>(
    `${base(organizationId)}/${serviceId}/check`,
    accessToken,
    { method: 'POST' },
  )
}

export function listChecks(
  accessToken: string,
  organizationId: string,
  serviceId: string,
) {
  return request<HealthCheckPage>(
    `${base(organizationId)}/${serviceId}/checks?size=20`,
    accessToken,
  )
}
