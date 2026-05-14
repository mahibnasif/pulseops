import {
  expect,
  test,
  type APIRequestContext,
  type Page,
} from '@playwright/test'

const demoBaseUrl = process.env.E2E_DEMO_URL ?? 'http://127.0.0.1:8191'

test('runs the monitored-service incident journey from registration to resolution', async ({
  page,
  request,
}) => {
  const unique = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`
  const email = `playwright-${unique}@example.com`
  const serviceName = `Checkout API ${unique}`

  await setDemoState(request, 200)

  await page.goto('/register')
  await page.getByLabel('First name').fill('Phase')
  await page.getByLabel('Last name').fill('Tester')
  await page.getByLabel('Email address').fill(email)
  await page.locator('input[name="password"]').fill('Correct-Horse-Phase-10')
  await page.getByLabel('Confirm password').fill('Correct-Horse-Phase-10')
  await page.getByRole('button', { name: 'Create account' }).click()

  await expect(
    page.getByRole('heading', { name: 'Bring your team into PulseOps.' }),
  ).toBeVisible()
  await page.getByLabel('Organization name').fill(`Phase 10 ${unique}`)
  await page.getByRole('button', { name: 'Create organization' }).click()
  await expect(page.getByRole('heading', { name: `Phase 10 ${unique}` })).toBeVisible()

  await page.getByRole('link', { name: 'Services' }).click()
  await page.getByRole('link', { name: 'Add service' }).click()
  await page.getByLabel('Service name').fill(serviceName)
  await page.getByLabel('Protocol').selectOption('HTTP')
  await page.getByLabel('URL').fill('http://demo-service:8090/demo/controlled')
  await page.getByLabel('Failures before down').fill('2')
  await page.getByLabel('Successes before recovery').fill('1')
  await page.getByRole('button', { name: 'Create service' }).click()

  await expect(page.getByRole('heading', { name: serviceName })).toBeVisible()
  await runManualCheck(page, 'Check passed')

  await setDemoState(request, 503)
  await runManualCheck(page, 'Check failed')
  await runManualCheck(page, 'Check failed')
  await expect(page.getByText('down', { exact: true }).first()).toBeVisible()

  await page.getByRole('link', { name: 'Incidents' }).click()
  await page.getByRole('link', { name: `${serviceName} is down` }).click()
  await expect(page.getByText('Monitoring', { exact: true })).toBeVisible()

  await page.getByLabel('Incident assignee').selectOption({ label: 'Phase Tester' })
  await expect(page.getByLabel('Incident assignee')).toHaveValue(/.+/)
  await page.getByLabel('Add response note').fill('Rollback investigation started.')
  await page.getByRole('button', { name: 'Add comment' }).click()
  await expect(page.getByText('Rollback investigation started.')).toBeVisible()

  await setDemoState(request, 200)
  await page.getByRole('link', { name: serviceName }).click()
  await runManualCheck(page, 'Check passed')
  await expect(page.getByText('operational', { exact: true }).first()).toBeVisible()

  await page.getByRole('link', { name: 'Incidents' }).click()
  await page.getByRole('link', { name: `${serviceName} is down` }).click()
  await expect(page.getByText('monitoring', { exact: true }).first()).toBeVisible()
  await page.getByLabel(/Root cause/).fill('A controlled dependency failure.')
  await page.getByLabel('Resolution summary').fill('Restored the dependency and verified recovery.')
  await page.getByRole('button', { name: 'Mark resolved' }).click()

  await expect(page.getByText('resolved', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('A controlled dependency failure.')).toBeVisible()
  await expect(
    page.getByText('Restored the dependency and verified recovery.'),
  ).toBeVisible()
})

async function setDemoState(
  request: APIRequestContext,
  statusCode: number,
) {
  const response = await request.post(`${demoBaseUrl}/demo/control`, {
    data: { statusCode, delayMilliseconds: 0 },
  })
  expect(response.ok(), await response.text()).toBe(true)
}

async function runManualCheck(page: Page, result: string) {
  await page.getByRole('button', { name: 'Run manual check' }).click()
  await expect(page.getByText(result, { exact: true })).toBeVisible()
}
