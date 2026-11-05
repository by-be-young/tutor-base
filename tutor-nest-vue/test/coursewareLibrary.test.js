// test/coursewareLibrary.test.js
// 静态讲义兜底路径：清单里的 logicalPath 优先，缺失时按编号回查 articles.json。
import test from 'node:test'
import assert from 'node:assert/strict'

import { enrichWithStaticIndex, staticPathFor } from '../src/utils/coursewareLibrary.js'

const INDEX = [
  { id: 45, title: '口语训练-1', series: '高中英语', path: '高中英语/口语训练/口语训练-1.md' }
]

test('staticPathFor 优先用清单给的 logicalPath', () => {
  assert.equal(
    staticPathFor({ articleId: 45, logicalPath: '高中英语/x.md' }, INDEX),
    '高中英语/x.md'
  )
})

test('staticPathFor 在缺少 logicalPath 时按 articleId / legacyArticleId 回查静态目录', () => {
  assert.equal(
    staticPathFor({ articleId: 45, logicalPath: null }, INDEX),
    '高中英语/口语训练/口语训练-1.md'
  )
  assert.equal(
    staticPathFor({ articleId: 1045, legacyArticleId: 45 }, INDEX),
    '高中英语/口语训练/口语训练-1.md'
  )
})

test('staticPathFor 查不到时返回空串，由调用方决定怎么提示', () => {
  assert.equal(staticPathFor({ articleId: 999 }, INDEX), '')
  assert.equal(staticPathFor(null, INDEX), '')
})

test('enrichWithStaticIndex 只补静态课件，不动已托管篇目', () => {
  const enriched = enrichWithStaticIndex(
    [
      { articleId: 45, title: '', hosted: false },
      { articleId: 45, title: '已托管', logicalPath: '已托管.md', hosted: true }
    ],
    INDEX
  )
  assert.equal(enriched[0].title, '口语训练-1')
  assert.equal(enriched[0].logicalPath, '高中英语/口语训练/口语训练-1.md')
  assert.equal(enriched[1].title, '已托管')
  assert.equal(enriched[1].logicalPath, '已托管.md')
})
