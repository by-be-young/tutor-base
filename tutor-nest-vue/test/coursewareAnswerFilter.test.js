import assert from 'node:assert/strict'
import test from 'node:test'

import { hasQuestions, needsAnswer } from '../src/utils/coursewareAnswerFilter.js'

test('纯讲义即使没设置答案也不进入待设置', () => {
  assert.equal(needsAnswer({ id: 1200, questionCount: 0 }, [], []), false)
  assert.equal(hasQuestions({ id: 1200, questionCount: 0 }), false)
})

test('有题目且未设置答案的文章进入待设置', () => {
  assert.equal(needsAnswer({ id: 3, questionCount: 19 }, [], []), true)
})

test('已设置答案的文章不再出现在待设置', () => {
  assert.equal(needsAnswer({ id: 3, questionCount: 19 }, [3], []), false)
})

test('旧数据缺题目数时，退回已确认无题的本地记录', () => {
  assert.equal(needsAnswer({ id: 9 }, [], [9]), false)
  assert.equal(needsAnswer({ id: 9 }, [], []), true)
})
