import { backendClient } from './backendClient.js'

const segment = value => encodeURIComponent(value)

export const growthGateway = {
  getAchievements() {
    return backendClient.request('/achievements')
  },
  claimAchievement(key) {
    return backendClient.request(`/achievements/${segment(key)}/claims`, { method: 'POST' })
  },
  getTaskBoard() {
    return backendClient.request('/tasks/board')
  },
  acceptTask(key) {
    return backendClient.request(`/tasks/${segment(key)}/accept`, { method: 'POST' })
  },
  claimTask(key) {
    return backendClient.request(`/tasks/${segment(key)}/claims`, { method: 'POST' })
  }
}
