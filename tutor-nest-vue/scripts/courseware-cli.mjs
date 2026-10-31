#!/usr/bin/env node
/**
 * 课件 Git 工作区 CLI。
 *
 * 仓库里只有 Markdown，正文与版本仍托管在服务端：
 *   node scripts/courseware-cli.mjs doctor                            # 自检环境与会话
 *   node scripts/courseware-cli.mjs pull --subject 高中化学 --dir ./courseware
 *   node scripts/courseware-cli.mjs status --subject 高中化学 --dir ./courseware
 *   node scripts/courseware-cli.mjs push --subject 高中化学 --dir ./courseware [--dry-run]
 *
 * 鉴权用已登录浏览器的会话 cookie（TUTOR_SESSION）与 CSRF token：
 *   $env:TUTOR_SESSION='...'; $env:TUTOR_CSRF='...'
 *
 * 设计取舍：
 * - pull 只写缺失或 hash 变化的文件，改动过但未 push 的文件不会被静默覆盖（会提示冲突）；
 * - push 前自动比对服务端版本，本地记录的 base 版本过期则直接失败，要求先 pull；
 * - push 会随请求带上被删除的文件路径（本地已删、服务端仍存在）。
 */
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'

const STATE_FILE = '.courseware-state.json'

function parseArgs(argv) {
  const [command, ...rest] = argv
  const options = { _: command }
  for (let index = 0; index < rest.length; index++) {
    const token = rest[index]
    if (!token.startsWith('--')) continue
    const key = token.slice(2)
    const next = rest[index + 1]
    if (next && !next.startsWith('--')) {
      options[key] = next
      index++
    } else {
      options[key] = true
    }
  }
  return options
}

function requireOption(options, name) {
  if (!options[name]) {
    fail(`缺少 --${name}`)
  }
  return String(options[name])
}

function fail(message) {
  console.error(`✗ ${message}`)
  process.exit(1)
}

function apiBase() {
  return (process.env.TUTOR_API_BASE || 'http://127.0.0.1:8080/api/v1').replace(/\/$/, '')
}

function authHeaders(extra = {}) {
  const cookie = process.env.TUTOR_SESSION
  const csrf = process.env.TUTOR_CSRF
  if (!cookie) fail('缺少环境变量 TUTOR_SESSION（登录后的会话 cookie）')
  const headers = { Accept: 'application/json', Cookie: `TUTOR_SESSION=${cookie}`, ...extra }
  if (csrf) headers['X-CSRF-TOKEN'] = csrf
  return headers
}

async function call(path, options = {}) {
  const response = await fetch(`${apiBase()}${path}`, {
    method: options.method || 'GET',
    headers: authHeaders(options.body ? { 'Content-Type': 'application/json' } : {}),
    body: options.body ? JSON.stringify(options.body) : undefined
  })
  const text = await response.text()
  const payload = text ? JSON.parse(text) : null
  if (!response.ok) {
    const detail = payload?.errors?.length
      ? payload.errors.join('; ')
      : payload?.detail || payload?.code || response.statusText
    throw new Error(`${response.status} ${detail}`)
  }
  return payload
}

function sha256(content) {
  return `sha256:${createHash('sha256').update(content, 'utf8').digest('hex')}`
}

function walkMarkdown(rootDir) {
  const files = new Map()
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name)
      const stats = statSync(full)
      if (stats.isDirectory()) walk(full)
      else if (name.endsWith('.md')) {
        files.set(relative(rootDir, full).replace(/\\/g, '/'), readFileSync(full, 'utf8'))
      }
    }
  }
  if (existsSync(rootDir)) walk(rootDir)
  return files
}

function readState(dir) {
  const path = join(dir, STATE_FILE)
  if (!existsSync(path)) return { version: null, files: {} }
  try {
    return JSON.parse(readFileSync(path, 'utf8'))
  } catch {
    return { version: null, files: {} }
  }
}

function writeState(dir, state) {
  mkdirSync(dir, { recursive: true })
  writeFileSync(join(dir, STATE_FILE), `${JSON.stringify(state, null, 2)}\n`, 'utf8')
}

function writeFile(dir, logicalPath, content) {
  const target = join(dir, logicalPath)
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, content, 'utf8')
}

