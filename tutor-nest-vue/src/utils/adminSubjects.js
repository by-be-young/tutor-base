// src/utils/adminSubjects.js
// 管理员按「学科」给学员开通科目；科目归属与具体文章授权是两件事。
// 这里只放不依赖 Vue / Pinia / 浏览器的纯函数，便于 node:test 直接覆盖。

/**
 * 学科 → 阶段：'inactive' 未开通、'pending' 科目已开通但尚未授权文章、
 * 'partial' 部分文章已授权、'active' 全科文章已授权。
 * 未开通科目即使 permissions 里残留文章，也不会显示为在学。
 */
export function subjectStage(grantedCount, totalCount, enrolled = true) {
  if (!enrolled) return 'inactive'
  const total = Number(totalCount) || 0
  const granted = Number(grantedCount) || 0
  if (granted <= 0) return 'pending'
  if (total <= 0) return 'partial'
  return granted >= total ? 'active' : 'partial'
}

/**
 * 按学科汇总学员当前科目归属与授权情况。
 * @param {{
 *   subjects: string[],
 *   enrolledSubjects: string[],
 *   articles: Array<{ id: number|string, series?: string }>,
 *   permissions: Array<number|string>
 * }} input
 * @returns {Array<{ subject: string, total: number, granted: number, enrolled: boolean, stage: string }>}
 */
export function buildSubjectScope({
  subjects = [],
  enrolledSubjects = [],
  articles = [],
  permissions = []
} = {}) {
  const grantedIds = new Set((permissions || []).map(Number).filter(Number.isFinite))
  const enrolled = new Set(enrolledSubjects || [])
  return (subjects || []).map(subject => {
    const subjectArticles = (articles || []).filter(article => article.series === subject)
    const granted = subjectArticles.filter(article => grantedIds.has(Number(article.id))).length
    const isEnrolled = enrolled.has(subject)
    return {
      subject,
      total: subjectArticles.length,
      granted,
      enrolled: isEnrolled,
      stage: subjectStage(granted, subjectArticles.length, isEnrolled)
    }
  })
}

/** 在学（已开通科目）列表，按传入顺序；与文章是否授权无关。 */
export function activeSubjects(scope) {
  return (scope || []).filter(item => item.enrolled).map(item => item.subject)
}

/** 学员已开通的学科，供权限管理等页面收敛可见范围。 */
export function openedSubjects({ subjects = [], enrolledSubjects = [] } = {}) {
  const enrolled = new Set(enrolledSubjects || [])
  return (subjects || []).filter(subject => enrolled.has(subject))
}

/**
 * 点击学科卡片：开通只建立科目归属，初始不授权任何文章；取消时同步清理该科已授权文章。
 * 返回需要在权限列表里新增或移除的文章 ID，以及点击后的科目归属状态。
 */
export function subjectToggleChange({ articles = [], subjects = [], enrolledSubjects = [], subject } = {}) {
  const enrolled = new Set(enrolledSubjects || [])
  const subjectArticles = (articles || []).filter(article => article.series === subject)
  const subjectIds = subjectArticles.map(article => Number(article.id)).filter(Number.isFinite)
  const wasEnrolled = enrolled.has(subject)
  return {
    enrolled: !wasEnrolled,
    removeArticleIds: wasEnrolled ? subjectIds : []
  }
}

/** 阶段中文标签，用于按钮与提示。 */
export function stageLabel(stage) {
  if (stage === 'active') return '已开通'
  if (stage === 'partial') return '部分文章已授权'
  if (stage === 'pending') return '已开通（未授权文章）'
  return '未开通'
}

/** 只保留学员当前已授权的文章；未传学员 ID 时返回空数组（避免把全库文章当成在学内容）。 */
export function grantedArticles({ articles = [], permissions = [], hasLearner = true } = {}) {
  if (!hasLearner) return []
  const grantedIds = new Set((permissions || []).map(Number).filter(Number.isFinite))
  return (articles || []).filter(article => grantedIds.has(Number(article.id)))
}
