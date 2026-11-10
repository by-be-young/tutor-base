// src/utils/coursewareLibrary.js
// 「我的课件」库：把后端托管清单与站点静态目录合起来看。
//
// 为什么需要它：`public/articles/**` 下的 Markdown 是 git 发布出去的静态课件，
// 从来没有走过 course_release_head，所以托管接口一条都列不到。后端现在会把
// `courseware_catalog` 里、当前管理员可见的静态课件一并返回（hosted=false），
// 但前提是这份目录已经同步过；这里在拉清单前补一次目录同步，并给静态课件
// 补上 articles.json 里的 path / series / title。
import { catalogSyncItems } from './coursewareClaims.js'
import { fetchStaticArticle, fetchStaticIndex } from './articleContent.js'
import { coursewareGateway } from '../gateways/coursewareGateway.js'

let staticIndexPromise = null

/** 静态目录索引只拉一次（同一会话内不变，避免每打开一篇都多一个请求）。 */
export function loadStaticIndex() {
  if (!staticIndexPromise) {
    staticIndexPromise = fetchStaticIndex().catch(() => [])
  }
  return staticIndexPromise
}

/** 静态讲义路径：优先用清单给的 logicalPath，缺失时按旧编号回查 articles.json。 */
export function staticPathFor(article, staticIndex = []) {
  if (article?.logicalPath) return article.logicalPath
  const ids = [article?.articleId, article?.legacyArticleId].filter(value => value != null)
  const entry = staticIndex.find(row => ids.some(id => Number(row.id) === Number(id)))
  return entry?.path || ''
}

async function readStaticArticle(article) {
  const index = await loadStaticIndex()
  return fetchStaticArticle(staticPathFor(article, index))
}

/** 是否值得回退到静态讲义：404=后端没有这篇，409=已发布篇目的正文文件丢了。 */
function isRecoverable(error) {
  return error?.status === 404
    || error?.status === 409
    || error?.code === 'courseware_not_found'
    || error?.code === 'courseware_content_missing'
}

/**
 * 打开一篇课件时该显示的正文：草稿 → 已发布正文 → 站内静态讲义。
 *
 * @returns {Promise<{articleId: number, markdown: string, hasDraft: boolean,
 *   source: 'draft'|'published'|'static'|'recovered', contentMissing: boolean}>}
 * @throws 三处都取不到正文时抛出后端/前端错误，由调用方提示
 */
export async function loadArticleMarkdownForEditing(article) {
  if (!article) throw new Error('没有选中课件')
  const articleId = article.articleId
  const staticOnly = article.hosted === false && !article.hasDraft

  if (!staticOnly) {
    try {
      const draft = await coursewareGateway.getDraft(articleId)
      const hasDraft = Boolean(draft?.hasDraft)
      return {
        articleId,
        markdown: String(draft?.markdown ?? ''),
        hasDraft,
        source: hasDraft ? 'draft' : 'published',
        contentMissing: false
      }
    } catch (error) {
      if (!isRecoverable(error)) throw error
      const markdown = await readStaticArticle(article)
      if (markdown == null) throw error
      // 已发布篇目的正文文件丢了：用站内讲义顶上，保存草稿后重新发布即可把 blob 写回。
      return { articleId, markdown, hasDraft: false, source: 'recovered', contentMissing: true }
    }
  }

  const markdown = await readStaticArticle(article)
  if (markdown == null) {
    throw new Error(`取不到这篇静态课件的正文：${article.logicalPath || articleId}`)
  }
  return { articleId, markdown, hasDraft: false, source: 'static', contentMissing: false }
}

/** 给 hosted=false 的条目补静态目录里的逻辑路径、系列与标题。 */
export function enrichWithStaticIndex(list, staticIndex) {
  if (!staticIndex.length) return list
  return list.map((item) => {
    if (item.hosted !== false) return item
    const entry = staticIndex.find(row => Number(row.id) === Number(item.articleId))
    if (!entry) return item
    return {
      ...item,
      title: item.title || entry.title,
      series: item.series || entry.series,
      logicalPath: item.logicalPath || entry.path
    }
  })
}

/**
 * 丢掉静态目录里已经没有的静态课件（讲义删并后，后端 catalog 还留着旧行）。
 * 只处理 hosted=false；目录为空时不做判断，避免接口异常时把清单清空。
 */
export function trimStaleStatic(list, staticIndex) {
  if (!staticIndex.length) return list
  const known = new Set(staticIndex.map(row => Number(row.id)))
  return list.filter(item => item.hosted !== false || known.has(Number(item.articleId)))
}

/**
 * 拉「我的课件」清单：先按静态目录同步一次 courseware_catalog，再取后端清单。
 * 同步失败不阻塞——后端已有的目录行仍然可用。
 */
export async function loadCoursewareLibrary() {
  const staticIndex = await loadStaticIndex()
  const articles = trimStaleStatic(
    enrichWithStaticIndex(await coursewareGateway.listArticles(), staticIndex),
    staticIndex
  )
  return { staticIndex, articles }
}
