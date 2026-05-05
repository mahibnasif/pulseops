import { lazy, Suspense } from 'react'
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
import { IncidentDetailsPage } from '../pages/IncidentDetailsPage'
import { IncidentFormPage } from '../pages/IncidentFormPage'
import { IncidentsPage } from '../pages/IncidentsPage'

const AnalyticsPage = lazy(() =>
  import('../pages/AnalyticsPage').then((module) => ({
    default: module.AnalyticsPage,
  })),
)

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
          <Route path="/incidents" element={<IncidentsPage />} />
          <Route path="/incidents/new" element={<IncidentFormPage />} />
          <Route
            path="/analytics"
            element={
              <Suspense
                fallback={
                  <div className="loading-panel">Loading analytics…</div>
                }
              >
                <AnalyticsPage />
              </Suspense>
            }
          />
          <Route
            path="/incidents/:incidentId"
            element={<IncidentDetailsPage />}
          />
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
