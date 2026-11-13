// src/utils/coursewareManifest.js
// 课件清单的展平与分组规则（纯函数，供 store 与单测共用）。
// 学员走 /courseware/manifests，管理员走 /admin/courseware/articles（托管库）。

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
 * 把清单展平成列表。
 *
 * 运行时仍以全局数值 `id` 识别课件（权限、错题、学习记录都用它），因此优先沿用
 * `legacyArticleId`；同一老师命名空间内才保证唯一，跨老师撞号时退回 `articleId`。
 * 日期与路径都来自发布清单本身，不再读取静态目录。
 */
export function flattenManifests(manifests) {
  const usedIds = new Set()
  const articles = []
  for (const manifest of manifests || []) {
    for (const article of manifest?.articles || []) {
      const legacyId = article.legacyArticleId ?? article.articleId
      let id = Number(legacyId)
      if (usedIds.has(id)) id = Number(article.articleId)
      usedIds.add(id)
      articles.push({
        id,
        title: article.title,
        series: article.series || manifest.subject,
        date: String(manifest.publishedAt || '').slice(0, 10),
        path: article.logicalPath,
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

/**
 * 管理员的课件库（`GET /admin/courseware/articles`）→ 与清单同构的列表。
 *
 * 管理员不是学员，`/courseware/manifests` 对其为空；权限、答案与批阅面板仍需要
 * 一份「我的托管课件」清单。只取已发布篇目：草稿学员看不到，不应出现在授权范围里。
 *
 * @param {Array<object>} list 接口返回的课件摘要
 * @param {{accountId: number, username: string}} account 当前管理员账号
 */
export function flattenHostedArticles(list, account) {
  const usedIds = new Set()
  const articles = []
  for (const item of list || []) {
    if (!item || item.published === false || item.hosted === false) continue
    const legacyId = item.legacyArticleId ?? item.articleId
    let id = Number(legacyId)
    if (usedIds.has(id)) id = Number(item.articleId)
    if (!Number.isFinite(id)) continue
    usedIds.add(id)
    articles.push({
      id,
      title: item.title || `课件 ${item.articleId}`,
      series: item.subject || '未归类',
      date: String(item.updatedAt || '').slice(0, 10),
      path: item.logicalPath || '',
      teacherId: account?.accountId ?? null,
      teacherName: account?.username || '',
      teacherArticleId: item.articleId
    })
  }
  return articles
}
