// src/utils/coursewareTree.js
// 「我的课件」目录：把课件清单按 logicalPath（`高中英语/口语/口语训练-1.md`）
// 还原成多级文件夹树，交给站内同一套 FolderNode/FileNode 渲染。
// 纯函数，方便单测；不读接口、不碰路由。

const FILE_SUFFIX = /\.(md|markdown)$/i

/** 一篇课件在目录里显示的状态文案与配色。 */
export function articleStatus(article) {
  const item = article || {}
  if (item.contentMissing) return { label: '正文缺失 · 需重新发布', tone: 'missing' }
  if (item.hosted === false) {
    return item.hasDraft
      ? { label: '静态讲义 · 有草稿', tone: 'draft' }
      : { label: '静态讲义 · 已上线', tone: 'static' }
  }
  if (!item.published) return { label: '草稿 · 未发布', tone: 'draft' }
  return item.hasDraft
    ? { label: '已发布 · 有草稿', tone: 'draft' }
    : { label: '已发布', tone: 'live' }
}

/** logicalPath → 目录段（去掉 `.md`，统一分隔符）。 */
export function pathSegments(article) {
  const raw = String(article?.logicalPath || '').replace(/\\/g, '/').trim()
  if (!raw) return []
  const segments = raw.split('/').filter(Boolean)
  const last = segments.length - 1
  segments[last] = segments[last].replace(FILE_SUFFIX, '')
  return segments.filter(Boolean)
}

function folder(key, name) {
  return { key, name, isFile: false, children: [] }
}

function sortChildren(node) {
  if (!node.children?.length) return node
  node.children.sort((left, right) => {
    if (left.isFile !== right.isFile) return left.isFile ? 1 : -1
    return String(left.name).localeCompare(String(right.name), 'zh-Hans-CN')
  })
  node.children.filter(child => !child.isFile).forEach(sortChildren)
  return node
}

/**
 * 科目目录树。
 *
 * @param {string} subjectName 科目名（树的根）
 * @param {Array<object>} articles 该科目的课件清单条目
 * @returns {{ name: string, isFile: false, children: Array<object> }}
 */
export function buildCoursewareTree(subjectName, articles = []) {
  const rootName = subjectName || '未归类'
  const root = folder('', rootName)
  const folders = new Map([['', root]])

  const ensureFolder = (parentKey, name) => {
    const key = parentKey ? `${parentKey}/${name}` : name
    let node = folders.get(key)
    if (!node) {
      node = folder(key, name)
      folders.set(key, node)
      folders.get(parentKey).children.push(node)
    }
    return node
  }

  for (const article of articles) {
    let segments = pathSegments(article)
    // 路径第一段常常就是科目名：去掉它，免得出现「高中英语 / 高中英语 / …」
    if (segments.length > 1 && segments[0] === rootName) segments = segments.slice(1)

    const fileName = segments.length ? segments[segments.length - 1] : ''
    let parent = root
    for (const name of segments.slice(0, -1)) {
      parent = ensureFolder(parent.key, name)
    }

    const status = articleStatus(article)
    parent.children.push({
      isFile: true,
      blogId: article.articleId,
      name: article.title || fileName || `课件 ${article.articleId}`,
      meta: status.label,
      tone: status.tone,
      path: article.logicalPath || '',
      article
    })
  }

  return sortChildren(root)
}

/** 树里的叶子篇目（按显示顺序展开）。 */
export function flattenTree(node, result = []) {
  if (!node) return result
  if (node.isFile) {
    result.push(node)
    return result
  }
  for (const child of node.children || []) flattenTree(child, result)
  return result
}
