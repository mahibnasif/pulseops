export interface ApiErrorBody {
  code?: string
  message?: string
  fieldErrors?: Record<string, string>
  traceId?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>
  readonly traceId?: string

  constructor(status: number, body: ApiErrorBody) {
    super(body.message ?? 'The request could not be completed.')
    this.name = 'ApiError'
    this.status = status
    this.code = body.code ?? 'REQUEST_FAILED'
    this.fieldErrors = body.fieldErrors ?? {}
    this.traceId = body.traceId
  }
}
