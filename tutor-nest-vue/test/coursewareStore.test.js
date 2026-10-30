import assert from 'node:assert/strict'
import test from 'node:test'

import {
  catalogSignature,
  catalogSyncItems,
  computeCatalogBySubject,
  computeUnclaimedCount,
  computeVisibleArticleIds,
  computeVisibleSubjects,
  coursewareActionError,
  isAbandonedArticle,
  isExcludedArticle
} from '../src/utils/coursewareClaims.js'

const CATALOG_SUBJECTS = [
  { subject: '化学', total: 2, mine: false, claimedByOthers: [] },
  { subject: '英语', total: 1, mine: false, claimedByOthers: [] },
  { subject: '初中英语', total: 1, mine: false, claimedByOthers: ['teacher-a'] }
]

const CATALOG_ITEMS = [
  { articleId: 1, subject: '化学', title: '原子的结构', claimedBy: [] },
  { articleId: 2, subject: '化学', title: '离子键', claimedBy: [{ accountId: 9, username: 'teacher-b' }] },
  { articleId: 3, subject: '英语', title: '时态总览', claimedBy: [] },
  { articleId: 4, subject: '初中英语', title: '定语从句', claimedBy: [{ accountId: 9, username: 'teacher-b' }] }
]

test('catalogSyncItems maps static articles to the sync payload and skips invalid rows', () => {
  const items = catalogSyncItems([
    { id: '1', series: ' 化学 ', title: ' 原子的结构 ' },
    { id: 2, series: '化学', title: null },
    { id: 3, series: '' },
    { id: 'abc', series: '英语' },
    { id: 1, series: '化学' },
    null
  ])

  assert.deepEqual(items, [
    { articleId: 1, subject: '化学', title: '原子的结构' },
    { articleId: 2, subject: '化学', title: null }
  ])
  assert.deepEqual(catalogSyncItems(), [])
})

test('catalogSignature is stable across input order and distinguishes subject changes', () => {
  const items = catalogSyncItems([
    { id: 2, series: '化学' },
    { id: 1, series: '英语' }
  ])
  const reordered = catalogSyncItems([
    { id: 1, series: '英语' },
    { id: 2, series: '化学' }
  ])

  assert.equal(catalogSignature(items), catalogSignature(reordered))
  assert.notEqual(
    catalogSignature(items),
    catalogSignature(catalogSyncItems([{ id: 2, series: '初中英语' }, { id: 1, series: '英语' }]))
  )
  assert.equal(catalogSignature([]), '')
})

test('computeVisibleArticleIds unions subject claims and individually claimed articles', () => {
  const visible = computeVisibleArticleIds(CATALOG_ITEMS, ['化学'], ['3'])

  assert.deepEqual([...visible].sort((left, right) => left - right), [1, 2, 3])
  assert.deepEqual([...computeVisibleArticleIds(CATALOG_ITEMS, [], [4])], [4])
  assert.equal(computeVisibleArticleIds([], ['化学'], [1]).size, 0)
  assert.equal(computeVisibleArticleIds(CATALOG_ITEMS, ['物理'], []).size, 0)
  assert.equal(computeVisibleArticleIds(CATALOG_ITEMS).size, 0)
})

test('computeVisibleSubjects keeps mine subjects and subjects with visible articles', () => {
  const mineSubjects = CATALOG_SUBJECTS.map(item => (item.subject === '英语' ? { ...item, mine: true } : item))
  const visibleIds = computeVisibleArticleIds(CATALOG_ITEMS, ['化学'], [])

  assert.deepEqual(computeVisibleSubjects(mineSubjects, CATALOG_ITEMS, visibleIds), ['化学', '英语'])
  assert.deepEqual(computeVisibleSubjects(CATALOG_SUBJECTS, CATALOG_ITEMS, new Set()), [])
  // 目录里没有的学科即使有可见课件也不返回（以 catalog subjects 为准）
  assert.deepEqual(
    computeVisibleSubjects([{ subject: '化学', mine: false }], [{ articleId: 1, subject: '英语' }], new Set([1])),
    []
  )
  assert.deepEqual(computeVisibleSubjects([], CATALOG_ITEMS, visibleIds), [])
})

