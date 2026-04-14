import { ApiError, type ApiErrorBody } from '../../api/ApiError'
import type {
  CreatedInvitation,
  CreateOrganizationInput,
  MembershipRole,
  Organization,
  OrganizationInvitation,
  OrganizationMember,
} from './types'

const ORGANIZATIONS_PATH = '/api/v1/organizations'

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

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export function listOrganizations(accessToken: string) {
  return request<Organization[]>(ORGANIZATIONS_PATH, accessToken)
}

export function createOrganization(
  accessToken: string,
  input: CreateOrganizationInput,
) {
  return request<Organization>(ORGANIZATIONS_PATH, accessToken, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateOrganization(
  accessToken: string,
  organizationId: string,
  input: Partial<Pick<Organization, 'name' | 'slug' | 'description'>>,
) {
  return request<Organization>(
    `${ORGANIZATIONS_PATH}/${organizationId}`,
    accessToken,
    {
      method: 'PATCH',
      body: JSON.stringify(input),
    },
  )
}

export function listMembers(accessToken: string, organizationId: string) {
  return request<OrganizationMember[]>(
    `${ORGANIZATIONS_PATH}/${organizationId}/members`,
    accessToken,
  )
}

export function changeMemberRole(
  accessToken: string,
  organizationId: string,
  userId: string,
  role: MembershipRole,
) {
  return request<OrganizationMember>(
    `${ORGANIZATIONS_PATH}/${organizationId}/members/${userId}`,
    accessToken,
    {
      method: 'PATCH',
      body: JSON.stringify({ role }),
    },
  )
}

export function removeMember(
  accessToken: string,
  organizationId: string,
  userId: string,
) {
  return request<void>(
    `${ORGANIZATIONS_PATH}/${organizationId}/members/${userId}`,
    accessToken,
    { method: 'DELETE' },
  )
}

export function transferOwnership(
  accessToken: string,
  organizationId: string,
  newOwnerId: string,
) {
  return request<Organization>(
    `${ORGANIZATIONS_PATH}/${organizationId}/transfer-ownership`,
    accessToken,
    {
      method: 'POST',
      body: JSON.stringify({ newOwnerId }),
    },
  )
}

export function leaveOrganization(
  accessToken: string,
  organizationId: string,
) {
  return request<void>(
    `${ORGANIZATIONS_PATH}/${organizationId}/leave`,
    accessToken,
    { method: 'POST' },
  )
}

export function listOrganizationInvitations(
  accessToken: string,
  organizationId: string,
) {
  return request<OrganizationInvitation[]>(
    `${ORGANIZATIONS_PATH}/${organizationId}/invitations`,
    accessToken,
  )
}

export function createInvitation(
  accessToken: string,
  organizationId: string,
  email: string,
  role: MembershipRole,
) {
  return request<CreatedInvitation>(
    `${ORGANIZATIONS_PATH}/${organizationId}/invitations`,
    accessToken,
    {
      method: 'POST',
      body: JSON.stringify({ email, role }),
    },
  )
}

export function cancelInvitation(
  accessToken: string,
  organizationId: string,
  invitationId: string,
) {
  return request<void>(
    `${ORGANIZATIONS_PATH}/${organizationId}/invitations/${invitationId}`,
    accessToken,
    { method: 'DELETE' },
  )
}

export function listPendingInvitations(accessToken: string) {
  return request<OrganizationInvitation[]>(
    '/api/v1/invitations',
    accessToken,
  )
}

export function acceptInvitation(accessToken: string, invitationId: string) {
  return request<Organization>(
    `/api/v1/invitations/${invitationId}/accept`,
    accessToken,
    { method: 'POST' },
  )
}
