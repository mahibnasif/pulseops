import { Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '../features/auth/ProtectedRoute'
import { OrganizationRoute } from '../features/organizations/OrganizationRoute'
import { DashboardPage } from '../pages/DashboardPage'
import { LandingPage } from '../pages/LandingPage'
import { LoginPage } from '../pages/LoginPage'
import { OrganizationSettingsPage } from '../pages/OrganizationSettingsPage'
import { RegisterPage } from '../pages/RegisterPage'
import { ServicesPage } from '../pages/ServicesPage'
import { ServiceFormPage } from '../pages/ServiceFormPage'
import { ServiceDetailsPage } from '../pages/ServiceDetailsPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<OrganizationRoute />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/services" element={<ServicesPage />} />
          <Route path="/services/new" element={<ServiceFormPage />} />
          <Route path="/services/:serviceId" element={<ServiceDetailsPage />} />
          <Route path="/services/:serviceId/edit" element={<ServiceFormPage />} />
          <Route
            path="/organizations/:organizationId/settings"
            element={<OrganizationSettingsPage />}
          />
        </Route>
      </Route>
      <Route path="*" element={<LandingPage />} />
    </Routes>
  )
}