test('computeUnclaimedCount counts catalog items without any claimer and not abandoned', () => {
  assert.equal(computeUnclaimedCount(CATALOG_ITEMS), 2)
  assert.equal(computeUnclaimedCount([{ articleId: 1 }, { articleId: 2, claimedBy: [] }]), 2)
  assert.equal(computeUnclaimedCount([
    { articleId: 1, claimedBy: [], abandoned: true },
    { articleId: 2, claimedBy: [] }
  ]), 1)
  assert.equal(computeUnclaimedCount([]), 0)
})

test('computeCatalogBySubject groups items by subject in backend order', () => {
  const grouped = computeCatalogBySubject(CATALOG_ITEMS)

  assert.deepEqual([...grouped.keys()], ['化学', '英语', '初中英语'])
  assert.deepEqual(grouped.get('化学').map(item => item.articleId), [1, 2])
  assert.deepEqual(grouped.get('英语').map(item => item.articleId), [3])
  assert.equal(computeCatalogBySubject([]).size, 0)
})

test('computeVisibleArticleIds excludes subject-covered articles released individually', () => {
  // 学科覆盖但被单篇释放 → 不可见
  assert.deepEqual(
    [...computeVisibleArticleIds(CATALOG_ITEMS, ['化学'], [], [1])],
    [2]
  )
  // 释放后再单篇认领 → 重新可见
  assert.deepEqual(
    [...computeVisibleArticleIds(CATALOG_ITEMS, ['化学'], [1], [1])],
    [1, 2]
  )
  // 未被学科覆盖且未单篇认领 → 不可见
  assert.equal(computeVisibleArticleIds(CATALOG_ITEMS, [], [], [1]).size, 0)
  // 单篇认领不受排除影响（不在学科范围内时也可见）
  assert.deepEqual([...computeVisibleArticleIds(CATALOG_ITEMS, [], [3], [3])], [3])
  // 字符串形式的 id 容错
  assert.deepEqual([...computeVisibleArticleIds(CATALOG_ITEMS, ['化学'], [], ['1'])], [2])
})

test('computeVisibleArticleIds excludes abandoned articles even under a claimed subject', () => {
  assert.deepEqual(
    [...computeVisibleArticleIds(CATALOG_ITEMS, ['化学'], [], [], [1])],
    [2]
  )
  assert.deepEqual(
    [...computeVisibleArticleIds(CATALOG_ITEMS, [], [1], [], [1])],
    []
  )
  assert.deepEqual(
    [...computeVisibleArticleIds(
      [{ articleId: 1, subject: '化学', abandoned: true, claimedBy: [] },
       { articleId: 2, subject: '化学', claimedBy: [] }],
      ['化学'], [], []
    )],
    [2]
  )
})

test('isAbandonedArticle matches positive ids and tolerates invalid input', () => {
  assert.equal(isAbandonedArticle(1, [1, 3]), true)
  assert.equal(isAbandonedArticle('3', [1, 3]), true)
  assert.equal(isAbandonedArticle(2, [1, 3]), false)
  assert.equal(isAbandonedArticle(1, null), false)
  assert.equal(isAbandonedArticle('abc', [1]), false)
})

test('isExcludedArticle matches positive ids and tolerates invalid input', () => {
  assert.equal(isExcludedArticle(1, [1, 3]), true)
  assert.equal(isExcludedArticle('3', [1, 3]), true)
  assert.equal(isExcludedArticle(2, [1, 3]), false)
  assert.equal(isExcludedArticle(1, null), false)
  assert.equal(isExcludedArticle('abc', [1]), false)
})

test('coursewareActionError maps courseware_not_claimed and other statuses to Chinese messages', () => {
  const notClaimed = coursewareActionError({ code: 'courseware_not_claimed', status: 403 }, '认领课件')
  assert.equal(notClaimed.message, '该课件不在你的认领范围内')
  assert.equal(notClaimed.code, 'courseware_not_claimed')
  assert.equal(notClaimed.status, 403)

  assert.equal(
    coursewareActionError({ code: 'resource_not_found', status: 404 }, '认领学科').message,
    '课件目录中找不到该学科或课件，请刷新后重试'
  )
  assert.equal(
    coursewareActionError({ status: 401 }, '释放认领').message,
    '登录状态已失效，请重新登录后再试'
  )
  assert.equal(
    coursewareActionError({ status: 500, message: '后端服务请求失败' }, '认领学科').message,
    '认领学科失败：后端服务请求失败'
  )
  assert.equal(coursewareActionError(null, '刷新').message, '刷新失败，请稍后重试')
})
