import { useContext } from 'react'
import { OrganizationContext } from './organizationContext'

export function useOrganizations() {
  const context = useContext(OrganizationContext)
  if (!context) {
    throw new Error('useOrganizations must be used within OrganizationProvider.')
  }
  return context
}
