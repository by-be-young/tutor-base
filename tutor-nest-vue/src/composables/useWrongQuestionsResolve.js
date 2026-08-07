// src/composables/useWrongQuestionsResolve.js
// 错题本 / 错题训练共用的动态解析逻辑：
// 错题表只存来源（source_blog_id + source_question_id），
// 展示时从文章 markdown 与答案表动态解析题干、学科、正确答案、顺序号
import { ref } from 'vue'
import { useArticleStore } from '@/stores/blogStore'
import { supabase } from '@/utils/supabase'
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

    async function loadAnswerKeys(blogIds) {
        if (!blogIds.length) return new Map()
        const { data, error } = await supabase
            .from('article_answer_keys')
            .select('blog_id, question_id, answer_text, auto_grade')
            .in('blog_id', blogIds)

        if (error) {
            console.error('加载答案数据失败:', error)
            return new Map()
        }
        const map = new Map()
            ; (data || []).forEach(k => map.set(`${k.blog_id}-${String(k.question_id)}`, {
                answerText: k.answer_text || '',
                autoGrade: Boolean(k.auto_grade)
            }))
        return map
    }

    /** 解析指定错题列表的动态数据（不依赖 store 中的 questions） */
    async function resolveQuestions(list) {
        resolvedReady.value = false
        const blogIds = [...new Set(list.map(q => q.source_blog_id).filter(b => b != null))]

        await Promise.all(blogIds.map(loadArticle))
        const answerMap = await loadAnswerKeys(blogIds)

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
