// src/utils/articleContent.js
// 课件正文读取：只走数据库托管接口（/courseware/articles/{teacherId}/{articleId}/content）。
// public/articles 下的静态 Markdown 不再作为运行时正文来源；静态课件必须先由服务端导入托管。
import { coursewareGateway } from '../gateways/coursewareGateway.js'

export async function loadArticleMarkdown(blog) {
  if (!blog || blog.teacherId == null || blog.teacherArticleId == null) {
    return null
  }
  return coursewareGateway.fetchArticleContent(blog.teacherId, blog.teacherArticleId)
}
