// src/utils/questionText.js
// 题干占位符解析工具（与 BlogDetailView 的解析规则保持一致）
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
 * 答题占位符的 id 已解析自动编号（无编号按出现顺序从 1 开始，跳过已用编号）
 * @returns {Array<{type:'stem'|'answer', ids?:string[], id?:string, index:number, end:number}>}
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
    const used = new Set()
    for (const m of markdown.matchAll(ANSWER_TOKEN)) {
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
        tokens.push({ type: 'answer', id, index: m.index, end: m.index + m[0].length })
    }
    tokens.sort((a, b) => a.index - b.index)
    return tokens
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

    return `${nearestH1(markdown, answer?.index ?? 0)} · 第${key}题`
}

