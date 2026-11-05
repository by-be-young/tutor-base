// test/coursewareTree.test.js
// 「我的课件」目录：logicalPath 要还原成多级文件夹，状态文案与选中标记要稳定。
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  articleStatus,
  buildCoursewareTree,
  flattenTree,
  markActiveArticle,
  pathSegments
} from '../src/utils/coursewareTree.js'

test('logicalPath 拆成目录段并去掉扩展名', () => {
  assert.deepEqual(pathSegments({ logicalPath: '高中英语/口语训练/口语训练-1.md' }), ['高中英语', '口语训练', '口语训练-1'])
  assert.deepEqual(pathSegments({ logicalPath: '高中英语\\语法\\1-1.MD' }), ['高中英语', '语法', '1-1'])
  assert.deepEqual(pathSegments({}), [])
})

test('按 logicalPath 生成多级目录，去掉重复的科目段', () => {
  const tree = buildCoursewareTree('高中英语', [
    { articleId: 1, title: '口语训练-1', logicalPath: '高中英语/口语训练/口语训练-1.md', published: true, hasDraft: false },
    { articleId: 2, title: '音标基础', logicalPath: '高中英语/音标/音标基础.md', published: true, hasDraft: true }
  ])

  assert.equal(tree.name, '高中英语')
  assert.equal(tree.isFile, false)
  const [first, second] = tree.children
  assert.equal(first.isFile, false)
  assert.equal(first.name, '口语训练')
  assert.equal(second.name, '音标')
  assert.deepEqual(flattenTree(tree).map(node => node.blogId), [1, 2])
})

test('没有 logicalPath 的草稿直接挂在科目根下，并沿用标题', () => {
  const tree = buildCoursewareTree('高中化学', [
    { articleId: 1001, title: '新练习', logicalPath: null, published: false, hasDraft: true }
  ])
  const files = flattenTree(tree)
  assert.equal(files.length, 1)
  assert.equal(files[0].name, '新练习')
  assert.equal(files[0].meta, '草稿 · 未发布')
})

test('同一目录里文件夹排在文件前面，并按中文排序', () => {
  const tree = buildCoursewareTree('高中英语', [
    { articleId: 10, title: 'b 篇', logicalPath: '高中英语/b.md', published: true },
    { articleId: 11, title: 'a 篇', logicalPath: '高中英语/a.md', published: true },
    { articleId: 12, title: '子目录里的篇', logicalPath: '高中英语/子目录/x.md', published: true }
  ])
  assert.deepEqual(tree.children.map(node => node.name), ['子目录', 'a 篇', 'b 篇'])
})

test('状态文案区分已发布、草稿、静态讲义与正文缺失', () => {
  assert.equal(articleStatus({ published: true, hasDraft: false }).label, '已发布')
  assert.equal(articleStatus({ published: true, hasDraft: true }).tone, 'draft')
  assert.equal(articleStatus({ published: false, hasDraft: true }).label, '草稿 · 未发布')
  assert.equal(articleStatus({ hosted: false, hasDraft: false }).label, '静态讲义 · 已上线')
  assert.equal(articleStatus({ hosted: false, hasDraft: true }).label, '静态讲义 · 有草稿')
  assert.deepEqual(articleStatus({ published: true, contentMissing: true }), {
    label: '正文缺失 · 需重新发布',
    tone: 'missing'
  })
})

test('选中篇目在树里被标记，其他篇目不受影响', () => {
  const tree = buildCoursewareTree('高中英语', [
    { articleId: 1, title: '一', logicalPath: '高中英语/一.md', published: true },
    { articleId: 2, title: '二', logicalPath: '高中英语/二.md', published: true }
  ])
  markActiveArticle(tree, 2)
  const files = flattenTree(tree)
  assert.equal(files.find(node => node.blogId === 1).active, false)
  assert.equal(files.find(node => node.blogId === 2).active, true)
})
