// src/utils/articleContent.js
// 课件正文读取：优先走后端托管接口（/courseware/articles/{teacherId}/{articleId}/content），
// 后端不可用或该篇未托管时回退到 public/articles 下的静态文件。
import { coursewareGateway } from '../gateways/coursewareGateway.js'

export async function fetchStaticArticle(path) {
  if (!path) return null
  const baseUrl = import.meta.env?.BASE_URL || ''
  try {
    const response = await fetch(`${baseUrl}articles/${path}`)
    if (!response.ok) return null
    return await response.text()
  } catch {
    return null
  }
}

export async function loadArticleMarkdown(blog) {
  if (!blog) return null
  if (blog.teacherId != null && blog.teacherArticleId != null) {
    try {
      const markdown = await coursewareGateway.fetchArticleContent(blog.teacherId, blog.teacherArticleId)
      if (markdown) return markdown
    } catch {
      // 未授权、版本下线或后端不可达：继续尝试静态兜底
    }
  }
  return fetchStaticArticle(blog.path)
}
