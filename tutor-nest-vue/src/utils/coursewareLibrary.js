// src/utils/coursewareLibrary.js
// 「我的课件」库：只使用后端托管清单与正文。
// 静态课件必须先由服务端导入成发布版本；浏览器不再从 public/articles 读取正文。
import { coursewareGateway } from '../gateways/coursewareGateway.js'

/**
 * 打开一篇课件时该显示的正文：草稿 → 已发布正文。
 * 两处都取不到时抛出后端的 courseware_content_missing / courseware_not_found，
 * 由调用方提示管理员恢复，而不是回退到静态文件。
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
 * hosted=false 的旧静态课件在服务端导入前不可读，故不列进浏览器课件库。
 */
export async function loadCoursewareLibrary() {
  const articles = await coursewareGateway.listArticles()
  return { articles: (articles || []).filter(item => item.hosted !== false) }
}
