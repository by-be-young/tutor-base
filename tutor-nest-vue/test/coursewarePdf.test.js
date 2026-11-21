import assert from 'node:assert/strict'
import test from 'node:test'

import { PDF_RENDER_WIDTH, buildPdfFilename, formatFileSize } from '../src/utils/coursewarePdf.js'

test('PDF 文件名清理非法字符并补扩展名', () => {
  assert.equal(buildPdfFilename('化学/平衡:移动*原理?'), '化学-平衡-移动-原理.pdf')
  assert.equal(buildPdfFilename('   '), '课件.pdf')
  assert.equal(buildPdfFilename(''), '课件.pdf')
})

test('PDF 文件名限制长度', () => {
  const filename = buildPdfFilename('课'.repeat(120))
  assert.equal(filename.length, 84)
  assert.ok(filename.endsWith('.pdf'))
})

test('PDF 体积展示为 KB / MB', () => {
  assert.equal(formatFileSize(0), '0 KB')
  assert.equal(formatFileSize(900), '1 KB')
  assert.equal(formatFileSize(820 * 1024), '820 KB')
  assert.equal(formatFileSize(2.4 * 1024 * 1024), '2.4 MB')
})

test('A4 内容渲染宽度固定，避免随手机屏幕缩窄', () => {
  assert.equal(PDF_RENDER_WIDTH, 682)
})
