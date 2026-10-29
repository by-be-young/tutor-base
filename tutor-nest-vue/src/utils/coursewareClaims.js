// src/utils/coursewareClaims.js
// 课件认领的纯函数工具：静态目录 → 同步载荷、认领范围计算、目录分组与错误映射。
// 不依赖 Vue / Pinia / 浏览器，便于 node:test 直接覆盖。

function toPositiveId(value) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

/** 把静态文章目录映射为后端 sync 载荷：{ articleId, subject, title }，跳过缺 id/学科的条目。 */
export function catalogSyncItems(articles = []) {
  const seen = new Set()
  const items = []
  for (const article of articles || []) {
    const articleId = toPositiveId(article?.id)
    const subject = String(article?.series ?? '').trim()
    if (articleId === null || !subject || seen.has(articleId)) continue
    seen.add(articleId)
    const title = String(article?.title ?? '').trim().slice(0, 300)
    items.push({ articleId, subject, title: title || null })
  }
  return items
}

/** 同步目录的签名：articleId:subject 排序后拼接；用于判断是否需要再次调用 sync。 */
export function catalogSignature(items = []) {
  return (items || [])
    .map(item => `${item.articleId}:${item.subject}`)
    .sort()
    .join('|')
}

/** 课件是否被「学科覆盖但单篇释放」排除。 */
export function isExcludedArticle(articleId, excludedArticleIds = []) {
  const id = toPositiveId(articleId)
  if (id === null) return false
  return (excludedArticleIds || []).some(value => toPositiveId(value) === id)
}

/**
 * 我的课件 ID 集合：
 * 学科认领覆盖的课件（剔除单篇释放产生的排除项）∪ 我单篇认领的课件。
 * 单篇认领优先于排除：再次单篇认领会恢复该课件。
 */
export function computeVisibleArticleIds(
  catalogItems = [],
  subjectClaims = [],
  articleClaims = [],
  excludedArticleIds = []
) {
  const claimedSubjects = new Set((subjectClaims || []).map(subject => String(subject)))
  const claimedArticleIds = new Set(
    (articleClaims || []).map(toPositiveId).filter(id => id !== null)
  )
  const excludedIds = new Set(
    (excludedArticleIds || []).map(toPositiveId).filter(id => id !== null)
  )
  const visible = new Set()
  for (const item of catalogItems || []) {
    const articleId = toPositiveId(item?.articleId)
    if (articleId === null) continue
    const coveredBySubject = claimedSubjects.has(String(item?.subject ?? ''))
    if (claimedArticleIds.has(articleId) || (coveredBySubject && !excludedIds.has(articleId))) {
      visible.add(articleId)
    }
  }
  return visible
}

/** 可见学科：目录里 mine=true 的学科，或至少包含一篇可见课件的学科。 */
export function computeVisibleSubjects(catalogSubjects = [], catalogItems = [], visibleArticleIds = new Set()) {
  const visibleIds = visibleArticleIds instanceof Set
    ? visibleArticleIds
    : new Set((visibleArticleIds || []).map(toPositiveId).filter(id => id !== null))
  const subjectsWithVisible = new Set()
  for (const item of catalogItems || []) {
    if (visibleIds.has(toPositiveId(item?.articleId))) subjectsWithVisible.add(String(item?.subject ?? ''))
  }

  const subjects = []
  for (const entry of catalogSubjects || []) {
    const name = typeof entry === 'string' ? entry : String(entry?.subject ?? '')
    if (!name) continue
    const mine = typeof entry === 'string' ? false : Boolean(entry?.mine)
    if (!mine && !subjectsWithVisible.has(name)) continue
    if (!subjects.includes(name)) subjects.push(name)
  }
  return subjects
}

/** 待认领课件数：目录中 claimedBy 为空的条目数。 */
export function computeUnclaimedCount(catalogItems = []) {
  return (catalogItems || []).filter(item => {
    const claimedBy = item?.claimedBy
    return !Array.isArray(claimedBy) || claimedBy.length === 0
  }).length
}

/** 按学科分组目录条目，保持后端返回的学科/articleId 排序。 */
export function computeCatalogBySubject(catalogItems = []) {
  const grouped = new Map()
  for (const item of catalogItems || []) {
    const subject = String(item?.subject ?? '')
    if (!subject) continue
    if (!grouped.has(subject)) grouped.set(subject, [])
    grouped.get(subject).push(item)
  }
  return grouped
}

/** 认领/释放失败的稳定中文提示；403 courseware_not_claimed 单独映射。 */
export function coursewareActionError(error, action = '操作') {
  const code = error?.code || ''
  const status = error?.status
  let message = `${action}失败，请稍后重试`

  if (code === 'courseware_not_claimed') {
    message = '该课件不在你的认领范围内'
  } else if (code === 'resource_not_found' || status === 404) {
    message = '课件目录中找不到该学科或课件，请刷新后重试'
  } else if (status === 401) {
    message = '登录状态已失效，请重新登录后再试'
  } else if (status === 403) {
    message = '当前账户无权执行该操作'
  } else if (status === 400) {
    message = `${action}失败：请求参数有误`
  } else if (error?.message) {
    message = `${action}失败：${error.message}`
  }

  const wrapped = new Error(message)
  wrapped.code = code || 'courseware_action_failed'
  wrapped.status = status
  return wrapped
}
