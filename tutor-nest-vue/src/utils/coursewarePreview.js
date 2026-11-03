// src/utils/coursewarePreview.js
// 在线编辑器预览：把课件 Markdown 按站点真实渲染方式转成 HTML。
//
// 站点正文用的是 marked（见 ArticleDetailView 的 renderMarkdown），并且：
// - `# ` 起一个新章节；
// - 章节内第一个独占一行的 `---` 之后是右栏（答题区），第二个 `---` 回到左栏；
// - `<!-- @layout:X -->` 是布局指令，学员端由服务端换成布局卡片；
// - `【题干N】` / `【@N】` 是题干与作答占位符。
// 预览按同样的切分规则渲染，占位符用浅色块示意，避免出现原始 Markdown 文本。
import { marked } from 'marked'

function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/** 数学片段里的下划线会被 marked 当成强调，先转义，和正文页保持一致。 */
function escapeUnderscoresInMath(markdown) {
  return markdown.replace(/(\$\$[^$]*\$\$|\$[^$]*\$)/g, match => match.replace(/_/g, '\\_'))
}

/** 单个片段渲染：marked + 占位符示意。 */
export function renderMarkdownFragment(markdown) {
  const prepared = escapeUnderscoresInMath(String(markdown ?? ''))
    .replace(/【题干\s*(\d+)\s*】/g, '<mark class="preview-stem">题干$1</mark>')
    .replace(/【@(\d*)】/g, '<span class="slot">答题卡$1</span>')
  return marked.parse(prepared)
}

/** 按 `<!-- @layout:X -->` 切段。 */
export function splitByLayout(markdown) {
  const sections = []
  let current = { layout: null, lines: [] }
  for (const line of String(markdown ?? '').split('\n')) {
    const directive = line.match(/^<!--\s*@layout:\s*([A-Za-z0-9_-]+)\s*-->/)
    if (directive) {
      if (current.lines.some(text => text.trim()) || current.layout) sections.push(current)
      current = { layout: directive[1], lines: [] }
      continue
    }
    current.lines.push(line)
  }
  if (current.lines.some(text => text.trim()) || current.layout) sections.push(current)
  return sections
}

/** 章节内按 `---` 分左右栏；返回 { main, side }。 */
export function splitColumns(lines) {
  const main = []
  const side = []
  let inSide = false
  let seenSep = false
  for (const line of lines) {
    if (line.trim() === '---') {
      if (!seenSep) {
        seenSep = true
        inSide = true
        continue
      }
      if (inSide) {
        inSide = false
        continue
      }
    }
    if (inSide) side.push(line)
    else main.push(line)
  }
  return { main: main.join('\n').trim(), side: side.join('\n').trim() }
}

/**
 * 预览整篇：布局标签 + 章节内容，双栏章节并排显示。
 * 空文档返回空字符串（由界面显示占位提示）。
 */
export function renderCoursewarePreview(markdown) {
  const source = String(markdown ?? '')
  if (!source.trim()) return ''
  const sections = splitByLayout(source)
  return sections
    .map(section => {
      const { main, side } = splitColumns(section.lines)
      const label = section.layout ? `<div class="layout">布局 ${escapeHtml(section.layout)}</div>` : ''
      const mainHtml = main ? renderMarkdownFragment(main) : ''
      const sideHtml = side ? renderMarkdownFragment(side) : ''
      if (!sideHtml) return `<section class="preview-section">${label}${mainHtml}</section>`
      return `<section class="preview-section">${label}<div class="preview-columns">`
        + `<div class="preview-main">${mainHtml}</div>`
        + `<div class="preview-side">${sideHtml}</div>`
        + '</div></section>'
    })
    .join('')
}
