// 课件图片文件名：科目 + 光标所在章节 + 本节第几张图。
// 纯函数，方便单测；不碰 DOM 和接口。

const SUBJECT_SLUGS = {
  '高中化学': 'chemistry',
  '初中化学': 'chemistry-junior',
  '高中英语': 'english',
  '初中英语': 'english-junior',
  '高中物理': 'physics',
  '初中物理': 'physics-junior',
  '高中生物': 'biology',
  '初中生物': 'biology-junior',
  '高中数学': 'math',
  '初中数学': 'math-junior',
  '高中语文': 'chinese',
  '初中语文': 'chinese-junior',
  '高中政治': 'politics',
  '初中政治': 'politics-junior',
  '高中历史': 'history',
  '初中历史': 'history-junior',
  '高中地理': 'geography',
  '初中地理': 'geography-junior'
}

/** 科目 → ASCII 前缀；未知科目退回 courseware。 */
export function subjectSlug(value) {
  const subject = String(value || '').trim()
  if (SUBJECT_SLUGS[subject]) return SUBJECT_SLUGS[subject]
  const ascii = subject.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  return ascii || 'courseware'
}

/** 光标所在章节：第几个 `#`、标题里的编号、本节已有几张图。 */
export function sectionAtCursor(source, cursor) {
  const before = String(source || '').slice(0, Math.max(0, cursor))
  const headings = [...before.matchAll(/^#\s+(.+)$/gm)]
  const section = headings.length || 1
  const heading = headings.length ? headings[headings.length - 1] : null
  const sectionStart = heading ? before.lastIndexOf(heading[0]) : 0
  const figures = (before.slice(sectionStart).match(/!\[\[[^\]]+\]\]/g) || []).length
  return {
    section,
    figure: figures + 1,
    number: heading?.[1].match(/\d+(?:[.-]\d+)*/)?.[0] || ''
  }
}

/**
 * 生成建议文件名：`chemistry-2-3-fig2.png`。
 * 章节标题有编号就用编号，否则用 `s<第几节>`。
 */
export function suggestedImageName(source, cursor, subject, extension = 'png') {
  const position = sectionAtCursor(source, cursor)
  const sectionToken = position.number
    ? position.number.replace(/[.]/g, '-')
    : `s${position.section}`
  return `${subjectSlug(subject)}-${sectionToken}-fig${position.figure}.${extension}`
}
