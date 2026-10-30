import assert from 'node:assert/strict'
import test from 'node:test'

import {
  activeSubjects,
  buildSubjectScope,
  grantedArticles,
  openedSubjects,
  stageLabel,
  subjectStage,
  subjectToggleChange
} from '../src/utils/adminSubjects.js'

const ARTICLES = [
  { id: 1, series: '化学' },
  { id: 2, series: '化学' },
  { id: 3, series: '英语' },
  { id: 4, series: '初中英语' },
  { id: 5, series: '初中英语' }
]

test('subjectStage classifies inactive, pending, partial and full coverage', () => {
  assert.equal(subjectStage(0, 4, false), 'inactive')
  assert.equal(subjectStage(4, 4, false), 'inactive')
  assert.equal(subjectStage(0, 4, true), 'pending')
  assert.equal(subjectStage(0, 0, true), 'pending')
  assert.equal(subjectStage(2, 4, true), 'partial')
  assert.equal(subjectStage(4, 4, true), 'active')
  assert.equal(subjectStage(5, 4, true), 'active')
})

test('stageLabel maps stages to Chinese labels', () => {
  assert.equal(stageLabel('inactive'), '未开通')
  assert.equal(stageLabel('pending'), '已开通（未授权文章）')
  assert.equal(stageLabel('partial'), '部分文章已授权')
  assert.equal(stageLabel('active'), '已开通')
  assert.equal(stageLabel(undefined), '未开通')
})

test('buildSubjectScope separates subject enrollment from article grants', () => {
  const scope = buildSubjectScope({
    subjects: ['化学', '英语', '初中英语'],
    enrolledSubjects: ['化学', '初中英语'],
    articles: ARTICLES,
    permissions: ['1', 2]
  })

  assert.deepEqual(scope, [
    { subject: '化学', total: 2, granted: 2, enrolled: true, stage: 'active' },
    { subject: '英语', total: 1, granted: 0, enrolled: false, stage: 'inactive' },
    { subject: '初中英语', total: 2, granted: 0, enrolled: true, stage: 'pending' }
  ])
  assert.deepEqual(activeSubjects(scope), ['化学', '初中英语'])
})

test('buildSubjectScope ignores empty inputs', () => {
  assert.deepEqual(buildSubjectScope(), [])
  assert.deepEqual(buildSubjectScope({ subjects: ['化学'], articles: [], permissions: [1] }),
    [{ subject: '化学', total: 0, granted: 0, enrolled: false, stage: 'inactive' }])
})

test('subjectToggleChange opens a subject without granting any article', () => {
  const open = subjectToggleChange({
    articles: ARTICLES,
    subjects: ['化学', '初中英语'],
    enrolledSubjects: [],
    subject: '初中英语'
  })
  assert.deepEqual(open, { enrolled: true, removeArticleIds: [] })

  // 科目未开通但残留文章授权时，开通动作也不携带文章 ID
  const reopen = subjectToggleChange({
    articles: ARTICLES,
    subjects: ['化学'],
    enrolledSubjects: [],
    permissions: [4, 5],
    subject: '初中英语'
  })
  assert.deepEqual(reopen, { enrolled: true, removeArticleIds: [] })
})

test('subjectToggleChange closes a subject and removes its granted articles', () => {
  const close = subjectToggleChange({
    articles: ARTICLES,
    subjects: ['化学'],
    enrolledSubjects: ['化学'],
    subject: '化学'
  })
  assert.deepEqual(close, { enrolled: false, removeArticleIds: [1, 2] })

  const closeEmpty = subjectToggleChange({
    articles: ARTICLES,
    subjects: ['英语'],
    enrolledSubjects: ['英语'],
    subject: '英语'
  })
  assert.deepEqual(closeEmpty, { enrolled: false, removeArticleIds: [3] })
})

test('subjectToggleChange tolerates unknown or empty subject', () => {
  assert.deepEqual(
    subjectToggleChange({ articles: ARTICLES, subjects: ['化学'], enrolledSubjects: [], subject: '物理' }),
    { enrolled: true, removeArticleIds: [] }
  )
  assert.deepEqual(subjectToggleChange(), { enrolled: true, removeArticleIds: [] })
})

test('grantedArticles keeps only the learner\'s granted articles', () => {
  assert.deepEqual(
    grantedArticles({ articles: ARTICLES, permissions: ['1', 3] }).map(item => item.id),
    [1, 3]
  )
  assert.deepEqual(grantedArticles({ articles: ARTICLES, permissions: [] }), [])
  assert.deepEqual(grantedArticles({ articles: ARTICLES, hasLearner: false }), [])
  assert.deepEqual(grantedArticles(), [])
})

test('openedSubjects returns enrolled subjects even without granted articles', () => {
  assert.deepEqual(
    openedSubjects({ subjects: ['化学', '英语', '初中英语'], enrolledSubjects: ['化学', '初中英语'] }),
    ['化学', '初中英语']
  )
  assert.deepEqual(openedSubjects({ subjects: ['化学', '英语'], enrolledSubjects: [] }), [])
  assert.deepEqual(openedSubjects(), [])
})
