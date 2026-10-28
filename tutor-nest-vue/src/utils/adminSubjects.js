// src/utils/adminSubjects.js
// 管理员按「学科」给学员分配到课阶段：很多学员只上其中几门科目。
// 这里只放不依赖 Vue / Pinia / 浏览器的纯函数，便于 node:test 直接覆盖。

/** 学科 → 阶段：'inactive' 未开通、'partial' 部分开通、'active' 全科开通。 */
export function subjectStage(grantedCount, totalCount) {
  const total = Number(totalCount) || 0
  const granted = Number(grantedCount) || 0
  if (total <= 0 || granted <= 0) return 'inactive'
  return granted >= total ? 'active' : 'partial'
}

/**
 * 按学科汇总学员当前授权情况。
 * @param {{ subjects: string[], articles: Array<{ id: number|string, series?: string }>, permissions: Array<number|string> }} input
 * @returns {Array<{ subject: string, total: number, granted: number, stage: string }>}
 */
export function buildSubjectScope({ subjects = [], articles = [], permissions = [] } = {}) {
  const grantedIds = new Set((permissions || []).map(Number).filter(Number.isFinite))
  return (subjects || []).map(subject => {
    const subjectArticles = (articles || []).filter(article => article.series === subject)
    const granted = subjectArticles.filter(article => grantedIds.has(Number(article.id))).length
    return {
      subject,
      total: subjectArticles.length,
      granted,
      stage: subjectStage(granted, subjectArticles.length)
    }
  })
}

/** 在学（有任意已授权文章）学科列表，按传入顺序。 */
export function activeSubjects(scope) {
  return (scope || []).filter(item => item.stage !== 'inactive').map(item => item.subject)
}

/** 学员已开通的学科（至少有一篇该科文章已授权），供权限管理等页面收敛可见范围。 */
export function openedSubjects({ subjects = [], articles = [], permissions = [] } = {}) {
  const grantedIds = new Set((permissions || []).map(Number).filter(Number.isFinite))
  return (subjects || []).filter(subject => (articles || []).some(article =>
    article.series === subject && grantedIds.has(Number(article.id))))
}

/**
 * 勾选/取消勾选整个学科时，需要新增或移除的文章 ID。
 * 已授权时返回要移除的 ID（取消整科），未完全授权时返回还是要补上的 ID（开通整科）。
 */
export function subjectToggleIds({ articles = [], permissions = [], subject } = {}) {
  const grantedIds = new Set((permissions || []).map(Number).filter(Number.isFinite))
  const subjectArticles = (articles || []).filter(article => article.series === subject)
  const subjectIds = subjectArticles.map(article => Number(article.id)).filter(Number.isFinite)
  if (subjectIds.length === 0) return { ids: [], checked: false }
  const allGranted = subjectIds.every(id => grantedIds.has(id))
  if (allGranted) return { ids: subjectIds, checked: false }
  return { ids: subjectIds.filter(id => !grantedIds.has(id)), checked: true }
}

/** 阶段中文标签，用于按钮与提示。 */
export function stageLabel(stage) {
  if (stage === 'active') return '已开通'
  if (stage === 'partial') return '部分开通'
  return '未开通'
}

/** 只保留学员当前已授权的文章；未传学员 ID 时返回空数组（避免把全库文章当成在学内容）。 */
export function grantedArticles({ articles = [], permissions = [], hasLearner = true } = {}) {
  if (!hasLearner) return []
  const grantedIds = new Set((permissions || []).map(Number).filter(Number.isFinite))
  return (articles || []).filter(article => grantedIds.has(Number(article.id)))
}
