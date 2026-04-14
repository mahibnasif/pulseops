import { Outlet } from 'react-router-dom'
import { OrganizationProvider } from './OrganizationProvider'

export function OrganizationRoute() {
  return (
    <OrganizationProvider>
      <Outlet />
    </OrganizationProvider>
  )
}
