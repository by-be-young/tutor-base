/**
 * scripts/check-courseware.mjs
 *
 * 课件格式校验：确保 public/articles/ 下的每一份课件都能被错题本正确解析题干。
 * 规则来源：tutor-nest-vue/docs/文章格式规范.md
 *
 * 用法：
 *   node tutor-nest-vue/scripts/check-courseware.mjs            仅报错（不通过退出码 1）
 *   node tutor-nest-vue/scripts/check-courseware.mjs --strict   同时把「旧课件回退」视为错误
 *   node tutor-nest-vue/scripts/check-courseware.mjs --verbose  打印每道题的解析结果
 */
import { readdirSync, readFileSync } from 'node:fs'
import { join, relative, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { analyzeCourseware } from '../src/utils/questionText.js'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ARTICLES_DIR = join(__dirname, '..', 'public', 'articles')

const args = new Set(process.argv.slice(2))
const strict = args.has('--strict')
const verbose = args.has('--verbose')

const explicitAnswer = /【@\s*(\d+)\s*】/g
const autoAnswer = /【@\s*】/g
const stemToken = /【题干\s*(\d+)(?:\s*-\s*(\d+))?\s*】/g

function scan(dir, base) {
    const out = []
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
        const full = join(dir, entry.name)
        if (entry.isDirectory()) out.push(...scan(full, base))
        else if (entry.isFile() && entry.name.endsWith('.md')) out.push(relative(base, full).replace(/\\/g, '/'))
    }
    return out.sort()
}

function checkFile(relPath) {
    const markdown = readFileSync(join(ARTICLES_DIR, relPath), 'utf-8')
    const errors = []
    const warnings = []

    const answers = [...markdown.matchAll(explicitAnswer)].map(m => Number(m[1]))
    const autoCount = [...markdown.matchAll(autoAnswer)].length
    const { questions } = analyzeCourseware(markdown)

    if (!questions.length) return { relPath, questions, errors, warnings, skipped: true }

    // 1. 显式编号唯一
    const seen = new Set()
    for (const id of answers) {
        if (seen.has(id)) errors.push(`作答占位符编号重复：【@${id}】`)
        seen.add(id)
    }

    // 2. 显式编号连续（自动编号按出现顺序生成，天然连续）
    //    历史课件允许缺号：重排编号会让已录入的答案键与新编号失配，因此只提示、不报错
    if (answers.length && !autoCount) {
        const max = Math.max(...answers)
        for (let id = 1; id <= max; id++) {
            if (!seen.has(id)) warnings.push(`作答占位符编号不连续：缺少【@${id}】（不影响解析；如需重排请同步重录答案键）`)
        }
    }
    if (answers.length && autoCount) {
        warnings.push(`显式编号【@N】与自动编号【@】混用（自动 ${autoCount} 处），请统一写法`)
    }

    // 3. 题干标记覆盖的题号必须有对应作答占位符
    const answerIds = new Set(questions.map(q => q.id))
    for (const m of markdown.matchAll(stemToken)) {
        const start = Number(m[1])
        const end = m[2] ? Number(m[2]) : start
        if (end < start) warnings.push(`题干区间非法：【题干${m[1]}-${m[2]}】`)
        for (let id = start; id <= end; id++) {
            if (!answerIds.has(String(id))) warnings.push(`题干标记【题干${m[0].slice(3, -1)}】覆盖的题号 ${id} 没有作答占位符`)
        }
    }

    // 4. 每道题都能解析出题干
    for (const question of questions) {
        if (question.source === 'fallback') {
            errors.push(`第 ${question.order} 题（id=${question.id}）无法解析题干，仅能回退为「${question.stem}」`)
        } else if (question.source === 'nearby') {
            const message = `第 ${question.order} 题（id=${question.id}）依赖旧课件回退，请补【题干${question.id}】`
            if (strict) errors.push(message)
            else warnings.push(message)
        } else if (question.stem.replace(/\s/g, '').length < 5) {
            errors.push(`第 ${question.order} 题（id=${question.id}）题干过短，请补全【题干${question.id}】内容`)
        }
    }

    return { relPath, questions, errors, warnings, skipped: false }
}

const files = scan(ARTICLES_DIR, ARTICLES_DIR)
let errorCount = 0
let warningCount = 0
let questionCount = 0
let fileWithQuestions = 0

for (const relPath of files) {
    const result = checkFile(relPath)
    if (result.skipped) {
        if (verbose) console.log(`- ${relPath}：无作答占位符，跳过`)
        continue
    }
    fileWithQuestions++
    questionCount += result.questions.length
    if (verbose) {
        console.log(`\n## ${relPath}（${result.questions.length} 题）`)
        result.questions.forEach(q => console.log(`  ${q.order}. [id=${q.id}][${q.source}] ${q.stem.split('\n')[0]}`))
    }
    for (const message of result.warnings) {
        warningCount++
        console.log(`⚠️  ${relPath}：${message}`)
    }
    for (const message of result.errors) {
        errorCount++
        console.error(`❌ ${relPath}：${message}`)
    }
}

console.log(`\n[check-courseware] 课件 ${fileWithQuestions} 份 / 题目 ${questionCount} 道：`
    + `${errorCount} 个错误，${warningCount} 个警告${strict ? '（--strict）' : ''}`)

if (errorCount > 0) process.exit(1)
