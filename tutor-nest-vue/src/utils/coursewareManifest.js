// src/utils/coursewareManifest.js
// /courseware/manifests 与静态目录索引的合流规则（纯函数，供 store 与单测共用）。

/**
 * 按老师分组：多老师场景下首页/目录需要区分「谁的课件」。
 *
 * 输入是 `flattenManifests` 的结果，输出保持老师出现顺序（后端已按用户名排序）。
 */
export function groupByTeacher(articles) {
  const groups = new Map()
  for (const article of articles || []) {
    const key = article.teacherId == null ? 'self' : String(article.teacherId)
    if (!groups.has(key)) {
      groups.set(key, {
        teacherId: article.teacherId ?? null,
        teacherName: article.teacherName || '',
        subjects: [],
        articles: []
      })
    }
    const group = groups.get(key)
    group.articles.push(article)
    if (article.series && !group.subjects.includes(article.series)) {
      group.subjects.push(article.series)
    }
  }
  return [...groups.values()]
}

/**
 * 把清单展平成与 `data/articles.json` 同构的列表。
 *
 * 运行时仍以全局数值 `id` 识别课件（权限、错题、学习记录都用它），因此优先沿用
 * `legacyArticleId`；同一老师命名空间内才保证唯一，跨老师撞号时退回 `articleId`。
 * 静态索引只用来补 `date` 与图片相对路径（`path`），不作为课件归属依据。
 */
export function flattenManifests(manifests, staticIndex = []) {
  const staticById = new Map((staticIndex || []).map(item => [Number(item?.id), item]))
  const usedIds = new Set()
  const articles = []
  for (const manifest of manifests || []) {
    for (const article of manifest?.articles || []) {
      const legacyId = article.legacyArticleId ?? article.articleId
      const known = staticById.get(Number(legacyId))
      let id = Number(legacyId)
      if (usedIds.has(id)) id = Number(article.articleId)
      usedIds.add(id)
      articles.push({
        id,
        title: article.title,
        series: article.series || manifest.subject,
        date: known?.date || String(manifest.publishedAt || '').slice(0, 10),
        path: known?.path || article.logicalPath,
        teacherId: manifest.teacherId,
        teacherName: manifest.teacherName,
        teacherArticleId: article.articleId,
        contentHash: article.contentHash,
        releaseVersion: manifest.version
      })
    }
  }
  return articles
}
