import assert from 'node:assert/strict'
import test from 'node:test'

import {
  analyzeCourseware,
  buildQuestionTextMap,
  cleanStemText,
  resolveQuestionOrder,
  resolveQuestionText,
  renderMarkdownTable
} from '../src/utils/questionText.js'

test('显式题干标记按出现顺序拼接，并支持一处标记多处使用', () => {
    const markdown = [
        '# 模块 1',
        '',
        '【题干1】第一片段。',
        '',
        '【题干1】第二片段。',
        '',
        '【@1】'
    ].join('\n')

    assert.equal(resolveQuestionText(markdown, 1), '第一片段。\n第二片段。')
})

test('公共题干【题干N-M】被区间内每道题共享', () => {
    const markdown = [
        '# 大题',
        '',
        '【题干1-2】阅读材料回答问题：',
        '',
        '【题干1】（1）第一问 ______。',
        '',
        '【@1】',
        '',
        '【题干2】（2）第二问 ______。',
        '',
        '【@2】'
    ].join('\n')

    assert.equal(resolveQuestionText(markdown, 1), '阅读材料回答问题：\n（1）第一问 ______。')
    assert.equal(resolveQuestionText(markdown, 2), '阅读材料回答问题：\n（2）第二问 ______。')
})

test('题干与作答区分离（布局 B）时按编号取题干而不是答题区标签', () => {
    const markdown = [
        '# 模块 1',
        '',
        '【题干1】银氨溶液与乙醛反应的现象是（ ）',
        '',
        'A. 生成银镜',
        '',
        '---',
        '',
        '1.',
        '',
        '【@1】',
        '',
        '---'
    ].join('\n')

    assert.equal(resolveQuestionText(markdown, 1), '银氨溶液与乙醛反应的现象是（ ）\nA. 生成银镜')
})

test('英语精读的引用块题干去掉 > 前缀', () => {
    const markdown = [
        '# Libraries at your service',
        '',
        'Of course. I chose to study library science for the same reason as you.',
        '',
        '> 【题干1】找出上面这一段所有的定语从句，指出分别是关系代词引导的还是关系副词引导的。',
        '>',
        '> 【@1】'
    ].join('\n')

    assert.equal(
        resolveQuestionText(markdown, 1),
        '找出上面这一段所有的定语从句，指出分别是关系代词引导的还是关系副词引导的。'
    )
})

test('旧课件的紧邻题目回退：题干 + 选项都在作答占位符上方时整体取回', () => {
    const markdown = [
        '# 阅读A',
        '',
        'Some passage text that is not part of the question.',
        '',
        '---',
        '',
        '21. What are participants in Walk 3 required to do?',
        '',
        '\tA. Get special permits.',
        '',
        '\tB. Wear proper clothes.',
        '',
        '【@】'
    ].join('\n')

    const [question] = analyzeCourseware(markdown).questions
    assert.equal(question.source, 'nearby')
    assert.match(question.stem, /^21\. What are participants/)
    assert.match(question.stem, /B\. Wear proper clothes\.$/)
})

test('无法定位题干时回退为「章节标题 · 第N题」，不会返回正文段落', () => {
    const markdown = [
        '# 语法填空',
        '',
        'A long passage that should never become the stem of a single blank.',
        '',
        '---',
        '',
        '11. 【@】'
    ].join('\n')

    const [question] = analyzeCourseware(markdown).questions
    assert.equal(question.source, 'fallback')
    assert.equal(question.stem, '语法填空 · 第1题')
})

test('自动编号按出现顺序分配，并与显式编号互不冲突', () => {
    const markdown = ['【题干1】一', '【@】', '【题干3】三', '【@3】', '【@】'].join('\n')

    const { questions } = analyzeCourseware(markdown)
    assert.deepEqual(questions.map(q => q.id), ['1', '3', '2'])
    assert.deepEqual(questions.map(q => q.order), [1, 2, 3])
    assert.equal(resolveQuestionOrder(markdown, 3), 2)
    assert.equal(resolveQuestionOrder(markdown, 99), null)
})

test('题干映射与单题解析结果一致，且不存在的题号返回空串', () => {
    const markdown = ['【题干1】第一题', '【@1】', '【题干2】第二题', '【@2】'].join('\n')

    const map = buildQuestionTextMap(markdown)
    assert.equal(map.get('1'), '第一题')
    assert.equal(map.get('2'), '第二题')
    assert.equal(resolveQuestionText(markdown, 7), '')
})

test('cleanStemText 去掉标题、分隔线、加粗与引用前缀', () => {
    const text = ['> **加粗**内容', '>', '## 小标题', '---', '`代码`'].join('\n')
    assert.equal(cleanStemText(text), '加粗内容\n代码')
})

test('超长题干按行截断，且不把 $...$ 公式切成两半', () => {
    const long = Array.from({ length: 60 }, (_, i) => `第 ${i} 行内容 $x_{${i}}$`).join('\n')
    const markdown = [`【题干1】${long}`, '【@1】'].join('\n')

    const stem = resolveQuestionText(markdown, 1)
    assert.ok(stem.endsWith('…'))
    assert.equal((stem.match(/\$/g) || []).length % 2, 0)
})

test('题干中的表格渲染为 <table> 且转义单元格内容', () => {
    const text = ['| 元素 | 第一电离能 |', '| :---: | :---: |', '| Cu <x> | $745.5$ |'].join('\n')
    const html = renderMarkdownTable(text)

    assert.match(html, /^<table><tr><th>元素<\/th><th>第一电离能<\/th><\/tr>/)
    assert.match(html, /<td>Cu &lt;x&gt;<\/td>/)
})
