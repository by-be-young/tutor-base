import assert from 'node:assert/strict'
import test from 'node:test'

import { flattenManifests, groupByTeacher } from '../src/utils/coursewareManifest.js'
import { loadArticleMarkdown } from '../src/utils/articleContent.js'

const MANIFESTS = [
  {
    teacherId: 7,
    teacherName: 'young',
    subject: '高中化学',
    version: 3,
    publishedAt: '2026-10-30T15:29:00Z',
    articles: [
      {
        articleId: 3,
        legacyArticleId: 3,
        title: '3-1 原电池',
        series: '高中化学',
        logicalPath: '高中化学/理论课讲义/3-1 原电池.md',
        contentHash: 'sha256:aaa',
        questionCount: 19
      },
      {
        articleId: 1200,
        legacyArticleId: null,
        title: '新讲义',
        series: '高中化学',
        logicalPath: '高中化学/理论课讲义/新讲义.md',
        contentHash: 'sha256:bbb',
        questionCount: 0
      }
    ]
  }
]

test('清单展平后保留全局 id，日期与路径都来自发布清单', () => {
  const articles = flattenManifests(MANIFESTS)

  assert.deepEqual(articles.map(article => article.id), [3, 1200])
  assert.deepEqual(articles[0], {
    id: 3,
    title: '3-1 原电池',
    series: '高中化学',
    date: '2026-10-30',
    path: '高中化学/理论课讲义/3-1 原电池.md',
    teacherId: 7,
    teacherName: 'young',
    teacherArticleId: 3,
    contentHash: 'sha256:aaa',
    releaseVersion: 3
  })
  assert.equal(articles[1].date, '2026-10-30')
  assert.equal(articles[1].path, '高中化学/理论课讲义/新讲义.md')
  assert.equal(articles[1].teacherId, 7)
})

test('跨老师撞号时退回命名空间内唯一的 articleId', () => {
  const other = {
    teacherId: 9,
    teacherName: 'Amy',
    subject: '高中化学',
    version: 1,
    publishedAt: '2026-10-30T16:00:00Z',
    articles: [{
      articleId: 1003,
      legacyArticleId: 3,
      title: '讲义（另一位老师）',
      series: '高中化学',
      logicalPath: '高中化学/讲义.md',
      contentHash: 'sha256:ccc',
      questionCount: 1
    }]
  }

  const articles = flattenManifests([...MANIFESTS, other])

  assert.deepEqual(articles.map(article => article.id), [3, 1200, 1003])
  assert.equal(articles[2].title, '讲义（另一位老师）')
  assert.equal(articles[2].teacherId, 9)
})

test('多老师时按老师分组，保持后端顺序', () => {
  const other = {
    teacherId: 9,
    teacherName: 'Amy',
    subject: '高中英语',
    version: 2,
    publishedAt: '2026-10-31T16:00:00Z',
    articles: [{
      articleId: 2001,
      legacyArticleId: null,
      title: '语法讲义',
      series: '高中英语',
      logicalPath: '高中英语/语法讲义.md',
      contentHash: 'sha256:ddd',
      questionCount: 2
    }]
  }

  const groups = groupByTeacher(flattenManifests([...MANIFESTS, other]))

  assert.equal(groups.length, 2)
  assert.equal(groups[0].teacherId, 7)
  assert.deepEqual(groups[0].subjects, ['高中化学'])
  assert.equal(groups[0].articles.length, 2)
  assert.equal(groups[1].teacherName, 'Amy')
  assert.deepEqual(groups[1].subjects, ['高中英语'])
})

test('单老师时不额外分组', () => {
  const groups = groupByTeacher(flattenManifests(MANIFESTS))
  assert.equal(groups.length, 1)
  assert.equal(groups[0].articles.length, 2)
})

test('正文只走托管接口，不再回退静态文件', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  try {
    globalThis.fetch = async (url) => {
      calls.push(String(url))
      return new Response('# 原电池\n\n【题干1】题\n', { status: 200 })
    }
    const markdown = await loadArticleMarkdown({
      id: 3, path: '高中化学/3-1 原电池.md', teacherId: 7, teacherArticleId: 3
    })
    assert.match(markdown, /原电池/)
    assert.equal(calls.length, 1)
    assert.match(calls[0], /\/courseware\/articles\/7\/3\/content$/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('没有托管身份的课件不读取任何正文', async () => {
  const originalFetch = globalThis.fetch
  let called = false
  try {
    globalThis.fetch = async () => {
      called = true
      return new Response('静态正文', { status: 200 })
    }
    assert.equal(await loadArticleMarkdown({ id: 9, path: '高中化学/9.md' }), null)
    assert.equal(called, false)
  } finally {
    globalThis.fetch = originalFetch
  }
})
