// src/utils/coursewareLibrary.js
// 「我的课件」库：清单与正文都只来自后端当前发布版本。
import { coursewareGateway } from '../gateways/coursewareGateway.js'

/**
 * 打开一篇课件时显示的正文：当前发布版本，没有草稿这一层。
 * 正文缺失时后端返回 courseware_content_missing，由调用方提示管理员重新保存。
 *
 * @returns {Promise<{articleId: number, markdown: string,
 *   source: 'published', contentMissing: boolean}>}
 */
export async function loadArticleMarkdownForEditing(article) {
  if (!article) throw new Error('没有选中课件')
  const articleId = article.articleId
  const content = await coursewareGateway.getArticle(articleId)
  return {
    articleId,
    markdown: String(content?.markdown ?? ''),
    source: 'published',
    contentMissing: false
  }
}

/**
 * 拉「我的课件」清单（只含当前发布版本），正文全部由后端托管。
 */
export async function loadCoursewareLibrary() {
  const articles = await coursewareGateway.listArticles()
  return { articles: articles || [] }
}
