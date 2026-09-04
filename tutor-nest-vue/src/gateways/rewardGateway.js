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
  }
}
