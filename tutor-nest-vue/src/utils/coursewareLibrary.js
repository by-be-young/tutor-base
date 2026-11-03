// src/utils/coursewareLibrary.js
// 「我的课件」库：把后端托管清单与站点静态目录合起来看。
//
// 为什么需要它：`public/articles/**` 下的 Markdown 是 git 发布出去的静态课件，
// 从来没有走过 course_release_head，所以托管接口一条都列不到。后端现在会把
// `courseware_catalog` 里、当前管理员可见的静态课件一并返回（hosted=false），
// 但前提是这份目录已经同步过；这里在拉清单前补一次目录同步，并给静态课件
// 补上 articles.json 里的 path / series / title。
import { catalogSyncItems } from './coursewareClaims.js'
import { fetchStaticIndex } from './articleContent.js'
import { coursewareGateway } from '../gateways/coursewareGateway.js'

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
 * 拉「我的课件」清单：先按静态目录同步一次 courseware_catalog，再取后端清单。
 * 同步失败不阻塞——后端已有的目录行仍然可用。
 */
export async function loadCoursewareLibrary() {
  const staticIndex = await fetchStaticIndex()
  try {
    const items = catalogSyncItems(staticIndex)
    if (items.length) await coursewareGateway.syncCatalog(items)
  } catch {
    // 目录同步失败（接口下线/网络异常）时继续读后端清单
  }
  const articles = enrichWithStaticIndex(await coursewareGateway.listArticles(), staticIndex)
  return { staticIndex, articles }
}
