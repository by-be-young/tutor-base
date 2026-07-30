/**
 * scripts/sync-blogs-data.js
 *
 * 扫描 public/blogs/ 下的所有 .md 文件，自动生成 public/data/blogs.json。
 * 已存在的文件保留其 id 和 date，新增文件自动分配新 id。
 *
 * 在 npm run dev 和 npm run build 前自动执行。
 */
import { readFileSync, writeFileSync, existsSync, statSync } from 'node:fs'
import { readdirSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { dirname } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..')
const BLOGS_DIR = join(ROOT, 'public', 'blogs')
const DATA_FILE = join(ROOT, 'public', 'data', 'blogs.json')

// 递归扫描所有 .md 文件
function scanMdFiles(dir, baseDir) {
  const entries = readdirSync(dir, { withFileTypes: true })
  const results = []
  for (const entry of entries) {
    const fullPath = join(dir, entry.name)
    if (entry.isDirectory()) {
      results.push(...scanMdFiles(fullPath, baseDir))
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      const relPath = relative(baseDir, fullPath).replace(/\\/g, '/')
      results.push(relPath)
    }
  }
  return results.sort() // 按路径字母序排列，确保稳定
}

// 读取现有 blogs.json（如果存在）
function loadExisting() {
  if (!existsSync(DATA_FILE)) return []
  try {
    const raw = readFileSync(DATA_FILE, 'utf-8')
    return JSON.parse(raw)
  } catch {
    return []
  }
}

// 获取文件修改时间
function getFileDate(relPath) {
  try {
    const fullPath = join(BLOGS_DIR, relPath)
    const mtime = statSync(fullPath).mtime
    return mtime.toISOString().slice(0, 10)
  } catch {
    return new Date().toISOString().slice(0, 10)
  }
}

// 生成标题：去掉目录前缀和 .md 后缀
function titleFromPath(relPath) {
  return relPath
    .replace(/\.md$/, '')           // 去掉 .md
    .split('/').pop()               // 取文件名部分
}

// 获取系列名：路径第一段目录
function seriesFromPath(relPath) {
  return relPath.split('/')[0] || ''
}

function main() {
  if (!existsSync(BLOGS_DIR)) {
    console.warn('[sync-blogs] 未找到 public/blogs/ 目录，跳过')
    return
  }

  const files = scanMdFiles(BLOGS_DIR, BLOGS_DIR)
  const existing = loadExisting()

  // 用 path 做 key 建立已有映射
  const pathMap = new Map()
  let maxId = 0
  for (const item of existing) {
    pathMap.set(item.path, item)
    if (item.id > maxId) maxId = item.id
  }

  // 找到所有已使用的 id
  const usedIds = new Set(existing.map(i => i.id))

  // 分配新 id 的计数器
  let nextId = maxId + 1

  const blogs = files.map(relPath => {
    const existingItem = pathMap.get(relPath)

    if (existingItem) {
      // 已存在：保留 id 和 date
      return {
        id: existingItem.id,
        title: titleFromPath(relPath),
        series: seriesFromPath(relPath),
        date: existingItem.date,
        path: relPath,
      }
    }

    // 新文件：分配 id，用文件修改时间
    // 优先填补 usedIds 中的空缺
    let newId = nextId++
    // 按系列分组排序：化学 > 英语，同系列内按路径排序
    return {
      id: newId,
      title: titleFromPath(relPath),
      series: seriesFromPath(relPath),
      date: getFileDate(relPath),
      path: relPath,
    }
  })

  // 排序：化学在前，英语在后；同系列按 id 排序
  blogs.sort((a, b) => {
    if (a.series !== b.series) return a.series.localeCompare(b.series, 'zh-CN')
    return a.id - b.id
  })

  writeFileSync(DATA_FILE, JSON.stringify(blogs, null, 2) + '\n', 'utf-8')
  console.log(`[sync-blogs] 同步完成：${files.length} 篇文章 → public/data/blogs.json`)
}

main()
