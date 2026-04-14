import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/useAuth'
import * as organizationApi from './organizationApi'
import { OrganizationContext } from './organizationContext'
import type { CreateOrganizationInput, Organization } from './types'

const SELECTED_ORGANIZATION_KEY = 'pulseops.selectedOrganizationId'
const EMPTY_ORGANIZATIONS: Organization[] = []

export function OrganizationProvider({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(() =>
    window.localStorage.getItem(SELECTED_ORGANIZATION_KEY),
  )
  const accessToken = auth.accessToken
  const organizationsQuery = useQuery({
    queryKey: ['organizations'],
    queryFn: () => organizationApi.listOrganizations(accessToken!),
    enabled: Boolean(accessToken),
  })
  const organizations = organizationsQuery.data ?? EMPTY_ORGANIZATIONS

  useEffect(() => {
    if (organizationsQuery.isLoading) {
      return
    }
    const selectedStillExists = organizations.some(
      (organization) => organization.id === selectedId,
    )
    const nextId = selectedStillExists
      ? selectedId
      : (organizations[0]?.id ?? null)
    if (nextId !== selectedId) {
      setSelectedId(nextId)
    }
    if (nextId) {
      window.localStorage.setItem(SELECTED_ORGANIZATION_KEY, nextId)
    } else {
      window.localStorage.removeItem(SELECTED_ORGANIZATION_KEY)
    }
  }, [organizations, organizationsQuery.isLoading, selectedId])

  const selectOrganization = useCallback((organizationId: string) => {
    setSelectedId(organizationId)
    window.localStorage.setItem(SELECTED_ORGANIZATION_KEY, organizationId)
  }, [])

  const refreshOrganizations = useCallback(async () => {
    await queryClient.invalidateQueries({ queryKey: ['organizations'] })
  }, [queryClient])

  const createOrganization = useCallback(
    async (input: CreateOrganizationInput) => {
      if (!accessToken) {
        throw new Error('An authenticated session is required.')
      }
      const created = await organizationApi.createOrganization(
        accessToken,
        input,
      )
      await refreshOrganizations()
      selectOrganization(created.id)
      return created
    },
    [accessToken, refreshOrganizations, selectOrganization],
  )

  const currentOrganization =
    organizations.find((organization) => organization.id === selectedId) ?? null

  const value = useMemo(
    () => ({
      organizations,
      currentOrganization,
      loading: organizationsQuery.isLoading,
      error: organizationsQuery.error,
      selectOrganization,
      createOrganization,
      refreshOrganizations,
    }),
    [
      organizations,
      currentOrganization,
      organizationsQuery.error,
      organizationsQuery.isLoading,
      selectOrganization,
      createOrganization,
      refreshOrganizations,
    ],
  )

  return (
    <OrganizationContext.Provider value={value}>
      {children}
    </OrganizationContext.Provider>
  )
}
