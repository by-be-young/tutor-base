// src/composables/useWrongQuestionsResolve.js
// 错题本 / 错题训练共用的展示解析逻辑：
// 后端返回当前账户可见的错题与正确答案；静态文章 markdown 只用于补充题干、学科和顺序号。
import { ref } from 'vue'
import { useArticleStore } from '@/stores/blogStore'
import { analyzeCourseware } from '@/utils/questionText'
import { loadArticleMarkdown } from '@/utils/articleContent'

export function useWrongQuestionsResolve() {
    const blogStore = useArticleStore()

    const resolvedMap = ref(new Map())   // 错题 id → { subject, questionText, correctAnswer, order }
    const resolvedReady = ref(false)     // 动态解析是否完成
    const articleCache = new Map()       // blogId → markdown（缓存）
    const questionCache = new Map()      // blogId → Map(题目编号 → { stem, order })

    function resolvedOf(q) {
        return resolvedMap.value.get(q.id) || {}
    }

    async function loadArticle(blogId) {
        const key = String(blogId)
        if (articleCache.has(key)) return articleCache.get(key)
        const blog = blogStore.blogData.find(b => Number(b.id) === Number(blogId))
        const md = blog ? await loadArticleMarkdown(blog) : null
        articleCache.set(key, md)
        return md
    }

    function indexBackendAnswers(list) {
        const map = new Map()
            ; list.forEach(question => map.set(
                `${question.source_blog_id}-${String(question.source_question_id)}`, {
                answerText: question.correct_answer || '',
                autoGrade: false
            }))
        return map
    }

    /** 解析并缓存单篇课件的题目（题干 + 显示顺序号），与课件详情页共用同一套规则 */
    function indexQuestions(blogId, markdown) {
        const key = String(blogId)
        if (questionCache.has(key)) return questionCache.get(key)
        const index = new Map()
        if (markdown) {
            analyzeCourseware(markdown).questions.forEach(question => {
                index.set(question.id, { stem: question.stem, order: question.order })
            })
        }
        questionCache.set(key, index)
        return index
    }

    /** 解析指定错题列表的动态数据（不依赖 store 中的 questions） */
    async function resolveQuestions(list) {
        resolvedReady.value = false
        const blogIds = [...new Set(list.map(q => q.source_blog_id).filter(b => b != null))]

        await Promise.all(blogIds.map(loadArticle))
        const answerMap = indexBackendAnswers(list)

        const map = new Map()
        list.forEach(q => {
            let subject = ''
            let questionText = ''
            let correctAnswer = ''
            let autoGrade = false
            let order = null
            if (q.source_blog_id != null) {
                const blog = blogStore.blogData.find(b => Number(b.id) === Number(q.source_blog_id))
                subject = q.subject || blog?.series || ''
                const index = indexQuestions(q.source_blog_id, articleCache.get(String(q.source_blog_id)))
                const resolved = index.get(String(q.source_question_id))
                // 优先用后端写入的题干快照：课件改版后历史错题仍显示当时看到的题干
                questionText = q.stem_snapshot || resolved?.stem || ''
                order = resolved?.order ?? null
                const key = answerMap.get(`${q.source_blog_id}-${String(q.source_question_id)}`)
                correctAnswer = key?.answerText || ''
                autoGrade = Boolean(key?.autoGrade)
            }
            map.set(q.id, { subject, questionText, correctAnswer, autoGrade, order })
        })
        resolvedMap.value = map
        resolvedReady.value = true
    }

    return {
        resolvedMap,
        resolvedReady,
        resolvedOf,
        loadArticle,
        resolveQuestions
    }
}
