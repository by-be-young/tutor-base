// src/utils/questionText.js
// 课件（public/articles/**/*.md）题目解析工具：把课件原文解析成「题目 → 题干」映射。
// 规范见 tutor-nest-vue/docs/文章格式规范.md，一致性由 scripts/check-courseware.mjs 守护。
//
// 占位符语法（全角，编号前后允许空格）：
//   作答占位符：【@】自动编号 / 【@N】显式编号
//   题干占位符：【题干N】单题 / 【题干N-M】区间内每题共享的公共题干

export const ANSWER_TOKEN = /【@(\d*)】/g
export const STEM_TOKEN = /【题干\s*(\d+)(?:\s*-\s*(\d+))?\s*】/g
export const STEM_TOKEN_ONE = /【题干\s*(\d+)(?:\s*-\s*(\d+))?\s*】/

/** 题干片段超过该长度时截断（错题本卡片容量） */
const STEM_LIMIT = 400

const QUOTE_PREFIX = /^\s{0,3}>\s?/
/** 旧课件的「题目起始行」：引用块内的有序/无序列表项 */
const QUESTION_LINE = /^\s{0,3}(?:>\s*)?(?:[-*+]\s+\S|\d+\s*[.、．]\s*\S)/

/** 去掉引用块前缀（> ），使块引用内的题干与正文题干解析结果一致 */
export function stripQuoteMarks(text) {
    return String(text ?? '')
        .split('\n')
        .map(line => line.replace(QUOTE_PREFIX, ''))
        .join('\n')
}

/** 清理题干片段中的 markdown 语法、引用前缀、标题行、分隔线与空行 */
export function cleanStemText(text) {
    return stripQuoteMarks(text)
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/`(.+?)`/g, '$1')
        .split('\n')
        .filter(line => {
            const t = line.trim()
            if (!t) return false
            if (/^#{1,6}\s/.test(t)) return false // 排除标题行
            if (/^---\s*$/.test(t)) return false  // 排除分隔线
            return true
        })
        .join('\n')
        .trim()
}

