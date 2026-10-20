import { backendClient } from './backendClient.js'

export const rewardGateway = {
  getSummary() {
    return backendClient.request('/rewards')
  },

  claimMilestone(milestonePoints) {
    return backendClient.request(
      `/rewards/milestones/${encodeURIComponent(milestonePoints)}/claims`,
      { method: 'POST' }
    )
  },
  draw(count = 1) {
    return backendClient.request('/rewards/draws', {
      method: 'POST',
      body: { count }
    })
  }
}
