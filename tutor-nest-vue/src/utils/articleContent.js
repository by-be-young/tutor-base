// src/utils/articleContent.js
// 课件正文读取：只走数据库托管接口（/courseware/articles/{teacherId}/{articleId}/content）。
// 仓库里没有静态 Markdown，正文与图片全部来自数据库。
import { coursewareGateway } from '../gateways/coursewareGateway.js'

export async function loadArticleMarkdown(blog) {
  if (!blog || blog.teacherId == null || blog.teacherArticleId == null) {
    return null
  }
  return coursewareGateway.fetchArticleContent(blog.teacherId, blog.teacherArticleId)
}
