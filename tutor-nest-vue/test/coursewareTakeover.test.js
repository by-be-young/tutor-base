// test/coursewareTakeover.test.js
// 静态课件接管：只挑还没托管的篇目，按原编号写草稿，再按科目整科发布。
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  pendingStaticArticles,
  takeOverStaticCourseware,
  takeoverBySubject
} from '../src/utils/coursewareTakeover.js'

function article(overrides) {
  return { articleId: 1, legacyArticleId: 1, subject: '高中英语', hosted: false, hasDraft: false, ...overrides }
}

test('只挑仍是静态讲义、且没有草稿的篇目', () => {
  const list = [
    article({ articleId: 1 }),
    article({ articleId: 2, hasDraft: true }),
    article({ articleId: 3, hosted: true, published: true }),
    article({ articleId: 4, legacyArticleId: null })
  ]
  assert.deepEqual(pendingStaticArticles(list).map(item => item.articleId), [1, 4])
})

test('按科目统计待接管篇数', () => {
  const groups = takeoverBySubject([
    article({ articleId: 1, subject: '高中英语' }),
    article({ articleId: 2, subject: '高中英语' }),
    article({ articleId: 3, subject: '高中化学' })
  ])
  assert.deepEqual(groups, [
    { subject: '高中英语', count: 2 },
    { subject: '高中化学', count: 1 }
  ])
})

test('按原编号写草稿，并按科目各发布一次', async () => {
  const saved = []
  const published = []
  const result = await takeOverStaticCourseware(
    [
      article({ articleId: 45, legacyArticleId: 45, subject: '高中英语' }),
      article({ articleId: 46, legacyArticleId: 46, subject: '高中英语' }),
      article({ articleId: 900, legacyArticleId: null, subject: '高中化学' })
    ],
    {
      loadMarkdown: async item => `# 课件 ${item.articleId}`,
      saveDraft: async (articleId, markdown, legacyArticleId) => {
        saved.push({ articleId, markdown, legacyArticleId })
      },
      publish: async (subject) => {
        published.push(subject)
        return { version: published.length, articleCount: 1 }
      }
    }
  )

  assert.equal(result.total, 3)
  assert.equal(result.saved.length, 3)
  assert.deepEqual(saved.map(item => item.articleId), [45, 46, 900])
  // legacyArticleId 缺失时退回用 articleId，保证学员旧编号仍能命中
  assert.deepEqual(saved.map(item => item.legacyArticleId), [45, 46, 900])
  assert.deepEqual(published, ['高中英语', '高中化学'])
  assert.equal(result.published.length, 2)
  assert.equal(result.failed.length, 0)
  assert.equal(result.errors.length, 0)
})

test('取不到静态正文的篇目跳过，单篇失败不影响其它篇目', async () => {
  const result = await takeOverStaticCourseware(
    [article({ articleId: 1 }), article({ articleId: 2 }), article({ articleId: 3 })],
    {
      loadMarkdown: async (item) => {
        if (item.articleId === 2) return null
        if (item.articleId === 3) throw new Error('网络异常')
        return '# ok'
      },
      saveDraft: async () => {},
      publish: async () => ({ version: 1 })
    }
  )

  assert.deepEqual(result.saved.map(item => item.articleId), [1])
  assert.deepEqual(result.skipped.map(item => item.articleId), [2])
  assert.equal(result.failed.length, 1)
  assert.equal(result.failed[0].article.articleId, 3)
  assert.deepEqual(result.published.map(item => item.subject), ['高中英语'])
})

test('某个科目发布失败时记录下来，其余科目继续', async () => {
  const result = await takeOverStaticCourseware(
    [article({ articleId: 1, subject: '高中英语' }), article({ articleId: 2, subject: '高中化学' })],
    {
      loadMarkdown: async () => '# 正文',
      saveDraft: async () => {},
      publish: async (subject) => {
        if (subject === '高中英语') throw new Error('版本冲突')
        return { version: 2 }
      }
    }
  )

  assert.equal(result.saved.length, 2)
  assert.deepEqual(result.published.map(item => item.subject), ['高中化学'])
  assert.equal(result.errors.length, 1)
  assert.equal(result.errors[0].subject, '高中英语')
})

test('没有待接管篇目时直接返回空结果，不调用任何接口', async () => {
  let called = false
  const result = await takeOverStaticCourseware([article({ hosted: true, published: true })], {
    loadMarkdown: async () => { called = true; return '# x' },
    saveDraft: async () => { called = true },
    publish: async () => { called = true }
  })
  assert.equal(result.total, 0)
  assert.equal(called, false)
})
