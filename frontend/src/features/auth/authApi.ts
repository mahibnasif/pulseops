import { ApiError, type ApiErrorBody } from '../../api/ApiError'
import type { AuthSession, LoginInput, RegisterInput } from './types'

const AUTH_BASE_PATH = '/api/v1/auth'

let inFlightRefresh: Promise<AuthSession> | null = null

async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const response = await fetch(path, {
    ...options,
    credentials: 'include',
    headers: {
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

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function login(input: LoginInput) {
  return request<AuthSession>(`${AUTH_BASE_PATH}/login`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function register(input: RegisterInput) {
  return request<AuthSession>(`${AUTH_BASE_PATH}/register`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function refreshSession() {
  if (!inFlightRefresh) {
    inFlightRefresh = request<AuthSession>(`${AUTH_BASE_PATH}/refresh`, {
      method: 'POST',
    }).finally(() => {
      inFlightRefresh = null
    })
  }
  return inFlightRefresh
}

export function logout() {
  return request<void>(`${AUTH_BASE_PATH}/logout`, {
    method: 'POST',
  })
}
