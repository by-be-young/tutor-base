import { backendClient } from './backendClient.js'

export const wrongBookGateway = {
  list: () => backendClient.request('/wrong-book'),
  collect(sourceArticleId, sourceQuestionId, myAnswer) {
    return backendClient.request('/wrong-book/entries', {
      method: 'POST', body: { sourceArticleId, sourceQuestionId, myAnswer }
    })
  },
  update(entryId, patch) {
    return backendClient.request(`/wrong-book/entries/${encodeURIComponent(entryId)}`, {
      method: 'PATCH', body: patch
    })
  },
  remove(entryId) {
    return backendClient.request(`/wrong-book/entries/${encodeURIComponent(entryId)}`, { method: 'DELETE' })
  }
}
