import { createContext } from 'react'
import type { LiveConnectionStatus } from './types'

export interface LiveUpdatesContextValue {
  status: LiveConnectionStatus
  lastEventAt: string | null
}

export const LiveUpdatesContext =
  createContext<LiveUpdatesContextValue | null>(null)
