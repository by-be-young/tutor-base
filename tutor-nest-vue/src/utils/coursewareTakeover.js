// src/utils/coursewareTakeover.js
// 站内静态课件「接管」：public/articles 下的讲义从未走过发布，正文还在 git 里。
// 接管 = 按原 articleId（= 旧静态编号，学员授权用的就是它）写成草稿，再按科目整科发布。
// 这样学员端按原编号看到托管后的正文，不需要重新授权，也不会多出一篇。
//
// 不选「导入 zip」：导入会重新分配 article_id，学员的 student.permissions 匹配不到。

/** 需要接管的目标：仍是站内静态课件，且还没有草稿。 */
export function pendingStaticArticles(articles = []) {
  return (articles || []).filter(item => item && item.hosted === false && !item.hasDraft)
}

/** 按科目统计待接管篇数，供确认框展示。 */
export function takeoverBySubject(articles = []) {
  const counts = new Map()
  for (const item of articles) {
    const subject = item.subject || '未归类'
    counts.set(subject, (counts.get(subject) || 0) + 1)
  }
  return [...counts.entries()].map(([subject, count]) => ({ subject, count }))
}

/**
 * 逐篇写草稿 → 逐科目发布。
 *
 * @param {Array<object>} articles 待接管的清单条目
 * @param {{loadMarkdown: Function, saveDraft: Function, publish: Function, onProgress?: Function}} handlers
 * @returns {Promise<{total: number, saved: Array, skipped: Array,
 *   failed: Array<{article: object, message: string}>, published: Array,
 *   errors: Array<{subject: string, message: string}>}>}
 */
export async function takeOverStaticCourseware(articles, handlers = {}) {
  const { loadMarkdown, saveDraft, publish, onProgress } = handlers
  const targets = pendingStaticArticles(articles)
  const result = {
    total: targets.length,
    saved: [],
    skipped: [],
    failed: [],
    published: [],
    errors: []
  }
  if (!targets.length) return result

  for (let index = 0; index < targets.length; index++) {
    const article = targets[index]
    if (typeof onProgress === 'function') {
      onProgress({ done: index, total: targets.length, article })
    }
    try {
      const markdown = await loadMarkdown(article)
      if (!markdown) {
        result.skipped.push(article)
        continue
      }
      const legacyArticleId = article.legacyArticleId == null ? article.articleId : article.legacyArticleId
      await saveDraft(article.articleId, markdown, legacyArticleId)
      result.saved.push(article)
    } catch (error) {
      result.failed.push({ article, message: error?.message || String(error) })
    }
  }
  if (typeof onProgress === 'function') {
    onProgress({ done: targets.length, total: targets.length, article: null })
  }

  const subjects = [...new Set(result.saved.map(item => item.subject).filter(Boolean))]
  for (const subject of subjects) {
    try {
      const release = await publish(subject)
      result.published.push({ subject, release })
    } catch (error) {
      result.errors.push({ subject, message: error?.message || String(error) })
    }
  }
  return result
}
