import { createContext } from 'react'
import type { CreateOrganizationInput, Organization } from './types'

export interface OrganizationContextValue {
  organizations: Organization[]
  currentOrganization: Organization | null
  loading: boolean
  error: Error | null
  selectOrganization: (organizationId: string) => void
  createOrganization: (
    input: CreateOrganizationInput,
  ) => Promise<Organization>
  refreshOrganizations: () => Promise<void>
}

export const OrganizationContext =
  createContext<OrganizationContextValue | null>(null)
