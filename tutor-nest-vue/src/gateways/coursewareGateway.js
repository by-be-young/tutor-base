// src/gateways/coursewareGateway.js
// 课件目录同步与管理员认领：/admin/courseware-catalog、/admin/courseware-claims。
import { backendClient } from './backendClient.js'

function normalizeClaims(response) {
  return {
    subjectClaims: Array.isArray(response?.subjectClaims)
      ? response.subjectClaims.map(subject => String(subject))
      : [],
    articleClaims: Array.isArray(response?.articleClaims)
      ? response.articleClaims.map(Number).filter(Number.isFinite)
      : [],
    // 学科认领范围内被单篇释放的排除项；旧后端缺失时按空数组处理。
    excludedArticleIds: Array.isArray(response?.excludedArticleIds)
      ? response.excludedArticleIds.map(Number).filter(Number.isFinite)
      : [],
    // 管理员主动放弃的课件；放弃后不再计入其“待认领”。
    abandonedArticleIds: Array.isArray(response?.abandonedArticleIds)
      ? response.abandonedArticleIds.map(Number).filter(Number.isFinite)
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
  /** 当前学习者可见的课件清单（老师 → 科目 → 版本）。 */
  async fetchManifests() {
    const response = await backendClient.request('/courseware/manifests')
    return Array.isArray(response?.items) ? response.items : []
  },

  /** 取某一老师命名空间下的一篇正文（markdown）。 */
  fetchArticleContent(teacherId, articleId) {
    return backendClient.requestText(
      `/courseware/articles/${encodeURIComponent(teacherId)}/${encodeURIComponent(articleId)}/content`
    )
  },

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
  },

  abandonArticle(articleId) {
    return backendClient.request(`/admin/courseware-claims/articles/${encodeURIComponent(articleId)}/abandonment`, {
      method: 'POST'
    })
  }
}
