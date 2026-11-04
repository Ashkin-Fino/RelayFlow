const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
const baseUrl = configuredBaseUrl.replace(/\/+$/, '')
let apiToken = window.sessionStorage.getItem('relay.demoToken') ?? ''

export class ApiError extends Error {
  constructor(message, { status, code, details } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

export function setApiToken(token) {
  apiToken = token
}

export async function apiRequest(path, { body, headers, ...options } = {}) {
  const requestHeaders = new Headers(headers)
  requestHeaders.set('Accept', 'application/json')
  requestHeaders.set('Content-Type', 'application/json')

  if (apiToken) {
    requestHeaders.set('Authorization', `Bearer ${apiToken}`)
  }

  let response
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...options,
      headers: requestHeaders,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(`Could not connect to the RelayFlow API at ${baseUrl}.`, {
      code: 'network_error',
    })
  }

  const responseBody = await readResponseBody(response)
  if (!response.ok) {
    const apiError = responseBody?.error
    throw new ApiError(apiError?.message || `Request failed with status ${response.status}.`, {
      status: response.status,
      code: apiError?.code || 'request_failed',
      details: responseBody,
    })
  }

  return responseBody
}

async function readResponseBody(response) {
  if (response.status === 204) {
    return null
  }

  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }

  const text = await response.text()
  return text || null
}
