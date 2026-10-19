#!/usr/bin/env node
/**
 * 只读 SFC 语法检查：编译所有 .vue 文件的 script + template。
 * 不写任何文件，可在受限沙箱里运行。
 *
 * 用法：
 *   node scripts/check-sfc.mjs
 *   node scripts/check-sfc.mjs --file tutor-nest-vue/src/views/HomeView.vue
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve, relative } from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { parse, compileTemplate, compileScript } = require('../node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js')

const srcRoot = resolve(process.cwd(), 'tutor-nest-vue', 'src')
const args = process.argv.slice(2)
const fileArgIndex = args.indexOf('--file')
const only = fileArgIndex >= 0 ? resolve(args[fileArgIndex + 1]) : null

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const st = statSync(full)
    if (st.isDirectory()) walk(full, out)
    else if (full.endsWith('.vue')) out.push(full)
  }
  return out
}

const files = only ? [only] : walk(srcRoot)
let failed = 0
let checked = 0

for (const file of files) {
  const rel = relative(process.cwd(), file).replace(/\\/g, '/')
  let source
  try {
    source = readFileSync(file, 'utf8')
  } catch (error) {
    console.error(`✖ ${rel}: 无法读取 ${error.message}`)
    failed += 1
    continue
  }

  try {
    const { descriptor, errors } = parse(source, { filename: file })
    if (errors?.length) throw new Error(errors.map(e => e.message).join('\n'))
    if (descriptor.script || descriptor.scriptSetup) {
      compileScript(descriptor, { id: rel })
    }
    if (descriptor.template) {
      const result = compileTemplate({
        source: descriptor.template.content,
        filename: file,
        id: rel,
        scoped: descriptor.styles.some(s => s.scoped)
      })
      if (result.errors?.length) throw new Error(result.errors.map(e => e.message || String(e)).join('\n'))
    }
    checked += 1
  } catch (error) {
    failed += 1
    console.error(`✖ ${rel}\n  ${String(error.message || error).split('\n').join('\n  ')}`)
  }
}

if (failed) {
  console.error(`\n检查失败：${failed} 个文件有错误（共 ${checked + failed} 个）。`)
  process.exit(1)
}
console.log(`✓ SFC 检查通过：${checked} 个文件。`)
