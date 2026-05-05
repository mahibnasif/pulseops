import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/useAuth'
import { useOrganizations } from '../organizations/useOrganizations'
import {
  consumeLiveEventStream,
  LiveStreamUnauthorizedError,
} from './liveEventStream'
import { LiveUpdatesContext } from './liveUpdatesContext'
import type {
  LiveConnectionStatus,
  OrganizationLiveEvent,
} from './types'

const MAX_REMEMBERED_EVENT_IDS = 500

export function LiveUpdatesProvider({ children }: { children: ReactNode }) {
  const auth = useAuth()
  const { currentOrganization } = useOrganizations()
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<LiveConnectionStatus>('idle')
  const [lastEventAt, setLastEventAt] = useState<string | null>(null)
  const organizationId = currentOrganization?.id
  const accessToken = auth.accessToken

  const refreshAuthoritativeData = useCallback(
    async (event?: OrganizationLiveEvent) => {
      if (!organizationId) return
      const keys = event ? keysFor(event) : [
        'services',
        'service',
        'service-checks',
        'incidents',
        'incident',
        'analytics',
        'dashboard-summary',
      ]
      await Promise.all(
        keys.map((key) =>
          queryClient.invalidateQueries({
            predicate: (query) =>
              query.queryKey[0] === key &&
              query.queryKey.some((part) => part === organizationId),
          }),
        ),
      )
      if (event?.type === 'NOTIFICATION_CREATED') {
        await queryClient.invalidateQueries({ queryKey: ['notifications'] })
      }
    },
    [organizationId, queryClient],
  )

  useEffect(() => {
    if (!organizationId || !accessToken) {
      setStatus('idle')
      setLastEventAt(null)
      return
    }

    const controller = new AbortController()
    const seenIds = new Set<string>()
    const idOrder: string[] = []
    let lastEventId: string | undefined

    const remember = (id: string) => {
      if (seenIds.has(id)) return false
      seenIds.add(id)
      idOrder.push(id)
      if (idOrder.length > MAX_REMEMBERED_EVENT_IDS) {
        seenIds.delete(idOrder.shift()!)
      }
      return true
    }

    const run = async () => {
      let attempt = 0
      let currentToken = accessToken
      while (!controller.signal.aborted) {
        if (!navigator.onLine) {
          setStatus('offline')
          await delay(1000, controller.signal)
          continue
        }
        setStatus(attempt === 0 ? 'connecting' : 'reconnecting')
        try {
          await consumeLiveEventStream({
            organizationId,
            accessToken: currentToken,
            signal: controller.signal,
            lastEventId,
            onOpen: () => {
              attempt = 0
              setStatus('live')
              void refreshAuthoritativeData()
            },
            onEvent: (event) => {
              if (
                event.organizationId !== organizationId ||
                !remember(event.id)
              ) {
                return
              }
              lastEventId = event.id
              setLastEventAt(event.occurredAt)
              if (event.type !== 'CONNECTION_READY') {
                void refreshAuthoritativeData(event)
              }
            },
          })
        } catch (error) {
          if (controller.signal.aborted) return
          if (error instanceof LiveStreamUnauthorizedError) {
            try {
              currentToken = await auth.refresh()
              attempt = 0
              continue
            } catch {
              return
            }
          }
        }
        if (controller.signal.aborted) return
        attempt += 1
        setStatus(navigator.onLine ? 'reconnecting' : 'offline')
        const retryMilliseconds = Math.min(30_000, 1000 * 2 ** (attempt - 1))
        await delay(retryMilliseconds, controller.signal)
      }
    }

    void run()
    return () => controller.abort()
  }, [
    accessToken,
    auth,
    organizationId,
    refreshAuthoritativeData,
  ])

  const value = useMemo(
    () => ({ status, lastEventAt }),
    [lastEventAt, status],
  )
  return (
    <LiveUpdatesContext.Provider value={value}>
      {children}
    </LiveUpdatesContext.Provider>
  )
}

function keysFor(event: OrganizationLiveEvent) {
  if (
    event.type.startsWith('SERVICE_') ||
    event.type === 'HEALTH_CHECK_RECORDED'
  ) {
    return [
      'services',
      'service',
      'service-checks',
      'analytics',
      'dashboard-summary',
    ]
  }
  if (event.type.startsWith('INCIDENT_')) {
    return [
      'incidents',
      'incident',
      'services',
      'analytics',
      'dashboard-summary',
    ]
  }
  return []
}

function delay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    if (signal.aborted) {
      resolve()
      return
    }
    const timeout = window.setTimeout(resolve, milliseconds)
    signal.addEventListener(
      'abort',
      () => {
        window.clearTimeout(timeout)
        resolve()
      },
      { once: true },
    )
  })
}
