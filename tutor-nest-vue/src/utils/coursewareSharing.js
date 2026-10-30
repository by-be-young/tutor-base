// src/utils/coursewareSharing.js
// 分享 / 资源目录 / 获取 的响应规整（纯函数，供 gateway 与单测共用）。

function toId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

export function normalizeShare(row) {
  const shareId = toId(row?.shareId)
  if (!shareId) return null
  return {
    shareId,
    scope: row?.scope === 'article' ? 'article' : 'subject',
    subject: String(row?.subject || ''),
    articleId: toId(row?.articleId),
    allowPreview: Boolean(row?.allowPreview),
    allowCopy: Boolean(row?.allowCopy),
    revoked: Boolean(row?.revoked)
  }
}

export function normalizeShares(list) {
  return (Array.isArray(list) ? list : []).map(normalizeShare).filter(Boolean)
}

export function normalizeSharedSubject(row) {
  const teacherId = toId(row?.teacherId)
  if (!teacherId) return null
  return {
    teacherId,
    teacherName: String(row?.teacherName || ''),
    subject: String(row?.subject || ''),
    wholeSubject: Boolean(row?.wholeSubject),
    articleCount: Number(row?.articleCount) || 0,
    allowCopy: Boolean(row?.allowCopy)
  }
}

export function normalizeSharedArticle(row) {
  const articleId = toId(row?.articleId)
  if (!articleId) return null
  return {
    articleId,
    legacyArticleId: toId(row?.legacyArticleId),
    title: String(row?.title || ''),
    series: String(row?.series || ''),
    logicalPath: String(row?.logicalPath || ''),
    contentHash: String(row?.contentHash || ''),
    questionCount: Number(row?.questionCount) || 0,
    allowCopy: Boolean(row?.allowCopy)
  }
}

export function normalizeCopyResult(row) {
  const copyId = toId(row?.copyId)
  const localArticleId = toId(row?.localArticleId)
  if (!copyId || !localArticleId) return null
  return {
    copyId,
    localArticleId,
    title: String(row?.title || ''),
    sourceVersion: Number(row?.sourceVersion) || 0,
    alreadyExists: Boolean(row?.alreadyExists)
  }
}

export function normalizeCopySummary(row) {
  const copyId = toId(row?.copyId)
  if (!copyId) return null
  return {
    copyId,
    localArticleId: toId(row?.localArticleId),
    title: String(row?.title || ''),
    sourceTeacherId: toId(row?.sourceTeacherId),
    sourceTeacherName: String(row?.sourceTeacherName || ''),
    sourceArticleId: toId(row?.sourceArticleId),
    sourceLogicalPath: String(row?.sourceLogicalPath || ''),
    localLogicalPath: String(row?.localLogicalPath || ''),
    baseContentHash: String(row?.baseContentHash || ''),
    sourceContentHash: row?.sourceContentHash == null ? null : String(row.sourceContentHash),
    status: String(row?.status || 'active'),
    hasUpdate: Boolean(row?.hasUpdate)
  }
}
