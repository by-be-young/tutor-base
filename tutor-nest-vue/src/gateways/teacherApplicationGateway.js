import { backendClient } from './backendClient.js'

export const teacherApplicationGateway = {
  list(status = 'pending') {
    const query = status && status !== 'all' ? `?status=${encodeURIComponent(status)}` : '?status=all'
    return backendClient.request(`/admin/teacher-applications${query}`)
  },

  approve(applicationId) {
    return backendClient.request(
      `/admin/teacher-applications/${encodeURIComponent(applicationId)}/approve`,
      { method: 'POST' }
    )
  },

  reject(applicationId, reason) {
    return backendClient.request(
      `/admin/teacher-applications/${encodeURIComponent(applicationId)}/reject`,
      { method: 'POST', body: { reason } }
    )
  }
}
