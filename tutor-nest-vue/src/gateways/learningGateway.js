import { backendClient } from './backendClient.js'

const segment = value => encodeURIComponent(value)

export const learningGateway = {
  getLearnerState(articleId) {
    return backendClient.request(`/articles/${segment(articleId)}/study-state`)
  },
  submit(articleId, questionId, answerText) {
    return backendClient.request(`/articles/${segment(articleId)}/submissions/${segment(questionId)}`, {
      method: 'PUT', body: { answerText }
    })
  },
  getAnswerKeys(articleId) {
    return backendClient.request(`/admin/articles/${segment(articleId)}/answer-keys`)
  },
  getAnswerKeyArticleIds() {
    return backendClient.request('/admin/articles/answer-key-article-ids')
  },
  getLearnerProgress() {
    return backendClient.request('/articles/study-progress')
  },
  getAdministratorProgress(learnerId) {
    return backendClient.request(`/admin/learners/${segment(learnerId)}/study-progress`)
  },
  replaceAnswerKeys(articleId, items) {
    return backendClient.request(`/admin/articles/${segment(articleId)}/answer-keys`, {
      method: 'PUT', body: { items }
    })
  },
  getAdministratorState(articleId, learnerId) {
    return backendClient.request(`/admin/articles/${segment(articleId)}/study-state?learnerId=${segment(learnerId)}`)
  },
  review(articleId, learnerId, questionId, result) {
    return backendClient.request(
      `/admin/articles/${segment(articleId)}/learners/${segment(learnerId)}/submissions/${segment(questionId)}/review`,
      { method: 'PUT', body: { result } }
    )
  }
}
