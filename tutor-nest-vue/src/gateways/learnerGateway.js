import { backendClient } from './backendClient'

function adminLearnersPath(cursor) {
  const parameters = new URLSearchParams({ limit: '100' })
  if (cursor) parameters.set('cursor', cursor)
  return `/admin/learners?${parameters}`
}

export const learnerGateway = {
  async getCurrentContentGrants() {
    const response = await backendClient.request('/me/content-grants')
    return {
      articleIds: Array.isArray(response.articleIds) ? response.articleIds : [],
      allArticles: response.allArticles === true
    }
  },

  async listAllLearners() {
    const learners = []
    let cursor = null
    do {
      const page = await backendClient.request(adminLearnersPath(cursor))
      learners.push(...(page.items || []))
      cursor = page.nextCursor || null
    } while (cursor)
    return learners
  },

  /** 学员详情：积分、签到、分学科进度、最近作答与近 30 天动态。 */
  getLearnerProfile(learnerId) {
    return backendClient.request(`/admin/learners/${encodeURIComponent(learnerId)}/profile`)
  },

  createLearner(username) {
    return backendClient.request('/admin/learners', {
      method: 'POST',
      body: { username }
    })
  },

  replaceContentGrants(learnerId, articleIds) {
    return backendClient.request(`/admin/learners/${encodeURIComponent(learnerId)}/content-grants`, {
      method: 'PUT',
      body: { articleIds }
    })
  },

  claimLearner(learnerId) {
    return backendClient.request(`/admin/learners/${encodeURIComponent(learnerId)}/claim`, {
      method: 'PUT'
    })
  },

  releaseLearnerClaim(learnerId) {
    return backendClient.request(`/admin/learners/${encodeURIComponent(learnerId)}/claim`, {
      method: 'DELETE'
    })
  },

  deleteLearner(learnerId) {
    return backendClient.request(`/admin/learners/${encodeURIComponent(learnerId)}`, {
      method: 'DELETE'
    })
  }
}
