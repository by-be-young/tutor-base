// test/coursewareLibrary.test.js
// 课件库只使用数据库托管正文：草稿 → 已发布正文，不再回退 public/articles。
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  loadArticleMarkdownForEditing,
  loadCoursewareLibrary
} from '../src/utils/coursewareLibrary.js'

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

async function withFetch(handler, run) {
  const originalFetch = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url, options) => {
    calls.push({ url: String(url), options })
    return handler(String(url), options)
  }
  try {
    await run(calls)
  } finally {
    globalThis.fetch = originalFetch
  }
}

test('编辑课件时从托管草稿/已发布正文读取', async () => {
  await withFetch(
    (url) => (url.includes('/courseware/drafts/45')
      ? jsonResponse(200, { articleId: 45, markdown: '# 口语训练-1', hasDraft: false })
      : jsonResponse(200, [])),
    async (calls) => {
      const loaded = await loadArticleMarkdownForEditing({ articleId: 45 })
      assert.equal(loaded.markdown, '# 口语训练-1')
      assert.equal(loaded.hasDraft, false)
      assert.equal(loaded.source, 'published')
      assert.equal(calls.length, 1)
      assert.match(calls[0].url, /\/courseware\/drafts\/45$/)
    }
  )
})

test('正文缺失时直接抛后端错误，不读取静态文件', async () => {
  await withFetch(
    (url) => (url.includes('/courseware/drafts/45')
      ? jsonResponse(409, { code: 'courseware_content_missing', detail: '正文文件缺失' })
      : new Response('静态正文', { status: 200 })),
    async (calls) => {
      await assert.rejects(
        () => loadArticleMarkdownForEditing({ articleId: 45 }),
        (error) => error.code === 'courseware_content_missing'
      )
      assert.equal(calls.length, 1)
      assert.match(calls[0].url, /\/courseware\/drafts\/45$/)
    }
  )
})

test('课件清单只取后端托管接口', async () => {
  await withFetch(
    (url) => (url.includes('/courseware/articles')
      ? jsonResponse(200, [{ articleId: 45, title: '口语训练-1', published: true }])
      : new Response('静态目录', { status: 200 })),
    async (calls) => {
      const { articles } = await loadCoursewareLibrary()
      assert.equal(articles.length, 1)
      assert.equal(articles[0].articleId, 45)
      assert.equal(calls.length, 1)
      assert.match(calls[0].url, /\/courseware\/articles$/)
    }
  )
})
