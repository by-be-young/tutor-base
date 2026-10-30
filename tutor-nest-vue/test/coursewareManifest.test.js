import assert from 'node:assert/strict'
import test from 'node:test'

import { flattenManifests } from '../src/utils/coursewareManifest.js'
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

test('清单展平后保留全局 id 并补充静态索引里的日期与路径', () => {
  const staticIndex = [
    { id: 3, title: '3-1 原电池', series: '高中化学', date: '2026-08-06', path: '高中化学/理论课讲义/3-1 原电池.md' }
  ]

  const articles = flattenManifests(MANIFESTS, staticIndex)

  assert.deepEqual(articles.map(article => article.id), [3, 1200])
  assert.deepEqual(articles[0], {
    id: 3,
    title: '3-1 原电池',
    series: '高中化学',
    date: '2026-08-06',
    path: '高中化学/理论课讲义/3-1 原电池.md',
    teacherId: 7,
    teacherName: 'young',
    teacherArticleId: 3,
    contentHash: 'sha256:aaa',
    releaseVersion: 3
  })
  // 没有静态索引条目时用发布日期兜底，并保留托管侧的逻辑路径
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

  const articles = flattenManifests([...MANIFESTS, other], [])

  assert.deepEqual(articles.map(article => article.id), [3, 1200, 1003])
  assert.equal(articles[2].title, '讲义（另一位老师）')
  assert.equal(articles[2].teacherId, 9)
})

test('正文优先取托管接口，接口失败时回退静态文件', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  try {
    globalThis.fetch = async (url) => {
      calls.push(String(url))
      if (String(url).includes('/courseware/articles/')) {
        return new Response('# 原电池\n\n【题干1】题\n', { status: 200 })
      }
      return new Response('fallback', { status: 200 })
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

  const failCalls = []
  try {
    globalThis.fetch = async (url) => {
      failCalls.push(String(url))
      if (String(url).includes('/courseware/articles/')) {
        return new Response(JSON.stringify({ code: 'courseware_forbidden' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' }
        })
      }
      return new Response('静态正文', { status: 200 })
    }
    const markdown = await loadArticleMarkdown({
      id: 3, path: '高中化学/3-1 原电池.md', teacherId: 7, teacherArticleId: 3
    })
    assert.equal(markdown, '静态正文')
    assert.equal(failCalls.length, 2)
    assert.match(failCalls[1], /articles\/高中化学\/3-1 原电池\.md$/)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('未托管的课件直接走静态文件', async () => {
  const originalFetch = globalThis.fetch
  const calls = []
  try {
    globalThis.fetch = async (url) => {
      calls.push(String(url))
      return new Response('静态正文', { status: 200 })
    }
    const markdown = await loadArticleMarkdown({ id: 9, path: '高中化学/9.md' })
    assert.equal(markdown, '静态正文')
    assert.equal(calls.length, 1)
    assert.doesNotMatch(calls[0], /courseware\/articles/)
  } finally {
    globalThis.fetch = originalFetch
  }
})
