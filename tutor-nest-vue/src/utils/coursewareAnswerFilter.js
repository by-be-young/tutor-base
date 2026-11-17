// src/utils/coursewareAnswerFilter.js
// 「设置答案」列表的筛选规则：纯讲义不进入待设置，部分设置仍算待设置。

function hasQuestionCount(article) {
  const count = article?.questionCount
  return count !== null && count !== undefined && count !== ''
}

/** 清单带题目数时直接判断；旧数据缺字段时由调用方提供已确认无题的 id 列表。 */
export function hasQuestions(article, noQuestionIds = []) {
  if (hasQuestionCount(article)) return Number(article.questionCount) > 0
  return !noQuestionIds.includes(Number(article?.id))
}

export function answerProgress(article, answerCount = 0, noQuestionIds = []) {
  if (!hasQuestions(article, noQuestionIds)) {
    return { total: 0, answered: 0, pending: false, complete: false }
  }
  const answered = Math.max(0, Number(answerCount) || 0)
  if (!hasQuestionCount(article)) {
    return { total: null, answered, pending: answered === 0, complete: answered > 0 }
  }
  const total = Math.max(0, Number(article.questionCount))
  return { total, answered, pending: answered < total, complete: answered >= total }
}

/** 未全部设置且确实有题目的文章，才需要出现在「待设置」。 */
export function needsAnswer(article, answerCount = 0, noQuestionIds = []) {
  return answerProgress(article, answerCount, noQuestionIds).pending
}

/** 全部题目都有答案的文章，才算「已设置」。 */
export function isAnswerComplete(article, answerCount = 0, noQuestionIds = []) {
  return answerProgress(article, answerCount, noQuestionIds).complete
}
