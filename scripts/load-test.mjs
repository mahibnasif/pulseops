import { performance } from 'node:perf_hooks'

const settings = {
  baseUrl: process.env.LOAD_BASE_URL ?? 'http://127.0.0.1:8080',
  path: process.env.LOAD_PATH ?? '/actuator/health',
  requests: readPositiveInteger('LOAD_REQUESTS', 250),
  concurrency: readPositiveInteger('LOAD_CONCURRENCY', 20),
  timeoutMilliseconds: readPositiveInteger('LOAD_TIMEOUT_MS', 5_000),
  expectedStatus: readPositiveInteger('LOAD_EXPECTED_STATUS', 200),
  maximumP95Milliseconds: readPositiveNumber('LOAD_MAX_P95_MS', 750),
  maximumErrorRate: readRate('LOAD_MAX_ERROR_RATE', 0.01),
}

const target = new URL(settings.path, ensureTrailingSlash(settings.baseUrl))
const displayTarget = new URL(target)
displayTarget.username = ''
displayTarget.password = ''

const durations = []
const failures = []
let nextRequest = 0

const startedAt = performance.now()
await Promise.all(
  Array.from(
    { length: Math.min(settings.concurrency, settings.requests) },
    async () => {
      while (true) {
        const requestNumber = nextRequest
        nextRequest += 1
        if (requestNumber >= settings.requests) {
          return
        }
        await executeRequest(requestNumber + 1)
      }
    },
  ),
)
const elapsedMilliseconds = performance.now() - startedAt

durations.sort((left, right) => left - right)
const errorRate = failures.length / settings.requests
const summary = {
  target: displayTarget.toString(),
  requests: settings.requests,
  concurrency: Math.min(settings.concurrency, settings.requests),
  successful: settings.requests - failures.length,
  failed: failures.length,
  errorRate: formatPercentage(errorRate),
  throughputPerSecond: round(
    settings.requests / (elapsedMilliseconds / 1_000),
  ),
  latencyMilliseconds: {
    p50: round(percentile(durations, 0.5)),
    p95: round(percentile(durations, 0.95)),
    maximum: round(durations.at(-1) ?? 0),
  },
  thresholds: {
    maximumP95Milliseconds: settings.maximumP95Milliseconds,
    maximumErrorRate: formatPercentage(settings.maximumErrorRate),
  },
}

console.log(JSON.stringify(summary, null, 2))

if (failures.length > 0) {
  console.error('Sample failures:')
  for (const failure of failures.slice(0, 5)) {
    console.error(`- request ${failure.request}: ${failure.message}`)
  }
}

const thresholdFailures = []
if (summary.latencyMilliseconds.p95 > settings.maximumP95Milliseconds) {
  thresholdFailures.push(
    `p95 latency ${summary.latencyMilliseconds.p95} ms exceeded ${settings.maximumP95Milliseconds} ms`,
  )
}
if (errorRate > settings.maximumErrorRate) {
  thresholdFailures.push(
    `error rate ${summary.errorRate} exceeded ${formatPercentage(settings.maximumErrorRate)}`,
  )
}

if (thresholdFailures.length > 0) {
  console.error(`Load smoke failed: ${thresholdFailures.join('; ')}.`)
  process.exitCode = 1
} else {
  console.log('Load smoke passed.')
}

async function executeRequest(requestNumber) {
  const requestStartedAt = performance.now()
  try {
    const response = await fetch(target, {
      headers: { accept: 'application/json' },
      signal: AbortSignal.timeout(settings.timeoutMilliseconds),
    })
    await response.arrayBuffer()
    if (response.status !== settings.expectedStatus) {
      failures.push({
        request: requestNumber,
        message: `expected HTTP ${settings.expectedStatus}, received ${response.status}`,
      })
    }
  } catch (error) {
    failures.push({
      request: requestNumber,
      message: error instanceof Error ? error.message : String(error),
    })
  } finally {
    durations.push(performance.now() - requestStartedAt)
  }
}

function ensureTrailingSlash(value) {
  return value.endsWith('/') ? value : `${value}/`
}

function percentile(values, quantile) {
  if (values.length === 0) {
    return 0
  }
  return values[Math.max(0, Math.ceil(values.length * quantile) - 1)]
}

function readPositiveInteger(name, fallback) {
  const value = readPositiveNumber(name, fallback)
  if (!Number.isInteger(value)) {
    throw new Error(`${name} must be a positive integer.`)
  }
  return value
}

function readPositiveNumber(name, fallback) {
  const raw = process.env[name]
  if (raw === undefined) {
    return fallback
  }
  const value = Number(raw)
  if (!Number.isFinite(value) || value <= 0) {
    throw new Error(`${name} must be a positive number.`)
  }
  return value
}

function readRate(name, fallback) {
  const raw = process.env[name]
  if (raw === undefined) {
    return fallback
  }
  const value = Number(raw)
  if (!Number.isFinite(value) || value < 0 || value > 1) {
    throw new Error(`${name} must be between 0 and 1.`)
  }
  return value
}

function round(value) {
  return Math.round(value * 100) / 100
}

function formatPercentage(value) {
  return `${round(value * 100)}%`
}
