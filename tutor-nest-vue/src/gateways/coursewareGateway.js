// src/gateways/coursewareGateway.js
// 课件目录同步与管理员认领：/admin/courseware-catalog、/admin/courseware-claims。
import { backendClient } from './backendClient.js'
import {
  normalizeCopyResult,
  normalizeCopySummary,
  normalizeNotification,
  normalizeSharedArticle,
  normalizeSharedSubject,
  normalizeShares
} from '../utils/coursewareSharing.js'

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

  /** 分享整科或指定单篇。 */
  async createShare({ subject, scope, articleIds, allowPreview = true, allowCopy = true }) {
    const body = { subject, scope, allowPreview, allowCopy }
    if (Array.isArray(articleIds) && articleIds.length) body.articleIds = articleIds
    return normalizeShares(await backendClient.request('/admin/courseware/shares', { method: 'POST', body }))
  },

  async listShares(includeRevoked = false) {
    const parameters = new URLSearchParams({ includeRevoked: String(Boolean(includeRevoked)) })
    return normalizeShares(await backendClient.request(`/admin/courseware/shares?${parameters}`))
  },

  revokeShare(shareId) {
    return backendClient.request(`/admin/courseware/shares/${encodeURIComponent(shareId)}`, { method: 'DELETE' })
  },

  async listResources() {
    const response = await backendClient.request('/admin/courseware/resources')
    return Array.isArray(response) ? response.map(normalizeSharedSubject) : []
  },

  async listResourceTree(teacherId, subject) {
    const parameters = new URLSearchParams({ teacherId: String(teacherId), subject })
    const response = await backendClient.request(`/admin/courseware/resources/tree?${parameters}`)
    return Array.isArray(response) ? response.map(normalizeSharedArticle) : []
  },

  /** 获取课件：`articleIds` 为空表示整科。 */
  async acquire({ sourceTeacherId, subject, articleIds, targetRootPath }) {
    const body = { sourceTeacherId, subject }
    if (Array.isArray(articleIds) && articleIds.length) body.articleIds = articleIds
    if (targetRootPath) body.targetRootPath = targetRootPath
    const response = await backendClient.request('/admin/courseware/copies', { method: 'POST', body })
    return Array.isArray(response) ? response.map(normalizeCopyResult) : []
  },

  /** 「我的课件」概览：科目、版本、草稿与待处理数量。 */
  async getOverview() {
    const response = await backendClient.request('/admin/courseware/overview')
    return {
      subjects: Array.isArray(response?.subjects) ? response.subjects : [],
      pendingDrafts: Number(response?.pendingDrafts) || 0,
      acquiredCount: Number(response?.acquiredCount) || 0,
      pendingUpdates: Number(response?.pendingUpdates) || 0,
      unreadNotifications: Number(response?.unreadNotifications) || 0
    }
  },

  /** 我的存储用量与配额水位（GET /admin/courseware/maintenance/usage）。 */
  async getUsage() {
    const response = await backendClient.request('/admin/courseware/maintenance/usage')
    const quotaBytes = Number(response?.quotaBytes) || 0
    const totalBytes = Number(response?.totalBytes) || 0
    return {
      blobCount: Number(response?.blobCount) || 0,
      totalBytes,
      orphanBytes: Number(response?.orphanBytes) || 0,
      orphanCount: Number(response?.orphanCount) || 0,
      quotaBytes,
      overQuota: Boolean(response?.overQuota),
      usageRatio: Number(response?.usageRatio) || 0,
      level: ['ok', 'warn', 'critical'].includes(response?.level) ? response.level : 'ok'
    }
  },

  /** 模板库（新建课件时的骨架来源）。 */
  async listTemplates(subject) {
    const query = subject ? `?${new URLSearchParams({ subject })}` : ''
    const response = await backendClient.request(`/admin/courseware/templates${query}`)
    return Array.isArray(response) ? response : []
  },

  async getTemplate(templateId) {
    const response = await backendClient.request(
      `/admin/courseware/templates/${encodeURIComponent(templateId)}`
    )
    return String(response?.markdown || '')
  },

  saveTemplate({ subject, kind, name, markdown }) {
    return backendClient.request('/admin/courseware/templates', {
      method: 'POST',
      body: { subject, kind, name, markdown }
    })
  },

  /** 布局注册表（编辑器与渲染共用）。 */
  async listLayouts() {
    const response = await backendClient.request('/admin/courseware/layouts')
    return Array.isArray(response) ? response : []
  },

  async getLayoutSkeleton(layoutId, title) {
    const parameters = new URLSearchParams({ title })
    const response = await backendClient.request(
      `/admin/courseware/layouts/${encodeURIComponent(layoutId)}/skeleton?${parameters}`
    )
    return String(response?.markdown || '')
  },

  async listDrafts() {
    const response = await backendClient.request('/admin/courseware/drafts')
    return Array.isArray(response) ? response : []
  },

  async getDraft(articleId) {
    return backendClient.request(`/admin/courseware/drafts/${encodeURIComponent(articleId)}`)
  },

  async saveDraft(articleId, markdown, legacyArticleId) {
    const body = legacyArticleId == null ? { markdown } : { markdown, legacyArticleId }
    if (articleId == null) {
      return backendClient.request('/admin/courseware/drafts', { method: 'POST', body })
    }
    return backendClient.request(`/admin/courseware/drafts/${encodeURIComponent(articleId)}`, {
      method: 'PUT',
      body
    })
  },

  deleteDraft(articleId) {
    return backendClient.request(`/admin/courseware/drafts/${encodeURIComponent(articleId)}`, {
      method: 'DELETE'
    })
  },

  async precheckDraft(markdown) {
    const response = await backendClient.request('/admin/courseware/drafts/precheck', {
      method: 'POST',
      body: { markdown }
    })
    return { ok: Boolean(response?.ok), errors: Array.isArray(response?.errors) ? response.errors : [] }
  },

  publishDrafts(subject, expectVersion) {
    const body = { subject }
    if (expectVersion != null) body.expectVersion = expectVersion
    return backendClient.request('/admin/courseware/drafts/publish', { method: 'POST', body })
  },

  /** 导出 zip：返回 blob 供前端触发下载。 */
  exportZip({ subject, articleIds } = {}) {
    const parameters = new URLSearchParams()
    if (subject) parameters.set('subject', subject)
    for (const id of articleIds || []) parameters.append('articleIds', String(id))
    const query = parameters.toString() ? `?${parameters}` : ''
    return backendClient.requestBlob(`/admin/courseware/export${query}`)
  },

  /** 导入 zip（multipart，结果先落草稿，由老师确认后发布）。 */
  importZip(file, overwrite = false) {
    const form = new FormData()
    form.append('file', file)
    return backendClient.requestForm(
      `/admin/courseware/import?overwrite=${overwrite ? 'true' : 'false'}`,
      form
    )
  },


  async listCopies() {
    const response = await backendClient.request('/admin/courseware/copies')
    return Array.isArray(response) ? response.map(normalizeCopySummary) : []
  },

  /** 预览来源正文（只读，需 allow_preview）。 */
  previewArticle(teacherId, articleId) {
    return backendClient.requestText(
      `/admin/courseware/resources/articles/${encodeURIComponent(teacherId)}/${encodeURIComponent(articleId)}/preview`
    )
  },

  /** 改标题 / 改逻辑路径。 */
  async renameCopy(copyId, { title, logicalPath }) {
    const body = {}
    if (title) body.title = title
    if (logicalPath) body.logicalPath = logicalPath
    return normalizeCopySummary(
      await backendClient.request(`/admin/courseware/copies/${encodeURIComponent(copyId)}`, {
        method: 'PATCH',
        body
      })
    )
  },

  async listNotifications(unreadOnly = false) {
    const parameters = new URLSearchParams({ unreadOnly: String(Boolean(unreadOnly)) })
    const response = await backendClient.request(`/admin/courseware/notifications?${parameters}`)
    return {
      items: Array.isArray(response?.items) ? response.items.map(normalizeNotification).filter(Boolean) : [],
      unreadCount: Number(response?.unreadCount) || 0
    }
  },

  async markNotificationsRead(ids) {
    const body = Array.isArray(ids) && ids.length ? { ids } : {}
    const response = await backendClient.request('/admin/courseware/notifications/read', {
      method: 'POST',
      body
    })
    return { updated: Number(response?.updated) || 0, unreadCount: Number(response?.unreadCount) || 0 }
  },

  async getUpdates() {
    const response = await backendClient.request('/admin/courseware/updates')
    return {
      updatedCount: Number(response?.updatedCount) || 0,
      addedCount: Number(response?.addedCount) || 0,
      removedCount: Number(response?.removedCount) || 0,
      updated: Array.isArray(response?.updated) ? response.updated : [],
      added: Array.isArray(response?.added) ? response.added : [],
      removed: Array.isArray(response?.removed) ? response.removed : []
    }
  },

  async getUpdateDetail(copyId) {
    return backendClient.request(`/admin/courseware/copies/${encodeURIComponent(copyId)}/updates`)
  },

  applyUpdate(copyId, { mode, expectedSourceHash }) {
    const body = { mode }
    if (expectedSourceHash) body.expectedSourceHash = expectedSourceHash
    return backendClient.request(`/admin/courseware/copies/${encodeURIComponent(copyId)}/updates/apply`, {
      method: 'POST',
      body
    })
  },

  dismissUpdate(copyId) {
    return backendClient.request(`/admin/courseware/copies/${encodeURIComponent(copyId)}/updates/dismiss`, {
      method: 'POST'
    })
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