/** 查找某个位置之前最近的一级标题（# 章节标题），找不到返回「本文」 */
export function nearestH1(markdown, pos) {
    const lines = markdown.slice(0, pos).split('\n')
    for (let i = lines.length - 1; i >= 0; i--) {
        const m = lines[i]?.match(/^#\s+(.*)$/)
        if (m) {
            return m[1].replace(/\*\*(.+?)\*\*/g, '$1').trim() || '本文'
        }
    }
    return '本文'
}

/**
 * 扫描课件中的全部占位符（题干标记 + 作答标记）
 * 作答占位符：
 *   id —— 存储用编号（显式编号原样；无编号按出现顺序自动分配，跳过已用编号）
 *   order —— 显示用顺序号（从 1 开始按出现顺序递增，与实际 id 无关）
 * 题干占位符：
 *   ids —— 该标记覆盖的题号（【题干N】为 [N]，【题干N-M】为 N..M）
 *
 * @param {string} markdown 课件原文
 * @returns {Array<{type:'stem'|'answer', ids?:string[], id?:string, order?:number, index:number, end:number}>}
 */
export function collectTokens(markdown) {
    const source = String(markdown ?? '')
    const tokens = []
    for (const m of source.matchAll(STEM_TOKEN)) {
        const start = Number(m[1])
        const end = m[2] ? Number(m[2]) : start
        const ids = []
        for (let n = start; n <= end; n++) ids.push(String(n))
        tokens.push({ type: 'stem', ids, index: m.index, end: m.index + m[0].length })
    }
    let autoCounter = 1
    let order = 0
    const used = new Set()
    for (const m of source.matchAll(ANSWER_TOKEN)) {
        order++
        let id
        if (m[1] !== '') {
            id = m[1]
            used.add(Number(m[1]))
        } else {
            while (used.has(autoCounter)) autoCounter++
            id = String(autoCounter)
            used.add(autoCounter)
            autoCounter++
        }
        tokens.push({ type: 'answer', id, order, index: m.index, end: m.index + m[0].length })
    }
    tokens.sort((a, b) => a.index - b.index)
    return tokens
}

/** 按行边界与公式边界截断题干，避免把 $...$ 切成两半 */
function truncateStem(text, limit = STEM_LIMIT) {
    if (text.length <= limit) return text
    const cut = text.slice(0, limit)
    const lineCut = cut.lastIndexOf('\n')
    let body = lineCut > limit * 0.6 ? cut.slice(0, lineCut) : cut
    if ((body.match(/\$/g) || []).length % 2 === 1) {
        const lastFormula = body.lastIndexOf('$')
        if (lastFormula > limit * 0.5) body = body.slice(0, lastFormula)
    }
    return `${body.trimEnd()}…`
}

/**
 * 旧课件的紧邻题干回退：从作答占位符往前找最近的「题目起始行」，
 * 把它到作答占位符之间的内容当作题干（保留选项行）。
 * 遇到标题或分隔线说明题目与作答分属不同栏，不做猜测；题干过短时同样返回空串。
 */
function extractAdjacentQuestion(markdown, tokens, pos) {
    let prevEnd = 0
    for (const token of tokens) {
        if (token.index >= pos) break
        prevEnd = token.end
    }
    const lines = markdown.slice(prevEnd, pos).split('\n')
    let last = lines.length - 1
    while (last >= 0 && !lines[last].trim()) last--
    if (last < 0) return ''

    for (let i = last; i >= 0; i--) {
        const trimmed = lines[i].trim()
        if (!trimmed) continue
        if (trimmed === '---' || /^#{1,6}\s/.test(trimmed)) return ''
        if (QUESTION_LINE.test(lines[i])) {
            return cleanStemText(lines.slice(i, last + 1).join('\n'))
        }
    }
    return ''
}

/** 题干片段不跨过分隔线：分隔线之后是作答区，答题区里的题号标签不属于题干 */
function cutAtSeparator(text) {
    const index = text.search(/^---\s*$/m)
    return index >= 0 ? text.slice(0, index) : text
}

/**
 * 解析课件中的全部题目
 *
 * 题干来源按优先级：
 *   1. 显式标记【题干N】/【题干N-M】→ 全部片段按出现顺序拼接
 *   2. 作答占位符上方紧邻的题目段落（旧课件回退）
 *   3. 「最近一级章节标题 · 第N题」
 *
 * @param {string} markdown 课件原文
 * @returns {{questions: Array<{id:string, order:number, stem:string, source:'stem'|'nearby'|'fallback'}>}}
 */
export function analyzeCourseware(markdown) {
    const source = String(markdown ?? '')
    const tokens = collectTokens(source)

    const stemFragments = new Map()
    for (let i = 0; i < tokens.length; i++) {
        const token = tokens[i]
        if (token.type !== 'stem') continue
        const next = tokens[i + 1]
        const end = next ? next.index : source.length
        const fragment = cleanStemText(cutAtSeparator(source.slice(token.end, end)))
        if (!fragment) continue
        token.ids.forEach(id => {
            if (!stemFragments.has(id)) stemFragments.set(id, [])
            stemFragments.get(id).push(fragment)
        })
    }

    const questions = tokens
        .filter(token => token.type === 'answer')
        .map(token => {
            const fragments = stemFragments.get(token.id)
            if (fragments && fragments.length) {
                return {
                    id: token.id,
                    order: token.order,
                    stem: truncateStem(fragments.join('\n')),
                    source: 'stem'
                }
            }
            const nearby = extractAdjacentQuestion(source, tokens, token.index)
            if (nearby.length >= 5) {
                return { id: token.id, order: token.order, stem: truncateStem(nearby), source: 'nearby' }
            }
            return {
                id: token.id,
                order: token.order,
                stem: `${nearestH1(source, token.index)} · 第${token.order}题`,
                source: 'fallback'
            }
        })

    return { questions }
}

/**
 * 查询某道题的显示顺序号（第几个作答占位符，从 1 开始）
 * 课件中没有该题时返回 null
 *
 * @param {string} markdown 课件原文
 * @param {string|number} questionId 存储用题目编号
 * @returns {number|null}
 */
export function resolveQuestionOrder(markdown, questionId) {
    const key = String(questionId)
    const answer = collectTokens(markdown).find(t => t.type === 'answer' && t.id === key)
    return answer ? answer.order : null
}

/**
 * 解析某道题的题干（错题本 / 错题训练动态获取用）
 *
 * @param {string} markdown 课件原文
 * @param {string|number} questionId 题目编号
 * @returns {string} 题干；课件中没有该题时返回空串
 */
export function resolveQuestionText(markdown, questionId) {
    const key = String(questionId)
    const question = analyzeCourseware(markdown).questions.find(q => q.id === key)
    return question ? question.stem : ''
}

/**
 * 构建「题目编号 → 题干」映射（课件详情页与错题本共用同一套解析结果）
 *
 * @param {string} markdown 课件原文
 * @returns {Map<string, string>}
 */
export function buildQuestionTextMap(markdown) {
    const map = new Map()
    analyzeCourseware(markdown).questions.forEach(question => map.set(question.id, question.stem))
    return map
}

// ========== Markdown 表格渲染（错题本/错题训练等简版渲染器用） ==========

/** 拆分表格行单元格（去掉首尾管道，逐个去空格） */
function splitTableCells(row) {
    return String(row ?? '')
        .trim()
        .replace(/^\|/, '')
        .replace(/\|$/, '')
        .split('|')
        .map(c => c.trim())
}

/** 表格单元格 HTML 转义（保留 $...$ 公式与 ![[图片]] 语法，后续由 KaTeX / 图片解析处理） */
function escapeTableCell(text) {
    return String(text ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
}

/**
 * 将 GFM 风格管道表格（| ... | 表头 + 分隔行 + 数据行）转换为 <table> HTML。
 * 表格整体合成一行，避免后续「换行转 <br>」破坏结构；
 * 单元格已转义，可安全注入 v-html。
 * 识别规则：以 | 开头且以 | 结尾的行；分隔行形如 | :---: |（含连字符）。
 *
 * @param {string} text 题干文本
 * @returns {string} 表格已转为 HTML 的文本
 */
export function renderMarkdownTable(text) {
    const lines = String(text ?? '').split('\n')
    const out = []
    let i = 0

    const isTableRow = (line) => /^\|.*\|\s*$/.test(String(line ?? '').trim())
    const isSeparator = (line) => /^\|[\s:|-]*\|\s*$/.test(String(line ?? '').trim()) && /-/.test(line ?? '')

    while (i < lines.length) {
        // 表头行 + 紧随的分隔行 → 识别为表格块
        if (isTableRow(lines[i]) && isSeparator(lines[i + 1])) {
            const headers = splitTableCells(lines[i])
            i += 2
            const rows = []
            // 数据行；下一行又是分隔行说明是下一个表格的表头，停止收集
            while (i < lines.length && isTableRow(lines[i]) && !isSeparator(lines[i + 1])) {
                rows.push(splitTableCells(lines[i]))
                i++
            }
            const thead = `<tr>${headers.map(c => `<th>${escapeTableCell(c)}</th>`).join('')}</tr>`
            const tbody = rows.length
                ? `<tbody>${rows.map(r => `<tr>${r.map(c => `<td>${escapeTableCell(c)}</td>`).join('')}</tr>`).join('')}</tbody>`
                : ''
            out.push(`<table>${thead}${tbody}</table>`)
        } else {
            out.push(lines[i])
            i++
        }
    }

    return out.join('\n')
}