async function pull(options) {
  const subject = requireOption(options, 'subject')
  const dir = resolve(options.dir || './courseware')
  const manifest = await call(`/admin/courseware/workspace/manifest?subject=${encodeURIComponent(subject)}`)
  const payload = await call(`/admin/courseware/workspace/files?subject=${encodeURIComponent(subject)}`)
  const state = readState(dir)
  const files = payload.files || {}
  let written = 0
  let unchanged = 0
  const conflicts = []

  for (const entry of manifest.entries) {
    const content = files[entry.logicalPath]
    if (content == null) continue
    const target = join(dir, entry.logicalPath)
    const localHash = existsSync(target) ? sha256(readFileSync(target, 'utf8')) : null
    const baseHash = state.files?.[entry.logicalPath] ?? null
    if (localHash === sha256(content)) {
      unchanged++
    } else if (localHash != null && localHash !== baseHash) {
      conflicts.push(entry.logicalPath)
      continue
    } else {
      writeFile(dir, entry.logicalPath, content)
      written++
    }
    state.files = state.files || {}
    state.files[entry.logicalPath] = sha256(content)
  }

  // 服务端已经没有的本地文件：只提示，不自动删除
  const remotePaths = new Set(manifest.entries.map(entry => entry.logicalPath))
  const stale = [...walkMarkdown(dir).keys()].filter(path => !remotePaths.has(path))

  state.version = manifest.version
  state.subject = subject
  writeState(dir, state)

  console.log(`✓ ${subject} v${manifest.version}：写入 ${written} 篇，未变 ${unchanged} 篇`)
  if (conflicts.length) {
    console.warn(`⚠ 本地有未推送改动，已保留（${conflicts.length} 篇）：`)
    conflicts.forEach(path => console.warn(`  ${path}`))
  }
  if (stale.length) {
    console.warn(`⚠ 服务端已不存在（本地可删除）：${stale.length} 篇`)
    stale.forEach(path => console.warn(`  ${path}`))
  }
}

async function status(options) {
  const subject = requireOption(options, 'subject')
  const dir = resolve(options.dir || './courseware')
  const manifest = await call(`/admin/courseware/workspace/manifest?subject=${encodeURIComponent(subject)}`)
  const state = readState(dir)
  const local = walkMarkdown(dir)
  const remote = new Map(manifest.entries.map(entry => [entry.logicalPath, entry.contentHash]))

  const added = [...local.keys()].filter(path => !remote.has(path))
  const deleted = [...remote.keys()].filter(path => !local.has(path))
  const modified = [...local.entries()]
    .filter(([path, content]) => remote.has(path) && remote.get(path) !== sha256(content))
    .map(([path]) => path)

  console.log(`服务端 ${subject} v${manifest.version}`)
  console.log(`本地 base 版本 ${state.version ?? '（未 pull）'}`)
  if (added.length) console.log(`新增 ${added.length}：\n  ${added.join('\n  ')}`)
  if (modified.length) console.log(`修改 ${modified.length}：\n  ${modified.join('\n  ')}`)
  if (deleted.length) console.log(`删除 ${deleted.length}：\n  ${deleted.join('\n  ')}`)
  if (!added.length && !modified.length && !deleted.length) console.log('没有差异。')
}

async function push(options) {
  const subject = requireOption(options, 'subject')
  const dir = resolve(options.dir || './courseware')
  const dryRun = Boolean(options['dry-run'])
  const manifest = await call(`/admin/courseware/workspace/manifest?subject=${encodeURIComponent(subject)}`)
  const state = readState(dir)

  if (state.version != null && state.version !== manifest.version) {
    fail(`本地 base 版本 ${state.version} 已过期（服务端 v${manifest.version}），请先 pull`)
  }
  if (state.version == null) {
    fail('还没有 pull 过该科目，请先执行 pull（push 需要 baseVersion）')
  }

  const remote = new Map(manifest.entries.map(entry => [entry.logicalPath, entry.contentHash]))
  const local = walkMarkdown(dir)
  const files = []
  for (const [path, content] of local.entries()) {
    if (remote.get(path) !== sha256(content)) {
      files.push({ logicalPath: path, markdown: content })
    }
  }
  const deletedPaths = [...remote.keys()].filter(path => !local.has(path))
  if (!files.length && !deletedPaths.length) {
    console.log('没有需要推送的变更。')
    return
  }

  console.log(`将推送 ${files.length} 篇、删除 ${deletedPaths.length} 篇（base v${manifest.version}）`)
  if (dryRun) {
    files.forEach(file => console.log(`  ~ ${file.logicalPath}`))
    deletedPaths.forEach(path => console.log(`  - ${path}`))
    return
  }

  const result = await call('/admin/courseware/workspace/push', {
    method: 'POST',
    body: { subject, baseVersion: manifest.version, files, deletedPaths }
  })
  state.version = result.version
  state.files = {}
  for (const [path, content] of local.entries()) state.files[path] = sha256(content)
  writeState(dir, state)
  console.log(`✓ 已发布 v${result.version}：共 ${result.articleCount} 篇，变更 ${result.changedCount}，删除 ${result.deletedCount}`)
}

