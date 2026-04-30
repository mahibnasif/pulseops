import { Outlet } from 'react-router-dom'
import { OrganizationProvider } from './OrganizationProvider'
import { LiveUpdatesProvider } from '../live/LiveUpdatesProvider'

export function OrganizationRoute() {
  return (
    <OrganizationProvider>
      <LiveUpdatesProvider>
        <Outlet />
      </LiveUpdatesProvider>
    </OrganizationProvider>
  )
}
