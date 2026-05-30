import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { chromium, expect, request } from '@playwright/test'

const baseUrl = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:5180'
const demoBaseUrl = process.env.E2E_DEMO_URL ?? 'http://127.0.0.1:8191'
const screenshotDirectory = resolve(
  process.env.PORTFOLIO_SCREENSHOT_DIR
    ?? '../docs/assets/screenshots',
)

await mkdir(screenshotDirectory, { recursive: true })

const browser = await chromium.launch()
const api = await request.newContext()
const context = await browser.newContext({
  baseURL: baseUrl,
  viewport: { width: 1440, height: 1000 },
  deviceScaleFactor: 1,
  colorScheme: 'light',
})
const page = await context.newPage()
await page.emulateMedia({ reducedMotion: 'reduce' })

try {
  await captureLanding(page)
  await registerPortfolioUser(page)
  await createOrganization(page)

  const healthyPath = await createService(page, {
    name: 'Customer Web',
    description: 'Public storefront and account experience.',
    url: 'http://demo-service:8090/demo/healthy',
    degradedAfter: 800,
  })
  await runManualCheck(page, 'Check passed')
  await runManualCheck(page, 'Check passed')

  await createService(page, {
    name: 'Billing API',
    description: 'Processes subscription and invoice requests.',
    url: 'http://demo-service:8090/demo/slow?delay=450',
    degradedAfter: 150,
  })
  await runManualCheck(page, 'Check passed')

  const checkoutPath = await createService(page, {
    name: 'Checkout API',
    description: 'Coordinates checkout sessions and payment confirmation.',
    url: 'http://demo-service:8090/demo/controlled',
    degradedAfter: 750,
    failureThreshold: 2,
  })
  await setDemoState(api, 503)
  await runManualCheck(page, 'Check failed')
  await runManualCheck(page, 'Check failed')
  await expect(page.getByText('down', { exact: true }).first()).toBeVisible()

  await createService(page, {
    name: 'Legacy Status Page',
    description: 'Paused during migration to the consolidated status site.',
    url: 'http://demo-service:8090/demo/healthy',
    degradedAfter: 1000,
  })
  await page.getByRole('button', { name: 'Pause' }).click()
  await expect(page.getByText('paused', { exact: true }).first()).toBeVisible()

  await page.goto('/dashboard')
  await expect(
    page.getByRole('heading', { name: 'Northstar Engineering' }),
  ).toBeVisible()
  await expect(page.getByText('4', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('1', { exact: true }).last()).toBeVisible()
  await screenshot(page, 'dashboard.png')

  await page.goto('/services')
  await expect(page.getByText('4 services', { exact: true })).toBeVisible()
  await screenshot(page, 'services.png')

  await page.goto(checkoutPath)
  await expect(
    page.getByRole('heading', { name: 'Checkout API' }),
  ).toBeVisible()
  await screenshot(page, 'service-details.png')

  await page.goto('/dashboard')
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(
    page.getByRole('heading', { name: 'Northstar Engineering' }),
  ).toBeVisible()
  await screenshot(page, 'mobile-dashboard.png')

  await page.setViewportSize({ width: 1440, height: 1120 })
  await prepareIncident(page)
  await page.evaluate(() => window.scrollTo(0, 0))
  await screenshot(page, 'incident-response.png')

  await page.goto('/analytics')
  await expect(
    page.getByRole('heading', { name: 'Operational performance' }),
  ).toBeVisible()
  await expect(page.getByText('60.00%', { exact: true })).toBeVisible()
  await page.waitForTimeout(500)
  await screenshot(page, 'analytics.png')

  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(healthyPath)
  await expect(
    page.getByRole('heading', { name: 'Customer Web' }),
  ).toBeVisible()
} finally {
  await context.close()
  await api.dispose()
  await browser.close()
}

console.log(`Portfolio screenshots written to ${screenshotDirectory}`)

async function captureLanding(page) {
  await page.goto('/')
  await expect(
    page.getByRole('heading', {
      name: 'Know when services fail. Act before users notice.',
    }),
  ).toBeVisible()
  await screenshot(page, 'landing.png')
}

async function registerPortfolioUser(page) {
  await page.goto('/register')
  await page.getByLabel('First name').fill('Avery')
  await page.getByLabel('Last name').fill('Morgan')
  await page.getByLabel('Email address').fill('avery.morgan@example.com')
  await page.locator('input[name="password"]').fill('Portfolio-Demo-Only-2026')
  await page.getByLabel('Confirm password').fill('Portfolio-Demo-Only-2026')
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(
    page.getByRole('heading', { name: 'Bring your team into PulseOps.' }),
  ).toBeVisible()
}

async function createOrganization(page) {
  await page.getByLabel('Organization name').fill('Northstar Engineering')
  await page
    .getByLabel(/Description/)
    .fill('Platform operations for customer-facing production services.')
  await page.getByRole('button', { name: 'Create organization' }).click()
  await expect(
    page.getByRole('heading', { name: 'Northstar Engineering' }),
  ).toBeVisible()
}

async function createService(
  page,
  {
    name,
    description,
    url,
    degradedAfter,
    failureThreshold = 3,
  },
) {
  await page.goto('/services/new')
  await page.getByLabel('Service name').fill(name)
  await page.getByLabel('Protocol').selectOption('HTTP')
  await page.getByLabel('URL').fill(url)
  await page.getByLabel(/Description/).fill(description)
  await page.getByLabel('Degraded after (ms)').fill(String(degradedAfter))
  await page
    .getByLabel('Failures before down')
    .fill(String(failureThreshold))
  await page.getByLabel('Successes before recovery').fill('1')
  await page.getByRole('button', { name: 'Create service' }).click()
  await expect(page.getByRole('heading', { name })).toBeVisible()
  return new URL(page.url()).pathname
}

async function prepareIncident(page) {
  await page.goto('/incidents')
  await page.getByRole('link', { name: 'Checkout API is down' }).click()
  await expect(
    page.getByRole('heading', { name: 'Checkout API is down' }),
  ).toBeVisible()
  await page.getByLabel('Incident assignee').selectOption({
    label: 'Avery Morgan',
  })
  await expect(page.getByLabel('Incident assignee')).toHaveValue(/.+/)
  await page.getByLabel('Incident severity').selectOption('CRITICAL')
  await expect(page.getByLabel('Incident severity')).toHaveValue('CRITICAL')
  await page.getByLabel('Incident status').selectOption('INVESTIGATING')
  await expect(page.getByLabel('Incident status')).toHaveValue('INVESTIGATING')
  const responseNote = page.getByLabel('Add response note')
  const commentText =
    'Payment provider errors reproduced; rollback owner paged.'
  await responseNote.fill(commentText)
  await page.getByRole('button', { name: 'Add comment' }).click()
  await expect(responseNote).toHaveValue('')
  await expect(
    page.locator('.incident-comments article').filter({
      hasText: commentText,
    }),
  ).toBeVisible()
}

async function runManualCheck(page, result) {
  await page.getByRole('button', { name: 'Run manual check' }).click()
  await expect(page.getByText(result, { exact: true })).toBeVisible()
}

async function setDemoState(api, statusCode) {
  const response = await api.post(`${demoBaseUrl}/demo/control`, {
    data: { statusCode, delayMilliseconds: 0 },
  })
  if (!response.ok()) {
    throw new Error(`Demo control failed: ${await response.text()}`)
  }
}

async function screenshot(page, filename) {
  await page.screenshot({
    path: resolve(screenshotDirectory, filename),
    fullPage: false,
    animations: 'disabled',
  })
}
