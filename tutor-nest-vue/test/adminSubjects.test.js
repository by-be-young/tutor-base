import assert from 'node:assert/strict'
import test from 'node:test'

import {
  activeSubjects,
  buildSubjectScope,
  stageLabel,
  subjectStage,
  subjectToggleIds
} from '../src/utils/adminSubjects.js'

const ARTICLES = [
  { id: 1, series: '化学' },
  { id: 2, series: '化学' },
  { id: 3, series: '英语' },
  { id: 4, series: '初中英语' },
  { id: 5, series: '初中英语' }
]

test('subjectStage classifies inactive, partial and full coverage', () => {
  assert.equal(subjectStage(0, 4), 'inactive')
  assert.equal(subjectStage(0, 0), 'inactive')
  assert.equal(subjectStage(2, 4), 'partial')
  assert.equal(subjectStage(4, 4), 'active')
  assert.equal(subjectStage(5, 4), 'active')
})

test('stageLabel maps stages to Chinese labels', () => {
  assert.equal(stageLabel('inactive'), '未开通')
  assert.equal(stageLabel('partial'), '部分开通')
  assert.equal(stageLabel('active'), '已开通')
  assert.equal(stageLabel(undefined), '未开通')
})

test('buildSubjectScope counts grants per subject and tolerates string ids', () => {
  const scope = buildSubjectScope({
    subjects: ['化学', '英语', '初中英语'],
    articles: ARTICLES,
    permissions: ['1', 2, 3]
  })

  assert.deepEqual(scope, [
    { subject: '化学', total: 2, granted: 2, stage: 'active' },
    { subject: '英语', total: 1, granted: 1, stage: 'active' },
    { subject: '初中英语', total: 2, granted: 0, stage: 'inactive' }
  ])
  assert.deepEqual(activeSubjects(scope), ['化学', '英语'])
})

test('buildSubjectScope ignores empty inputs', () => {
  assert.deepEqual(buildSubjectScope(), [])
  assert.deepEqual(buildSubjectScope({ subjects: ['化学'], articles: [], permissions: [1] }),
    [{ subject: '化学', total: 0, granted: 0, stage: 'inactive' }])
})

test('subjectToggleIds activates a whole subject and then clears it', () => {
  const partial = subjectToggleIds({ articles: ARTICLES, permissions: [1], subject: '化学' })
  assert.deepEqual(partial, { ids: [2], checked: true })

  const unset = subjectToggleIds({ articles: ARTICLES, permissions: [], subject: '初中英语' })
  assert.deepEqual(unset, { ids: [4, 5], checked: true })

  const full = subjectToggleIds({ articles: ARTICLES, permissions: [1, 2], subject: '化学' })
  assert.deepEqual(full, { ids: [1, 2], checked: false })
})

test('subjectToggleIds returns nothing for an unknown or empty subject', () => {
  assert.deepEqual(subjectToggleIds({ articles: ARTICLES, permissions: [1], subject: '物理' }),
    { ids: [], checked: false })
  assert.deepEqual(subjectToggleIds(), { ids: [], checked: false })
})
