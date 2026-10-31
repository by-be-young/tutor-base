#!/usr/bin/env node
/**
 * 静态检查：禁止给 `const` 声明的变量二次赋值。
 *
 * `const x = await f()` 之后再写 `x = ...` 在语法上是合法的，只有运行到这里才抛
 * "Assignment to constant variable"，构建与 SFC 编译都不会报错。这个脚本用 Vue 自带的
 * babel parser 扫描每个 .vue 的 script 块，按函数作用域收集 `const` 名字并找出对它们的赋值。
 *
 * 用法：
 *   node scripts/check-const-reassign.mjs
 *   node scripts/check-const-reassign.mjs --file tutor-nest-vue/src/views/ArticleDetailView.vue
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve } from 'node:path'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const { parse: parseSfc } = require('../node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js')
const { parse: parseJs } = require('@babel/parser')

const srcRoot = resolve(process.cwd(), 'tutor-nest-vue', 'src')
const args = process.argv.slice(2)
const fileArgIndex = args.indexOf('--file')
const only = fileArgIndex >= 0 ? resolve(args[fileArgIndex + 1]) : null

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const stats = statSync(full)
    if (stats.isDirectory()) walk(full, out)
    else if (full.endsWith('.vue')) out.push(full)
  }
  return out
}

const FUNCTION_TYPES = new Set([
  'FunctionDeclaration',
  'FunctionExpression',
  'ArrowFunctionExpression'
])

function isNode(value) {
  return value && typeof value === 'object' && typeof value.type === 'string'
}

function forEachChild(node, visit) {
  for (const key of Object.keys(node)) {
    if (key === 'loc' || key === 'start' || key === 'end') continue
    const value = node[key]
    if (Array.isArray(value)) {
      for (const item of value) {
        if (isNode(item)) visit(item)
      }
    } else if (isNode(value)) {
      visit(value)
    }
  }
}

/** 收集某个函数作用域（不含更深层函数）里的 const 名字。 */
function constNamesInScope(fnNode) {
  const names = new Set()
  const body = fnNode.body
  if (body?.type === 'BlockStatement') {
    for (const statement of body.body) {
      collectDeclaration(statement, names)
    }
  }
  return names
}

function collectDeclaration(node, names) {
  if (!node) return
  if (node.type === 'VariableDeclaration' && node.kind === 'const') {
    for (const declarator of node.declarations) {
      if (declarator.id?.type === 'Identifier') names.add(declarator.id.name)
    }
  }
  // 只下钻语句容器，不进入内层函数（内层函数自己算作用域）
  if (node.type === 'BlockStatement') {
    for (const statement of node.body) collectDeclaration(statement, names)
  }
  if (node.type === 'IfStatement') {
    collectDeclaration(node.consequent, names)
    collectDeclaration(node.alternate, names)
  }
  if (node.type === 'ForStatement' || node.type === 'ForOfStatement' || node.type === 'ForInStatement') {
    collectDeclaration(node.init, names)
    collectDeclaration(node.left, names)
    collectDeclaration(node.body, names)
  }
  if (node.type === 'TryStatement') {
    collectDeclaration(node.block, names)
    collectDeclaration(node.handler?.body, names)
    collectDeclaration(node.finalizer, names)
  }
  if (node.type === 'WhileStatement' || node.type === 'DoWhileStatement') {
    collectDeclaration(node.body, names)
  }
  if (node.type === 'SwitchStatement') {
    for (const switchCase of node.cases) {
      for (const statement of switchCase.consequent) collectDeclaration(statement, names)
    }
  }
}

function collectFunctions(node, out = []) {
  if (FUNCTION_TYPES.has(node.type)) out.push(node)
  forEachChild(node, child => collectFunctions(child, out))
  return out
}

function findReassignments(scriptContent) {
  const ast = parseJs(scriptContent, { sourceType: 'module', plugins: ['jsx'] })
  const problems = []
  const functions = [ast.program, ...collectFunctions(ast.program)]
  for (const fn of functions) {
    const scope = fn === ast.program ? new Set() : constNamesInScope(fn)
    if (fn === ast.program) {
      for (const statement of ast.program.body) collectDeclaration(statement, scope)
    }
    if (!scope.size) continue
    const visit = (node) => {
      if (FUNCTION_TYPES.has(node.type) && node !== fn) return
      if (node.type === 'AssignmentExpression' && node.left?.type === 'Identifier'
        && scope.has(node.left.name)) {
        problems.push({ name: node.left.name, line: node.loc?.start?.line || 0 })
      }
      if (node.type === 'UpdateExpression' && node.argument?.type === 'Identifier'
        && scope.has(node.argument.name)) {
        problems.push({ name: node.argument.name, line: node.loc?.start?.line || 0 })
      }
      forEachChild(node, visit)
    }
    if (fn === ast.program) {
      for (const statement of ast.program.body) visit(statement)
    } else {
      visit(fn.body)
    }
  }
  return problems
}

const files = only ? [only] : walk(srcRoot)
let failed = 0

for (const file of files) {
  const rel = relative(process.cwd(), file).replace(/\\/g, '/')
  let source
  try {
    source = readFileSync(file, 'utf8')
  } catch (error) {
    console.error(`✗ ${rel}: 无法读取 ${error.message}`)
    failed += 1
    continue
  }
  const { descriptor } = parseSfc(source, { filename: file })
  const scripts = [descriptor.scriptSetup?.content, descriptor.script?.content].filter(Boolean)
  for (const content of scripts) {
    // 用 script 在 .vue 内的起始偏移校正行号
    let problems = []
    try {
      problems = findReassignments(content)
    } catch (error) {
      console.error(`✗ ${rel}: 解析失败 ${error.message}`)
      failed += 1
      continue
    }
    for (const problem of problems) {
      console.error(`✗ ${rel}: 给 const 变量 ${problem.name} 二次赋值（script 内第 ${problem.line} 行）`)
      failed += 1
    }
  }
}

if (failed) {
  console.error(`\nconst 二次赋值检查失败：${failed} 处。改用 let 或引入新变量。`)
  process.exit(1)
}
console.log(`✓ const 二次赋值检查通过：${files.length} 个文件。`)
