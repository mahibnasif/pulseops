export type ServiceType = 'HTTP' | 'HTTPS' | 'TCP' | 'JSON_API'
export type HttpMethod = 'GET' | 'HEAD'
export type ServiceStatus =
  | 'UNKNOWN'
  | 'OPERATIONAL'
  | 'DEGRADED'
  | 'DOWN'
  | 'PAUSED'

export interface MonitoredService {
  id: string
  organizationId: string
  name: string
  description: string | null
  serviceType: ServiceType
  url: string
  httpMethod: HttpMethod
  expectedStatusCode: number
  expectedResponseText: string | null
  expectedJsonPath: string | null
  expectedJsonValue: string | null
  timeoutMilliseconds: number
  checkIntervalSeconds: number
  failureThreshold: number
  recoveryThreshold: number
  degradedLatencyThresholdMilliseconds: number
  status: ServiceStatus
  active: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
  lastCheckedAt: string | null
  lastSuccessfulCheckAt: string | null
  lastFailureAt: string | null
}

export interface ServicePage {
  content: MonitoredService[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ServiceInput {
  name: string
  description?: string
  serviceType: 'HTTP' | 'HTTPS'
  url: string
  httpMethod: HttpMethod
  expectedStatusCode: number
  expectedResponseText?: string
  expectedJsonPath?: string
  expectedJsonValue?: string
  timeoutMilliseconds: number
  checkIntervalSeconds: number
  failureThreshold: number
  recoveryThreshold: number
  degradedLatencyThresholdMilliseconds: number
}

export interface ManualCheckResult {
  checkedAt: string
  success: boolean
  degraded: boolean
  statusCode: number | null
  responseTimeMilliseconds: number
  errorType: string | null
  errorMessage: string | null
  responseValidationPassed: boolean
  responseExcerpt: string | null
  affectsServiceStatus: false
}
