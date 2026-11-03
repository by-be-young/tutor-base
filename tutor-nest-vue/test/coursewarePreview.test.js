// test/coursewarePreview.test.js
// 编辑器预览必须把 Markdown 渲染成站点正文的样子，而不是显示原始文本。
import test from 'node:test'
import assert from 'node:assert/strict'

import {
  renderMarkdownFragment,
  renderCoursewarePreview,
  splitByLayout,
  splitColumns
} from '../src/utils/coursewarePreview.js'

test('标题、表格、加粗都渲染成 HTML，不残留原始 Markdown 记号', () => {
  const html = renderMarkdownFragment('# 一、重点词汇\n\n| 字母组合 | 例词 |\n| :--- | :--- |\n| ea | team |\n\n加粗 **team building**。')
  assert.match(html, /<h1[^>]*>一、重点词汇<\/h1>/)
  assert.match(html, /<table/)
  assert.match(html, /<th[^>]*>字母组合<\/th>/)
  assert.match(html, /<strong>team building<\/strong>/)
  assert.doesNotMatch(html, /\| :---/)
  assert.doesNotMatch(html, /# 一、重点词汇/)
})

test('布局指令变成标签，题干与作答占位符变成色块', () => {
  const sections = splitByLayout('<!-- @layout:A -->\n# 阅读\n\n【题干1】原文\n\n【@1】')
  assert.equal(sections.length, 1)
  assert.equal(sections[0].layout, 'A')

  const html = renderCoursewarePreview('<!-- @layout:A -->\n# 阅读\n\n【题干1】原文\n\n【@1】')
  assert.match(html, /class="layout">布局 A/)
  assert.match(html, /class="preview-stem">题干1/)
  assert.match(html, /class="slot">答题卡1/)
  assert.doesNotMatch(html, /【题干1】/)
})

test('章节里独占一行的 --- 切成左右两栏', () => {
  const columns = splitColumns(['# 讲解', '正文段落', '---', '答题区', '---', '回到左栏'])
  assert.equal(columns.main, '# 讲解\n正文段落\n回到左栏')
  assert.equal(columns.side, '答题区')

  const html = renderCoursewarePreview('# 讲解\n正文段落\n---\n答题区\n')
  assert.match(html, /class="preview-columns"/)
  assert.match(html, /class="preview-side"/)
})

test('空文档不产出内容，界面据此显示占位提示', () => {
  assert.equal(renderCoursewarePreview('   \n'), '')
})
