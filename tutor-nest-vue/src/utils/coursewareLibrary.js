// src/utils/coursewareLibrary.js
// 「我的课件」库：清单与正文都只来自后端数据库。
import { coursewareGateway } from '../gateways/coursewareGateway.js'

/**
 * 打开一篇课件时该显示的正文：草稿 → 已发布正文。
 * 两处都取不到时抛出后端的 courseware_content_missing / courseware_not_found，
 * 由调用方提示管理员重新发布，仓库里没有任何可回退的静态文件。
 *
 * @returns {Promise<{articleId: number, markdown: string, hasDraft: boolean,
 *   source: 'draft'|'published', contentMissing: boolean}>}
 */
export async function loadArticleMarkdownForEditing(article) {
  if (!article) throw new Error('没有选中课件')
  const articleId = article.articleId
  const draft = await coursewareGateway.getDraft(articleId)
  const hasDraft = Boolean(draft?.hasDraft)
  return {
    articleId,
    markdown: String(draft?.markdown ?? ''),
    hasDraft,
    source: hasDraft ? 'draft' : 'published',
    contentMissing: false
  }
}

/**
 * 拉「我的课件」清单（已发布 + 草稿），正文全部由后端托管。
 */
export async function loadCoursewareLibrary() {
  const articles = await coursewareGateway.listArticles()
  return { articles: articles || [] }
}
