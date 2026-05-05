import { ApiError, type ApiErrorBody } from '../../api/ApiError'
import type { AnalyticsRange, AnalyticsSnapshot } from './types'

export async function getAnalytics(
  accessToken: string,
  organizationId: string,
  range: AnalyticsRange,
) {
  const query = new URLSearchParams({
    from: range.from,
    to: range.to,
  })
  const response = await fetch(
    `/api/v1/organizations/${organizationId}/analytics?${query.toString()}`,
    {
      credentials: 'include',
      headers: {
        Authorization: `Bearer ${accessToken}`,
        'X-Requested-With': 'PulseOps',
      },
    },
  )
  if (!response.ok) {
    let body: ApiErrorBody = {}
    try {
      body = (await response.json()) as ApiErrorBody
    } catch {
      body = {}
    }
    throw new ApiError(response.status, body)
  }
  return (await response.json()) as AnalyticsSnapshot
}
