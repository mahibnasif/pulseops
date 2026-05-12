import { createServer } from 'node:http'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const port = Number(process.env.PORT ?? 8090)
const maximumDelayMilliseconds = 5_000

export function createDemoServer() {
  const state = {
    statusCode: 200,
    delayMilliseconds: 0,
    flakyRequestCount: 0,
  }

  return createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? '/', 'http://demo-service')

      if (request.method === 'GET' && url.pathname === '/actuator/health') {
        return json(response, 200, { status: 'UP' })
      }
      if (request.method === 'GET' && url.pathname === '/demo/healthy') {
        return json(response, 200, { status: 'healthy' })
      }
      if (request.method === 'GET' && url.pathname === '/demo/failing') {
        return json(response, 500, { status: 'failing' })
      }
      if (request.method === 'GET' && url.pathname === '/demo/flaky') {
        state.flakyRequestCount += 1
        const successful = state.flakyRequestCount % 2 === 1
        return json(
          response,
          successful ? 200 : 503,
          { status: successful ? 'healthy' : 'failing' },
        )
      }
      if (request.method === 'GET' && url.pathname === '/demo/slow') {
        const requestedDelay = Number(url.searchParams.get('delay') ?? 1_000)
        const delay = boundedDelay(requestedDelay)
        await wait(delay)
        return json(response, 200, { status: 'healthy', delayMilliseconds: delay })
      }
      if (request.method === 'GET' && url.pathname === '/demo/json/healthy') {
        return json(response, 200, { status: 'healthy', ready: true })
      }
      if (request.method === 'GET' && url.pathname === '/demo/json/unhealthy') {
        return json(response, 200, { status: 'unhealthy', ready: false })
      }
      if (request.method === 'GET' && url.pathname === '/demo/controlled') {
        await wait(state.delayMilliseconds)
        return json(
          response,
          state.statusCode,
          { status: state.statusCode < 400 ? 'healthy' : 'failing' },
        )
      }
      if (request.method === 'GET' && url.pathname.startsWith('/demo/status/')) {
        const statusCode = Number(url.pathname.slice('/demo/status/'.length))
        if (!Number.isInteger(statusCode) || statusCode < 100 || statusCode > 599) {
          return json(response, 400, { error: 'Status must be between 100 and 599.' })
        }
        response.writeHead(statusCode)
        return response.end()
      }
      if (request.method === 'POST' && url.pathname === '/demo/control') {
        const input = await readJson(request)
        if (
          !Number.isInteger(input.statusCode)
          || input.statusCode < 100
          || input.statusCode > 599
        ) {
          return json(response, 400, { error: 'statusCode must be between 100 and 599.' })
        }
        if (
          !Number.isInteger(input.delayMilliseconds)
          || input.delayMilliseconds < 0
          || input.delayMilliseconds > maximumDelayMilliseconds
        ) {
          return json(
            response,
            400,
            { error: `delayMilliseconds must be between 0 and ${maximumDelayMilliseconds}.` },
          )
        }
        state.statusCode = input.statusCode
        state.delayMilliseconds = input.delayMilliseconds
        return json(response, 200, {
          statusCode: state.statusCode,
          delayMilliseconds: state.delayMilliseconds,
        })
      }

      return json(response, 404, { error: 'Demo route not found.' })
    } catch {
      return json(response, 400, { error: 'Malformed demo request.' })
    }
  })
}

function boundedDelay(value) {
  if (!Number.isFinite(value)) {
    return 1_000
  }
  return Math.max(0, Math.min(maximumDelayMilliseconds, Math.trunc(value)))
}

async function readJson(request) {
  const chunks = []
  let size = 0
  for await (const chunk of request) {
    size += chunk.length
    if (size > 16_384) {
      throw new Error('Request body is too large.')
    }
    chunks.push(chunk)
  }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'))
}

function json(response, statusCode, body) {
  const content = JSON.stringify(body)
  response.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(content),
    'Cache-Control': 'no-store',
  })
  response.end(content)
}

function wait(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

const isEntrypoint = process.argv[1]
  && fileURLToPath(import.meta.url) === resolve(process.argv[1])

if (isEntrypoint) {
  createDemoServer().listen(port, '0.0.0.0', () => {
    console.log(`PulseOps demo service listening on port ${port}.`)
  })
}
