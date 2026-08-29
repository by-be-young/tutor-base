import { backendClient, BackendGatewayError } from './backendClient.js'

export { BackendGatewayError as IdentityGatewayError } from './backendClient.js'

export const identityGateway = {
  async login(username, password) {
    const session = await backendClient.request('/sessions', {
      method: 'POST',
      body: { username, password }
    })
    // 登录会轮换会话 Cookie，旧 CSRF token 与新 Cookie 不再匹配。
    backendClient.clearCsrfToken()
    return session
  },

  async getSession() {
    try {
      return await backendClient.request('/session')
    } catch (error) {
      if (error instanceof BackendGatewayError && error.status === 401) return null
      throw error
    }
  },

  async register(username, password) {
    const session = await backendClient.request('/accounts', {
      method: 'POST',
      body: { username, password }
    })
    // 注册会直接建立会话并轮换 Cookie，旧 CSRF token 与新 Cookie 不再匹配。
    backendClient.clearCsrfToken()
    return session
  },

  async changePassword(currentPassword, newPassword) {
    return backendClient.request('/password', {
      method: 'PUT',
      body: { currentPassword, newPassword }
    })
  },

  async logout() {
    try {
      await backendClient.request('/session', { method: 'DELETE' })
    } finally {
      backendClient.clearCsrfToken()
    }
  },

  setLearnerPassword(learnerId, password) {
    return backendClient.request(`/admin/learners/${encodeURIComponent(learnerId)}/password`, {
      method: 'PUT',
      body: { password }
    })
  },

  async impersonateLearner(learnerId) {
    const session = await backendClient.request(`/admin/learners/${encodeURIComponent(learnerId)}/impersonate`, {
      method: 'POST'
    })
    // 进入学生账号会轮换会话 Cookie，旧 CSRF token 与新 Cookie 不再匹配。
    backendClient.clearCsrfToken()
    return session
  }
}