async function doctor(options) {
  const checks = []
  const nodeMajor = Number(process.versions.node.split('.')[0])
  checks.push({
    ok: nodeMajor >= 18,
    label: `Node.js ${process.versions.node}`,
    hint: '需要 18 或更高版本：https://nodejs.org/'
  })
  const base = apiBase()
  checks.push({ ok: true, label: `接口地址 ${base}`, hint: '' })
  const cookie = process.env.TUTOR_SESSION
  checks.push({
    ok: Boolean(cookie),
    label: cookie ? '已设置 TUTOR_SESSION' : '未设置 TUTOR_SESSION',
    hint: '登录后从浏览器开发者工具复制 TUTOR_SESSION cookie 值'
  })
  if (cookie) {
    try {
      const health = await fetch(`${base}/system/status`, { headers: { Accept: 'application/json' } })
      checks.push({ ok: health.ok, label: `接口连通（HTTP ${health.status}）`, hint: '检查 TUTOR_API_BASE 与网络' })
      if (health.ok) {
        const manifests = await call('/courseware/manifests')
        const count = Array.isArray(manifests?.items) ? manifests.items.length : 0
        checks.push({
          ok: true,
          label: `会话有效，可见 ${count} 个发布科目`,
          hint: '会话过期请重新登录并更新 cookie'
        })
      }
    } catch (error) {
      checks.push({ ok: false, label: `会话/连通性检查失败：${error.message}`, hint: '重新登录后更新 TUTOR_SESSION' })
    }
  }
  if (options.subject) {
    const dir = resolve(options.dir || './courseware')
    checks.push({
      ok: existsSync(dir),
      label: existsSync(dir) ? `工作区存在 ${dir}` : `工作区尚未创建 ${dir}（先执行 pull）`,
      hint: 'node scripts/courseware-cli.mjs pull --subject <科目>'
    })
  }
  for (const check of checks) {
    console.log(`${check.ok ? '✓' : '✗'} ${check.label}${check.ok || !check.hint ? '' : `\n    → ${check.hint}`}`)
  }
  if (checks.some(check => !check.ok)) {
    console.error('\n有检查未通过，按上面的提示处理后重试。')
    process.exit(1)
  }
  console.log('\n✓ 环境就绪：可以执行 pull / status / push 了。')
}

async function main() {
  const options = parseArgs(process.argv.slice(2))
  switch (options._) {
    case 'pull':
      return pull(options)
    case 'status':
      return status(options)
    case 'push':
      return push(options)
    case 'doctor':
      return doctor(options)
    default:
      console.log(`用法：
  node scripts/courseware-cli.mjs doctor                            # 自检：Node / 接口 / 会话 / 工作区
  node scripts/courseware-cli.mjs pull   --subject <科目> [--dir ./courseware]
  node scripts/courseware-cli.mjs status --subject <科目> [--dir ./courseware]
  node scripts/courseware-cli.mjs push   --subject <科目> [--dir ./courseware] [--dry-run]

环境变量：TUTOR_SESSION（会话 cookie 值）、TUTOR_CSRF（CSRF token）、TUTOR_API_BASE（默认 http://127.0.0.1:8080/api/v1）`)
      if (options._) process.exit(1)
  }
}

main().catch(error => fail(error.message))
