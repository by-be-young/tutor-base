import assert from 'node:assert/strict'
import test from 'node:test'

import { coursewareGateway } from '../src/gateways/coursewareGateway.js'
import {
  normalizeCopyResult,
  normalizeCopySummary,
  normalizeSharedSubject,
  normalizeShares
} from '../src/utils/coursewareSharing.js'

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

test('分享响应规整：丢弃缺 id 的行，布尔与 scope 归一', () => {
  const shares = normalizeShares([
    { shareId: 3, scope: 'article', subject: '高中化学', articleId: 9, allowPreview: 1, allowCopy: true },
    { shareId: 'bad', subject: 'x' },
    { shareId: 4, scope: 'weird', subject: '高中化学' }
  ])

  assert.equal(shares.length, 2)
  assert.deepEqual(shares[0], {
    shareId: 3,
    scope: 'article',
    subject: '高中化学',
    articleId: 9,
    allowPreview: true,
    allowCopy: true,
    revoked: false
  })
  assert.equal(shares[1].scope, 'subject')
  assert.equal(shares[1].articleId, null)
})

test('资源目录与获取结果规整', () => {
  assert.equal(normalizeSharedSubject({ teacherId: 0 }), null)
  assert.deepEqual(normalizeSharedSubject({
    teacherId: 7, teacherName: 'Amy', subject: '高中英语', wholeSubject: true, articleCount: '16', allowCopy: true
  }), {
    teacherId: 7, teacherName: 'Amy', subject: '高中英语', wholeSubject: true, articleCount: 16, allowCopy: true
  })

  assert.equal(normalizeCopyResult({ copyId: 1 }), null)
  assert.deepEqual(normalizeCopyResult({ copyId: 118, localArticleId: 1007, title: '原电池', sourceVersion: 12 }),
    { copyId: 118, localArticleId: 1007, title: '原电池', sourceVersion: 12, alreadyExists: false })

  const summary = normalizeCopySummary({
    copyId: 5, localArticleId: 1008, sourceTeacherId: 7, sourceContentHash: null, hasUpdate: 'yes'
  })
  assert.equal(summary.hasUpdate, true)
  assert.equal(summary.sourceContentHash, null)
  assert.equal(summary.status, 'active')
})

test('获取课件按整科/单篇发送正确请求体', async () => {
  await withFetch(
    (url) => (url.includes('/csrf')
      ? jsonResponse(200, { token: 'test-token' })
      : jsonResponse(200, [{ copyId: 1, localArticleId: 1000, title: 'A', sourceVersion: 1, alreadyExists: false }])),
    async (calls) => {
      await coursewareGateway.acquire({ sourceTeacherId: 7, subject: '高中化学' })
      const post = calls.find(call => call.options.method === 'POST')
      const body = JSON.parse(post.options.body)
      assert.equal(post.url.endsWith('/admin/courseware/copies'), true)
      assert.deepEqual(body, { sourceTeacherId: 7, subject: '高中化学' })
      assert.equal(post.options.headers['X-CSRF-TOKEN'], 'test-token')
    }
  )

  await withFetch(
    (url) => (url.includes('/csrf') ? jsonResponse(200, { token: 'test-token' }) : jsonResponse(200, [])),
    async (calls) => {
      await coursewareGateway.acquire({
        sourceTeacherId: 7, subject: '高中化学', articleIds: [3, 9], targetRootPath: '我的课件'
      })
      const post = calls.find(call => call.options.method === 'POST')
      assert.deepEqual(JSON.parse(post.options.body), {
        sourceTeacherId: 7, subject: '高中化学', articleIds: [3, 9], targetRootPath: '我的课件'
      })
    }
  )
})

