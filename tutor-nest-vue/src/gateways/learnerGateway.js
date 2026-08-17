import { backendClient } from './backendClient'

function adminLearnersPath(cursor) {
  const parameters = new URLSearchParams({ limit: '100' })
  if (cursor) parameters.set('cursor', cursor)
  return `/admin/learners?${parameters}`
}

export const learnerGateway = {
  async getCurrentContentGrants() {
    const response = await backendClient.request('/me/content-grants')
    return Array.isArray(response.articleIds) ? response.articleIds : []
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
  }
}
