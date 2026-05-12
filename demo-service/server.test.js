import assert from 'node:assert/strict'
import { after, before, test } from 'node:test'

import { createDemoServer } from './server.js'

let server
let baseUrl

before(async () => {
  server = createDemoServer()
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  const address = server.address()
  baseUrl = `http://127.0.0.1:${address.port}`
})

after(async () => {
  await new Promise((resolve, reject) =>
    server.close((error) => error ? reject(error) : resolve()),
  )
})

test('provides deterministic healthy, failing, JSON, and status endpoints', async () => {
  assert.equal((await fetch(`${baseUrl}/demo/healthy`)).status, 200)
  assert.equal((await fetch(`${baseUrl}/demo/failing`)).status, 500)
  assert.deepEqual(
    await (await fetch(`${baseUrl}/demo/json/healthy`)).json(),
    { status: 'healthy', ready: true },
  )
  assert.equal((await fetch(`${baseUrl}/demo/status/418`)).status, 418)
})

test('alternates the flaky endpoint predictably', async () => {
  assert.equal((await fetch(`${baseUrl}/demo/flaky`)).status, 200)
  assert.equal((await fetch(`${baseUrl}/demo/flaky`)).status, 503)
  assert.equal((await fetch(`${baseUrl}/demo/flaky`)).status, 200)
})

test('changes the controlled endpoint and validates unsafe input', async () => {
  const changed = await fetch(`${baseUrl}/demo/control`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ statusCode: 503, delayMilliseconds: 10 }),
  })
  assert.equal(changed.status, 200)
  assert.equal((await fetch(`${baseUrl}/demo/controlled`)).status, 503)

  const invalid = await fetch(`${baseUrl}/demo/control`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ statusCode: 700, delayMilliseconds: 0 }),
  })
  assert.equal(invalid.status, 400)
})