test('分享整科时省略 articleIds，单篇时带上', async () => {
  await withFetch(
    (url) => (url.includes('/csrf')
      ? jsonResponse(200, { token: 'test-token' })
      : jsonResponse(200, [{ shareId: 1, scope: 'subject', subject: '高中化学', allowPreview: true, allowCopy: true }])),
    async (calls) => {
      const shares = await coursewareGateway.createShare({ subject: '高中化学', scope: 'subject' })
      assert.equal(shares.length, 1)
      const post = calls.find(call => call.options.method === 'POST')
      assert.deepEqual(JSON.parse(post.options.body), {
        subject: '高中化学', scope: 'subject', allowPreview: true, allowCopy: true
      })
    }
  )

  await withFetch(
    (url) => (url.includes('/csrf') ? jsonResponse(200, { token: 'test-token' }) : jsonResponse(200, [])),
    async (calls) => {
      await coursewareGateway.createShare({ subject: '高中化学', scope: 'article', articleIds: [3], allowCopy: false })
      const post = calls.find(call => call.options.method === 'POST')
      assert.deepEqual(JSON.parse(post.options.body), {
        subject: '高中化学', scope: 'article', allowPreview: true, allowCopy: false, articleIds: [3]
      })
    }
  )
})

test('预览走 text 接口，改名走 PATCH', async () => {
  await withFetch(
    (url) => (url.includes('/csrf')
      ? jsonResponse(200, { token: 'test-token' })
      : url.includes('/preview')
        ? new Response('# 正文', { status: 200 })
        : jsonResponse(200, { copyId: 5, localArticleId: 1000, title: '新标题', localLogicalPath: 'x/y.md' })),
    async (calls) => {
      const markdown = await coursewareGateway.previewArticle(7, 3)
      assert.equal(markdown, '# 正文')
      assert.match(calls[0].url, /\/resources\/articles\/7\/3\/preview$/)

      const renamed = await coursewareGateway.renameCopy(5, { title: '新标题', logicalPath: 'x/y.md' })
      assert.equal(renamed.title, '新标题')
      const patch = calls.find(call => call.options.method === 'PATCH')
      assert.deepEqual(JSON.parse(patch.options.body), { title: '新标题', logicalPath: 'x/y.md' })
    }
  )
})

test('提醒与更新接口', async () => {
  await withFetch(
    (url) => {
      if (url.includes('/csrf')) return jsonResponse(200, { token: 'test-token' })
      if (url.includes('/notifications/read')) return jsonResponse(200, { updated: 3, unreadCount: 0 })
      if (url.includes('/notifications')) {
        return jsonResponse(200, {
          items: [{ id: 9, kind: 'source_updated', title: '来源课件有更新', body: 'x', read: false }],
          unreadCount: 1
        })
      }
      return jsonResponse(200, {
        updatedCount: 1,
        addedCount: 0,
        removedCount: 0,
        updated: [{ copyId: 5, sourceArticleId: 3, title: '原电池' }],
        added: [],
        removed: []
      })
    },
    async (calls) => {
      const notifications = await coursewareGateway.listNotifications(true)
      assert.equal(notifications.unreadCount, 1)
      assert.equal(notifications.items[0].kind, 'source_updated')
      assert.match(calls[0].url, /unreadOnly=true$/)

      const read = await coursewareGateway.markNotificationsRead([])
      assert.equal(read.unreadCount, 0)
      const post = calls.find(call => call.options.method === 'POST')
      assert.deepEqual(JSON.parse(post.options.body), {})

      const digest = await coursewareGateway.getUpdates()
      assert.equal(digest.updatedCount, 1)
      assert.equal(digest.updated[0].copyId, 5)
    }
  )
})

test('共享树与我的获取关系走对应路径', async () => {
  await withFetch(
    (url) => (url.includes('/resources/tree')
      ? jsonResponse(200, [{ articleId: 3, title: '原电池', logicalPath: '高中化学/3.md', allowCopy: true }])
      : jsonResponse(200, [{ copyId: 1, localArticleId: 1000, sourceTeacherName: 'Amy' }])),
    async (calls) => {
      const tree = await coursewareGateway.listResourceTree(7, '高中化学')
      assert.equal(tree[0].articleId, 3)
      assert.match(calls[0].url, /teacherId=7&subject=/)

      const copies = await coursewareGateway.listCopies()
      assert.equal(copies[0].sourceTeacherName, 'Amy')
    }
  )
})
