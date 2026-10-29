// src/gateways/coursewareGateway.js
// 课件目录同步与管理员认领：/admin/courseware-catalog、/admin/courseware-claims。
import { backendClient } from './backendClient'

function normalizeClaims(response) {
  return {
    subjectClaims: Array.isArray(response?.subjectClaims)
      ? response.subjectClaims.map(subject => String(subject))
      : [],
    articleClaims: Array.isArray(response?.articleClaims)
      ? response.articleClaims.map(Number).filter(Number.isFinite)
      : []
  }
}

function normalizeCatalog(response) {
  return {
    subjects: Array.isArray(response?.subjects) ? response.subjects : [],
    items: Array.isArray(response?.items) ? response.items : []
  }
}

export const coursewareGateway = {
  /** 同步前端静态课件目录；重复调用幂等。 */
  syncCatalog(items) {
    return backendClient.request('/admin/courseware-catalog/sync', {
      method: 'POST',
      body: { items }
    })
  },

  async getClaims() {
    const response = await backendClient.request('/admin/courseware-claims')
    return normalizeClaims(response)
  },

  async getCatalog(scope = 'mine') {
    const parameters = new URLSearchParams({ scope })
    const response = await backendClient.request(`/admin/courseware-claims/catalog?${parameters}`)
    return normalizeCatalog(response)
  },

  claimSubject(subject) {
    return backendClient.request(`/admin/courseware-claims/subjects/${encodeURIComponent(subject)}`, {
      method: 'PUT'
    })
  },

  releaseSubject(subject) {
    return backendClient.request(`/admin/courseware-claims/subjects/${encodeURIComponent(subject)}`, {
      method: 'DELETE'
    })
  },

  claimArticle(articleId) {
    return backendClient.request(`/admin/courseware-claims/articles/${encodeURIComponent(articleId)}`, {
      method: 'PUT'
    })
  },

  releaseArticle(articleId) {
    return backendClient.request(`/admin/courseware-claims/articles/${encodeURIComponent(articleId)}`, {
      method: 'DELETE'
    })
  }
}
