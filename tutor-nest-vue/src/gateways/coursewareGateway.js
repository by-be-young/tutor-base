// src/gateways/coursewareGateway.js
// 课件发布、编辑、共享与「我的课件」：全部数据来自后端数据库。
import { backendClient } from './backendClient.js'
import {
  normalizeCopyResult,
  normalizeCopySummary,
  normalizeNotification,
  normalizeSharedArticle,
  normalizeSharedSubject,
  normalizeShares
} from '../utils/coursewareSharing.js'

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

  /** 「我的课件」概览：科目、版本与待处理数量。 */
  async getOverview() {
    const response = await backendClient.request('/admin/courseware/overview')
    return {
      subjects: Array.isArray(response?.subjects) ? response.subjects : [],
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

  /** 「我的课件」全量清单（只含当前发布版本），课件页目录与预览共用。 */
  async listArticles() {
    const response = await backendClient.request('/admin/courseware/articles')
    return Array.isArray(response) ? response : []
  },

  /** 读取当前发布版本的一篇课件（编辑页唯一入口）。 */
  getArticle(articleId) {
    return backendClient.request(`/admin/courseware/articles/${encodeURIComponent(articleId)}`)
  },

  /** 保存并立即更新线上版本（不走草稿）。 */
  saveArticle(articleId, markdown) {
    return backendClient.request(`/admin/courseware/articles/${encodeURIComponent(articleId)}`, {
      method: 'PUT',
      body: { markdown }
    })
  },

  /** 从课件页新建一篇并立即发布（不走草稿）。 */
  createArticle({ subject, title, logicalPath, markdown }) {
    return backendClient.request('/admin/courseware/articles', {
      method: 'POST',
      body: { subject, title, logicalPath, markdown }
    })
  },

  /** 我登记过的空目录（`学科/子目录`），用于让没有课件的目录也出现在树里。 */
  async listFolders() {
    const response = await backendClient.request('/admin/courseware/folders')
    return Array.isArray(response) ? response : []
  },

  /** 新建目录（父目录会一并登记）。返回本次新登记的路径列表。 */
  async createFolder(path) {
    const response = await backendClient.request('/admin/courseware/folders', {
      method: 'POST',
      body: { path }
    })
    return Array.isArray(response?.created) ? response.created : []
  },

  /**
   * 删除一篇课件：服务端会把它从当前发布版本剔除、并清掉草稿/答案/共享/副本与学员授权。
   * 不可撤销，调用前必须先让用户确认。
   */
  deleteArticle(articleId) {
    return backendClient.request(`/admin/courseware/articles/${encodeURIComponent(articleId)}`, {
      method: 'DELETE'
    })
  },

  /** 图片文件名是否已在桶里；重名时返回 suggested（第一个可用后缀）。 */
  async checkMedia(fileName) {
    const response = await backendClient.request(
      `/admin/courseware/media/check?${new URLSearchParams({ fileName })}`
    )
    return {
      exists: Boolean(response?.exists),
      fileName: String(response?.fileName || fileName),
      suggested: String(response?.suggested || fileName),
      url: String(response?.url || '')
    }
  },

  /** 上传课件图片（multipart），成功返回桶内文件名与公开 URL。 */
  uploadMedia(file, fileName) {
    const form = new FormData()
    form.append('file', file)
    form.append('fileName', fileName)
    return backendClient.requestForm('/admin/courseware/media', form)
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

  /** 移动课件到新路径（重命名目录/文件名，保持 articleId 与内容不变）。 */
  moveArticle(articleId, newLogicalPath) {
    return backendClient.request(`/admin/courseware/articles/${encodeURIComponent(articleId)}/move`, {
      method: 'POST',
      body: { newLogicalPath }
    })
  }
}
