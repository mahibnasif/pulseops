export type MembershipRole = 'ADMIN' | 'ENGINEER' | 'VIEWER'
export type MembershipStatus = 'ACTIVE' | 'LEFT' | 'REMOVED'

export interface Organization {
  id: string
  name: string
  slug: string
  description: string | null
  ownerId: string
  currentUserRole: MembershipRole
  createdAt: string
  updatedAt: string
}

export interface OrganizationMember {
  membershipId: string
  userId: string
  firstName: string
  lastName: string
  email: string
  role: MembershipRole
  status: MembershipStatus
  joinedAt: string
}

export interface OrganizationInvitation {
  id: string
  organizationId: string
  organizationName: string
  email: string
  role: MembershipRole
  expiresAt: string
  acceptedAt: string | null
  createdAt: string
}

export interface CreatedInvitation {
  invitation: OrganizationInvitation
  acceptanceToken: string
}

export interface CreateOrganizationInput {
  name: string
  slug?: string
  description?: string
}
