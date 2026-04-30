import type { OrganizationLiveEvent } from './types'

export class LiveStreamUnauthorizedError extends Error {
  constructor() {
    super('The live event stream requires a refreshed session.')
    this.name = 'LiveStreamUnauthorizedError'
  }
}

interface StreamOptions {
  organizationId: string
  accessToken: string
  signal: AbortSignal
  lastEventId?: string
  onOpen: () => void
  onEvent: (event: OrganizationLiveEvent) => void
}

export async function consumeLiveEventStream({
  organizationId,
  accessToken,
  signal,
  lastEventId,
  onOpen,
  onEvent,
}: StreamOptions) {
  const response = await fetch(
    `/api/v1/organizations/${organizationId}/events`,
    {
      credentials: 'include',
      headers: {
        Accept: 'text/event-stream, application/json',
        Authorization: `Bearer ${accessToken}`,
        ...(lastEventId ? { 'Last-Event-ID': lastEventId } : {}),
      },
      signal,
    },
  )

  if (response.status === 401) {
    throw new LiveStreamUnauthorizedError()
  }
  if (!response.ok) {
    throw new Error(`Live event stream failed with status ${response.status}.`)
  }
  if (!response.body) {
    throw new Error('The live event stream response had no body.')
  }

  onOpen()
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (!signal.aborted) {
    const { done, value } = await reader.read()
    if (done) break
    buffer = (buffer + decoder.decode(value, { stream: true }))
      .replaceAll('\r\n', '\n')
      .replaceAll('\r', '\n')
    if (buffer.length > 1_000_000) {
      throw new Error('The live event stream buffer exceeded its safe limit.')
    }
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      dispatchEventBlock(buffer.slice(0, boundary), onEvent)
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
  }
}

function dispatchEventBlock(
  block: string,
  onEvent: (event: OrganizationLiveEvent) => void,
) {
  const data = block
    .split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).replace(/^ /, ''))
    .join('\n')
  if (!data) return

  try {
    const event = JSON.parse(data) as Partial<OrganizationLiveEvent>
    if (
      typeof event.id === 'string' &&
      typeof event.organizationId === 'string' &&
      typeof event.type === 'string' &&
      typeof event.entityType === 'string' &&
      typeof event.entityId === 'string' &&
      typeof event.occurredAt === 'string'
    ) {
      onEvent(event as OrganizationLiveEvent)
    }
  } catch {
    // Malformed event hints are ignored; authoritative REST data remains safe.
  }
}
