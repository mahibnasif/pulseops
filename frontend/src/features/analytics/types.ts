export interface AnalyticsSnapshot {
  range: {
    from: string
    to: string
    bucket: 'HOUR' | 'DAY'
    timezone: 'UTC'
    uptimeMethod: string
  }
  services: {
    total: number
    operational: number
    degraded: number
    down: number
    paused: number
    unknown: number
  }
  reliability: {
    totalChecks: number
    successfulChecks: number
    failedChecks: number
    uptimePercentage: number | null
    averageResponseTimeMilliseconds: number | null
    p50ResponseTimeMilliseconds: number | null
    p95ResponseTimeMilliseconds: number | null
  }
  incidents: {
    total: number
    active: number
    critical: number
    meanTimeToAcknowledgeMinutes: number | null
    meanTimeToResolveMinutes: number | null
    longestIncidentMinutes: number | null
  }
  healthTrend: Array<{
    bucketStart: string
    totalChecks: number
    successfulChecks: number
    failedChecks: number
    uptimePercentage: number | null
    averageResponseTimeMilliseconds: number | null
  }>
  incidentTrend: Array<{
    bucketStart: string
    totalIncidents: number
    resolvedIncidents: number
  }>
  incidentsBySeverity: Array<{
    severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
    count: number
  }>
  serviceReliability: Array<{
    serviceId: string
    serviceName: string
    status: 'UNKNOWN' | 'OPERATIONAL' | 'DEGRADED' | 'DOWN' | 'PAUSED'
    totalChecks: number
    failedChecks: number
    uptimePercentage: number | null
    averageResponseTimeMilliseconds: number | null
    p95ResponseTimeMilliseconds: number | null
    incidentCount: number
  }>
}

export interface AnalyticsRange {
  from: string
  to: string
}
