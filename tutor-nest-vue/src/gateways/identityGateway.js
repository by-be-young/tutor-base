const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')

let csrfToken = null

export class IdentityGatewayError extends Error {
  constructor(problem, status) {
    super(problem?.detail || '身份服务请求失败')
    this.name = 'IdentityGatewayError'
    this.code = problem?.code || 'identity_request_failed'
    this.status = status
  }
}

async function parseResponse(response) {
  if (response.status === 204) return null
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new IdentityGatewayError(body, response.status)
  return body
}

async function getCsrfToken(force = false) {
  if (csrfToken && !force) return csrfToken
  const response = await fetch(`${API_BASE_URL}/csrf`, {
    method: 'GET',
    credentials: 'include',
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
    body: options.body === undefined ? undefined : JSON.stringify(options.body)
  })

  if (response.status === 403 && retryCsrf) {
    const problem = await response.clone().json().catch(() => null)
    if (problem?.code === 'invalid_csrf') {
      await getCsrfToken(true)
      return request(path, options, false)
    }
  }
  return parseResponse(response)
}

export const identityGateway = {
  login(username, password) {
    return request('/sessions', { method: 'POST', body: { username, password } })
  },

  async getSession() {
    try {
      return await request('/session')
    } catch (error) {
      if (error instanceof IdentityGatewayError && error.status === 401) return null
      throw error
    }
  },

  async logout() {
    try {
      await request('/session', { method: 'DELETE' })
    } finally {
      csrfToken = null
    }
  },

  setLearnerPassword(learnerId, password) {
    return request(`/admin/learners/${encodeURIComponent(learnerId)}/password`, {
      method: 'PUT',
      body: { password }
    })
  }
}
