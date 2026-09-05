import { defineStore } from 'pinia'
import { ref } from 'vue'
import { wrongBookGateway } from '@/gateways/wrongBookGateway'

function normalizeEntry(entry) {
    return {
        id: entry.id, student_id: entry.learnerId,
        correct_answer: entry.correctAnswer || '', my_answer: entry.myAnswer || '',
        wrong_reason: entry.wrongReason || '', tags: entry.tags || [], note: entry.note || '',
        source_blog_id: entry.sourceArticleId, source_question_id: entry.sourceQuestionId,
        is_manual: entry.manual, mastered: entry.mastered, removed: false,
        wrong_count: entry.wrongCount, created_at: entry.createdAt, updated_at: entry.updatedAt
    }
}

function toApiPatch(data) {
    const patch = {}
    if ('my_answer' in data) patch.myAnswer = data.my_answer
    if ('wrong_reason' in data) patch.wrongReason = data.wrong_reason
    if ('tags' in data) patch.tags = data.tags
    if ('note' in data) patch.note = data.note
    if ('mastered' in data) patch.mastered = Boolean(data.mastered)
    return patch
}

export const useWrongQuestionsStore = defineStore('wrongQuestions', () => {
    const questions = ref([])
    const isLoading = ref(false)
    const error = ref(null)

    const getStudentId = user => user?.learnerId ?? user?.id ?? null

    async function fetchQuestions(studentId) {
        if (!studentId) {
            questions.value = []
            return []
        }
        isLoading.value = true
        error.value = null
        try {
            questions.value = (await wrongBookGateway.list()).map(normalizeEntry)
            return questions.value
        } catch (cause) {
            console.error('加载错题失败:', cause)
            error.value = cause.message
            return []
        } finally {
            isLoading.value = false
        }
    }

    async function updateQuestion(id, data) {
        const updated = normalizeEntry(await wrongBookGateway.update(id, toApiPatch(data)))
        const index = questions.value.findIndex(question => question.id === id)
        if (index !== -1) questions.value[index] = updated
        return updated
    }

    const toggleMastered = (id, mastered) => updateQuestion(id, { mastered: Boolean(mastered) })

    async function deleteQuestion(id) {
        await wrongBookGateway.remove(id)
        questions.value = questions.value.filter(question => question.id !== id)
        return null
    }

    async function collectManual({ sourceArticleId, sourceQuestionId, myAnswer }) {
        const entry = normalizeEntry(await wrongBookGateway.collect(
            sourceArticleId, String(sourceQuestionId), myAnswer || ''))
        questions.value.unshift(entry)
        return entry
    }

    return { questions, isLoading, error, getStudentId, fetchQuestions, updateQuestion,
        toggleMastered, deleteQuestion, collectManual }
})
