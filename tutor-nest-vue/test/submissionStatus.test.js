import assert from 'node:assert/strict'
import test from 'node:test'

import {
  buildProgressMap,
  hasQuestions,
  isPending,
  isReviewed,
  isUnsubmitted,
  pendingQuestionCount,
  questionCountOf
} from '../src/utils/submissionStatus.js'

test('没有题目的课件不参与提交筛选', () => {
  const article = { id: 1, questionCount: 0 }
  const progress = { totalQuestions: 0, submittedQuestions: 0, pendingReviews: 0 }
  assert.equal(hasQuestions(article, progress), false)
  assert.equal(isUnsubmitted(article, progress), false)
  assert.equal(isReviewed(article, progress), false)
})

test('老师没设答案、progress 没有该课件时，按一题未交判定待提交', () => {
  const article = { id: 2, questionCount: 19 }
  assert.equal(hasQuestions(article, undefined), true)
  assert.equal(pendingQuestionCount(article, undefined), 19)
  assert.equal(isUnsubmitted(article, undefined), true)
  assert.equal(isPending(undefined), false)
})

test('一题未交算未提交', () => {
  const article = { id: 3, questionCount: 5 }
  const progress = { totalQuestions: 5, submittedQuestions: 0, pendingReviews: 0 }
  assert.equal(pendingQuestionCount(article, progress), 5)
  assert.equal(isUnsubmitted(article, progress), true)
  assert.equal(isPending(progress), false)
})

test('只交了一部分也算未提交', () => {
  const article = { id: 4, questionCount: 5 }
  const progress = { totalQuestions: 5, submittedQuestions: 2, pendingReviews: 0 }
  assert.equal(pendingQuestionCount(article, progress), 3)
  assert.equal(isUnsubmitted(article, progress), true)
  assert.equal(isReviewed(article, progress), false)
})

test('部分提交且还有待批题时，同时进入未提交与待批阅', () => {
  const article = { id: 5, questionCount: 5 }
  const progress = { totalQuestions: 5, submittedQuestions: 2, pendingReviews: 2 }
  assert.equal(isUnsubmitted(article, progress), true)
  assert.equal(isPending(progress), true)
})

test('全部交齐且批完才算已批阅', () => {
  const article = { id: 6, questionCount: 5 }
  const reviewed = { totalQuestions: 5, submittedQuestions: 5, pendingReviews: 0 }
  assert.equal(isUnsubmitted(article, reviewed), false)
  assert.equal(isReviewed(article, reviewed), true)

  const pending = { totalQuestions: 5, submittedQuestions: 5, pendingReviews: 2 }
  assert.equal(isPending(pending), true)
  assert.equal(isReviewed(article, pending), false)
})

test('清单缺 questionCount 时退回答案行数', () => {
  const article = { id: 7, questionCount: null }
  assert.equal(questionCountOf(article, { totalQuestions: 3 }), 3)
  assert.equal(isUnsubmitted(article, { totalQuestions: 3, submittedQuestions: 1 }), true)
})

test('答案行数多于正文题目数时，已交题数按题目数封顶', () => {
  const article = { id: 8, questionCount: 4 }
  const progress = { totalQuestions: 6, submittedQuestions: 6, pendingReviews: 0 }
  assert.equal(pendingQuestionCount(article, progress), 0)
  assert.equal(isReviewed(article, progress), true)
})

test('buildProgressMap 归一化后端行并跳过非法 articleId', () => {
  const map = buildProgressMap([
    { articleId: 3, totalQuestions: 19, submittedQuestions: 7, pendingReviews: 2 },
    { articleId: null, totalQuestions: 1, submittedQuestions: 0, pendingReviews: 0 }
  ])
  assert.equal(map.size, 1)
  assert.deepEqual(map.get(3), { totalQuestions: 19, submittedQuestions: 7, pendingReviews: 2 })
})
