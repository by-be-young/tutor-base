/**
 * scripts/sync-articles-data.js
 *
 * 扫描 public/articles/ 下的所有 .md 文件，自动生成 id_map.json 和 articles.json。
 * 已有文件保留原 id，新文件分配 (当前最大 id + 1)。
 *
 * 在 npm run dev 和 npm run build 前自动执行。
 */
import { readFileSync, writeFileSync, existsSync, statSync } from 'node:fs'
import { readdirSync } from 'node:fs'
import { join, relative, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ROOT = join(__dirname, '..')
const BLOGS_DIR = join(ROOT, 'public', 'articles')
const DATA_FILE = join(ROOT, 'public', 'data', 'articles.json')
const ID_MAP_FILE = join(ROOT, 'public', 'data', 'id_map.json')

// ===== 工具函数 =====

function readJson(path) {
  if (!existsSync(path)) return null
  try {
    return JSON.parse(readFileSync(path, 'utf-8'))
  } catch { return null }
}

function scanMdFiles(dir, baseDir) {
  const entries = readdirSync(dir, { withFileTypes: true })
  const results = []
  for (const entry of entries) {
    const fullPath = join(dir, entry.name)
    if (entry.isDirectory()) {
      results.push(...scanMdFiles(fullPath, baseDir))
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      results.push(relative(baseDir, fullPath).replace(/\\/g, '/'))
    }
  }
  return results.sort()
}

function getFileDate(relPath) {
  try {
    const fullPath = join(BLOGS_DIR, relPath)
    return statSync(fullPath).mtime.toISOString().slice(0, 10)
  } catch {
    return new Date().toISOString().slice(0, 10)
  }
}

function titleFromPath(relPath) {
  return relPath.replace(/\.md$/, '').split('/').pop()
}

function seriesFromPath(relPath) {
  return relPath.split('/')[0] || ''
}

// ===== 主流程 =====

function main() {
  if (!existsSync(BLOGS_DIR)) {
    console.warn('[sync-articles] 未找到 public/articles/ 目录，跳过')
    return
  }

  const files = scanMdFiles(BLOGS_DIR, BLOGS_DIR)

  // 1. 读取现有 id_map.json
  const oldIdMap = readJson(ID_MAP_FILE) || {}
  const maxId = Object.values(oldIdMap).reduce((max, id) => id > max ? id : max, 0)
  let nextId = maxId + 1

  // 2. 生成新的 id_map（为新增文件分配 id）
  const idMap = {}
  for (const relPath of files) {
    if (relPath in oldIdMap) {
      idMap[relPath] = oldIdMap[relPath]  // 保留旧 id
    } else {
      idMap[relPath] = nextId++            // 新文件：最大 id + 1
    }
  }
  writeFileSync(ID_MAP_FILE, JSON.stringify(idMap, null, 2) + '\n', 'utf-8')

  // 3. 从旧的 articles.json 读取已有日期
  const oldArticles = readJson(DATA_FILE) || []
  const dateMap = new Map()
  for (const item of oldArticles) {
    dateMap.set(item.path, item.date)
  }

  // 4. 生成 articles.json
  const articles = files.map(relPath => ({
    id: idMap[relPath],
    title: titleFromPath(relPath),
    series: seriesFromPath(relPath),
    date: dateMap.get(relPath) || getFileDate(relPath),
    path: relPath,
  }))

  // 5. 按系列 + id 排序
  articles.sort((a, b) => {
    if (a.series !== b.series) return a.series.localeCompare(b.series, 'zh-CN')
    return a.id - b.id
  })

  writeFileSync(DATA_FILE, JSON.stringify(articles, null, 2) + '\n', 'utf-8')
  console.log(`[sync-articles] 同步完成：${files.length} 篇文章`)
  console.log(`  → id_map.json 已更新（${Object.keys(idMap).length} 项）`)
  console.log(`  → articles.json 已生成`)
}

main()
