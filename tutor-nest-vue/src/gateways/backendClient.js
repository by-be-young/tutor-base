const API_BASE_URL = (import.meta.env?.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')

let csrfToken = null

export class BackendGatewayError extends Error {
  constructor(problem, status) {
    super(problem?.detail || '后端服务请求失败')
    this.name = 'BackendGatewayError'
    this.code = problem?.code || 'backend_request_failed'
    this.status = status
  }
}

async function parseResponse(response) {
  if (response.status === 204) return null
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new BackendGatewayError(body, response.status)
  return body
}

async function getCsrfToken(force = false) {
  if (csrfToken && !force) return csrfToken
  // 加时间戳并禁用缓存：避免浏览器复用旧的 401 响应导致登录一直失败
  const response = await fetch(`${API_BASE_URL}/csrf?_=${Date.now()}`, {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
    headers: { Accept: 'application/json' }
  })
  const body = await parseResponse(response)
  csrfToken = body.token
  return csrfToken
}

async function request(path, options = {}, retryCsrf = true) {
  const method = options.method || 'GET'
  const headers = { Accept: 'application/json', ...options.headers }
  if (!['GET', 'HEAD'].includes(method)) {
    headers['X-CSRF-TOKEN'] = await getCsrfToken()
  }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    method,
    headers,
    credentials: 'include',
    cache: 'no-store',
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  })

  if (response.status === 403 && retryCsrf) {
    const problem = await response.clone().json().catch(() => null)
    if (['csrf_invalid', 'invalid_csrf'].includes(problem?.code)) {
      await getCsrfToken(true)
      return request(path, options, false)
    }
  }
  return parseResponse(response)
}

export const backendClient = {
  request,
  clearCsrfToken() {
    csrfToken = null
  }
}
