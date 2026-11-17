import assert from 'node:assert/strict'
import test from 'node:test'

import { answerProgress, hasQuestions, isAnswerComplete, needsAnswer } from '../src/utils/coursewareAnswerFilter.js'

test('纯讲义即使没设置答案也不进入待设置', () => {
  assert.equal(needsAnswer({ id: 1200, questionCount: 0 }, 0, []), false)
  assert.equal(isAnswerComplete({ id: 1200, questionCount: 0 }, 0, []), false)
  assert.equal(hasQuestions({ id: 1200, questionCount: 0 }), false)
})

test('有题目且未设置答案的文章进入待设置', () => {
  assert.equal(needsAnswer({ id: 3, questionCount: 19 }, 0, []), true)
})

test('只设置了一部分答案的文章仍留在待设置', () => {
  assert.deepEqual(answerProgress({ id: 3, questionCount: 19 }, 7, []), {
    total: 19, answered: 7, pending: true, complete: false
  })
})

test('全部题目都设置后移出待设置并进入已设置', () => {
  assert.equal(needsAnswer({ id: 3, questionCount: 19 }, 19, []), false)
  assert.equal(isAnswerComplete({ id: 3, questionCount: 19 }, 19, []), true)
})

test('旧数据缺题目数时，退回已确认无题的本地记录', () => {
  assert.equal(needsAnswer({ id: 9 }, 0, [9]), false)
  assert.equal(needsAnswer({ id: 9 }, 0, []), true)
})
