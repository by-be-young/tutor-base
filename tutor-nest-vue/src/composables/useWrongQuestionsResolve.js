// src/composables/useWrongQuestionsResolve.js
// 错题本 / 错题训练共用的展示解析逻辑：
// 后端返回当前账户可见的错题与正确答案；静态文章 markdown 只用于补充题干、学科和顺序号。
import { ref } from 'vue'
import { useArticleStore } from '@/stores/blogStore'
import { resolveQuestionText, resolveQuestionOrder } from '@/utils/questionText'

export function useWrongQuestionsResolve() {
    const blogStore = useArticleStore()

    const resolvedMap = ref(new Map())   // 错题 id → { subject, questionText, correctAnswer, order }
    const resolvedReady = ref(false)     // 动态解析是否完成
    const articleCache = new Map()       // blogId → markdown（缓存）

    function resolvedOf(q) {
        return resolvedMap.value.get(q.id) || {}
    }

    async function loadArticle(blogId) {
        const key = String(blogId)
        if (articleCache.has(key)) return articleCache.get(key)
        const blog = blogStore.blogData.find(b => Number(b.id) === Number(blogId))
        let md = null
        if (blog) {
            try {
                const res = await fetch(`${import.meta.env.BASE_URL}articles/${blog.path}`)
                if (res.ok) md = await res.text()
            } catch {
                md = null
            }
        }
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
                subject = blog?.series || ''
                const md = articleCache.get(String(q.source_blog_id))
                if (md) {
                    questionText = resolveQuestionText(md, q.source_question_id)
                    order = resolveQuestionOrder(md, q.source_question_id)
                }
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
