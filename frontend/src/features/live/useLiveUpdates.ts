import { useContext } from 'react'
import { LiveUpdatesContext } from './liveUpdatesContext'

export function useLiveUpdates() {
  const context = useContext(LiveUpdatesContext)
  if (!context) {
    throw new Error('useLiveUpdates must be used within LiveUpdatesProvider.')
  }
  return context
}
