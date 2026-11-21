// src/utils/submissionStatus.js
// 系统内统一的「提交状态」口径：课件只要有题目就参与筛选，交了一部分也算未交齐。
// 题目总数优先取课件清单的 questionCount（来自正文占位符），老师还没设答案时
// 后端 progress 不会返回该课件，此时按「一题未交」处理。
// 教师端批阅中心与学生端课件地图/首页都从这里取判断，避免两边规则再次分叉。

const finiteNumber = (value) => {
  if (value === null || value === undefined || value === '') return null
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

/** 归一化后端 study-progress 行，得到 articleId → { totalQuestions, submittedQuestions, pendingReviews }。 */
export function buildProgressMap(rows) {
  const map = new Map()
  for (const row of rows || []) {
    const articleId = finiteNumber(row?.articleId)
    if (articleId === null) continue
    map.set(articleId, {
      totalQuestions: Math.max(0, finiteNumber(row.totalQuestions) || 0),
      submittedQuestions: Math.max(0, finiteNumber(row.submittedQuestions) || 0),
      pendingReviews: Math.max(0, finiteNumber(row.pendingReviews) || 0)
    })
  }
  return map
}

/** 课件题目总数：优先清单里的 questionCount，缺字段时退回答案行数。 */
export function questionCountOf(article, progress) {
  const manifestCount = finiteNumber(article?.questionCount)
  if (manifestCount !== null) return Math.max(0, manifestCount)
  return Math.max(0, finiteNumber(progress?.totalQuestions) || 0)
}

/** 已提交题数：不超过题目总数，避免历史答案行数与正文题目数不一致时出现负数余量。 */
export function submittedCountOf(article, progress) {
  const submitted = Math.max(0, finiteNumber(progress?.submittedQuestions) || 0)
  return Math.min(submitted, questionCountOf(article, progress))
}

/** 课件还差多少题没交；交齐或没有题目时为 0。 */
export function pendingQuestionCount(article, progress) {
  return questionCountOf(article, progress) - submittedCountOf(article, progress)
}

/** 有题目：没设答案时也算有题目，只要清单给出了题目数。 */
export function hasQuestions(article, progress) {
  return questionCountOf(article, progress) > 0
}

/** 未提交：有题目，但一题未交或没交齐。 */
export function isUnsubmitted(article, progress) {
  return hasQuestions(article, progress) && pendingQuestionCount(article, progress) > 0
}

/** 待批阅：有已提交但还没批的题。 */
export function isPending(progress) {
  return Math.max(0, finiteNumber(progress?.pendingReviews) || 0) > 0
}

/** 已批阅：题目全部交齐，且没有积压待批。 */
export function isReviewed(article, progress) {
  return hasQuestions(article, progress) && pendingQuestionCount(article, progress) === 0 && !isPending(progress)
}
