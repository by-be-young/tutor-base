import assert from 'node:assert/strict'
import test from 'node:test'
import {
  sectionAtCursor,
  subjectSlug,
  suggestedImageName
} from '../src/utils/coursewareMediaName.js'

test('科目转换成可读 ASCII 前缀', () => {
  assert.equal(subjectSlug('高中化学'), 'chemistry')
  assert.equal(subjectSlug('初中英语'), 'english-junior')
  assert.equal(subjectSlug('AP Chemistry'), 'ap-chemistry')
  assert.equal(subjectSlug(''), 'courseware')
})

test('按光标位置计算章节与本节图片序号', () => {
  const source = '# 2.3 氧化还原\n\n![[chemistry-2-3-fig1.png]]\n\n正文\n\n# 3.1 原电池\n\n光标'
  const position = sectionAtCursor(source, source.indexOf('光标'))
  assert.equal(position.section, 2)
  assert.equal(position.number, '3.1')
  assert.equal(position.figure, 1)
})

test('章节有编号时用编号，没有时用 s+序号', () => {
  const withNumber = '# 2.3 氧化还原\n\n正文'
  assert.equal(
    suggestedImageName(withNumber, withNumber.length, '高中化学', 'png'),
    'chemistry-2-3-fig1.png'
  )
  const withoutNumber = '# 氧化还原\n\n正文'
  assert.equal(
    suggestedImageName(withoutNumber, withoutNumber.length, '高中化学', 'jpg'),
    'chemistry-s1-fig1.jpg'
  )
})
