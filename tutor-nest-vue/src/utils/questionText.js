// src/utils/questionText.js
// 题干占位符解析工具（与 ArticleDetailView 的解析规则保持一致）
// 占位符语法：
//   答题占位符：【@】自动编号 / 【@N】显式编号
//   题干占位符：【题干N】/【题干N-M】（编号前后允许空格）

export const ANSWER_TOKEN = /【@(\d*)】/g
export const STEM_TOKEN = /【题干\s*(\d+)(?:\s*-\s*(\d+))?\s*】/g
export const STEM_TOKEN_ONE = /【题干\s*(\d+)(?:\s*-\s*(\d+))?\s*】/

/** 清理题干片段中的 markdown 语法、标题行、分隔线与空行 */
export function cleanStemText(text) {
    return text
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
 * 扫描文章中的全部占位符（题干标记 + 答题标记）
 * 答题占位符：
 *   id —— 存储用编号（显式编号原样；无编号按出现顺序自动分配，跳过已用编号）
 *   order —— 显示用顺序号（从 1 开始按出现顺序递增，与实际 id 无关）
 * @returns {Array<{type:'stem'|'answer', ids?:string[], id?:string, order?:number, index:number, end:number}>}
 */
function scanTokens(markdown) {
    const tokens = []
    for (const m of markdown.matchAll(STEM_TOKEN)) {
        const start = Number(m[1])
        const end = m[2] ? Number(m[2]) : start
        const ids = []
        for (let n = start; n <= end; n++) ids.push(String(n))
        tokens.push({ type: 'stem', ids, index: m.index, end: m.index + m[0].length })
    }
    let autoCounter = 1
    let order = 0
    const used = new Set()
    for (const m of markdown.matchAll(ANSWER_TOKEN)) {
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

/**
 * 查询某道题的显示顺序号（第几个答题占位符，从 1 开始）
 * 文章中没有该题时返回 null
 *
 * @param {string} markdown 文章原文
 * @param {string|number} questionId 存储用题目编号
 * @returns {number|null}
 */
export function resolveQuestionOrder(markdown, questionId) {
    const key = String(questionId)
    const tokens = scanTokens(markdown)
    const answer = tokens.find(t => t.type === 'answer' && t.id === key)
    return answer ? answer.order : null
}

/**
 * 启发式提取：取答题占位符上方紧邻的内容作为题干
 * （上一个占位符之后、遇到最近标题或分隔线为止，保留题目与选项行）
 * 适用于未写【题干N】标记的旧文章；提取内容过短（如纯题号）时返回空
 */
function extractNearbyText(markdown, tokens, pos) {
    let prevEnd = 0
    for (const t of tokens) {
        if (t.index >= pos) break
        prevEnd = t.end
    }
    const lines = markdown.slice(prevEnd, pos).split('\n')

    // 从后往前扫描，遇到标题或分隔线则停止（题目内容通常紧邻占位符）
    let startIdx = 0
    for (let i = lines.length - 1; i >= 0; i--) {
        if (/^#{1,6}\s/.test(lines[i]) || /^---\s*$/.test(lines[i].trim())) {
            startIdx = i + 1
            break
        }
    }

    const text = cleanStemText(lines.slice(startIdx).join('\n'))
    return text.length >= 5 ? text : ''
}

/**
 * 解析某道题的题干（错题本动态获取用）
 * 优先级：
 *   1. 写了【题干N】/【题干N-M】 → 全部片段按出现顺序拼接（截断 400 字）
 *   2. 未写标记 → 启发式提取答题占位符上方的紧邻文本
 *   3. 仍无内容 → 回退「最近一级章节标题 · 第N题」
 *
 * @param {string} markdown 文章原文
 * @param {string|number} questionId 题目编号
 * @returns {string}
 */
export function resolveQuestionText(markdown, questionId) {
    const key = String(questionId)
    const tokens = scanTokens(markdown)

    const fragments = []
    for (let i = 0; i < tokens.length; i++) {
        const t = tokens[i]
        if (t.type !== 'stem' || !t.ids.includes(key)) continue
        const next = tokens[i + 1]
        const end = next ? next.index : markdown.length
        const fragment = cleanStemText(markdown.slice(t.end, end))
        if (fragment) fragments.push(fragment)
    }

    if (fragments.length) {
        const text = fragments.join('\n')
        return text.length > 400 ? text.slice(0, 400) + '…' : text
    }

    const answer = tokens.find(t => t.type === 'answer' && t.id === key)
    if (answer) {
        const nearby = extractNearbyText(markdown, tokens, answer.index)
        if (nearby) return nearby
    }

    // 回退：显示顺序号（文章中没有该题时用原编号）
    const display = answer ? answer.order : key
    return `${nearestH1(markdown, answer?.index ?? 0)} · 第${display}题`
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
