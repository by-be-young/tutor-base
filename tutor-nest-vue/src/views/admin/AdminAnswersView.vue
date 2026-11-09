<!-- src/views/admin/AdminAnswersView.vue -->
<template>
    <div class="answers-view">
        <div class="panel panel--tight answers-bar">
            <div class="answers-bar__switcher">
                <span class="eyebrow">CURRENT LEARNER</span>
                <StudentSwitcher @select="handleSelectStudent" />
            </div>
            <p class="answers-bar__hint">
                <i aria-hidden="true" class="fas fa-circle-info"></i>
                答案按学员范围维护：只列出你名下、且该学员已开通科目里已授权的课件。
            </p>
        </div>

        <AnswerPanel :blog-data="blogData" :all-subjects="allSubjects" :students="adminStore.students"
            :selected-student-id="adminStore.currentStudentId" />
    </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useArticleStore } from '@/stores/blogStore'
import { useAdminStore } from '@/stores/adminStore'
import { useAuthStore } from '@/stores/authStore'
import { useCoursewareStore } from '@/stores/coursewareStore'
import { ensureArticleData } from '@/composables/useAdminWorkspace'
import { showToast } from '@/utils/toast'
import StudentSwitcher from '@/components/admin/StudentSwitcher.vue'
import AnswerPanel from '@/components/admin/AnswerPanel.vue'

const blogStore = useArticleStore()
const adminStore = useAdminStore()
const authStore = useAuthStore()
const coursewareStore = useCoursewareStore()

const blogData = computed(() => blogStore.blogData)
const allSubjects = computed(() => {
    const subjects = new Set()
    blogStore.blogData.forEach(blog => {
        if (blog.series) subjects.add(blog.series)
    })
    return Array.from(subjects).sort((left, right) => left.localeCompare(right, 'zh-CN'))
})

const accountId = computed(() => authStore.currentUser?.accountId ?? null)

function handleSelectStudent(studentId) {
    adminStore.selectStudent(studentId)
}

async function ensureStudents() {
    if (adminStore.students.length > 0) return
    try {
        await adminStore.loadStudents()
        adminStore.selectInitialStudent(accountId.value)
    } catch (error) {
        showToast(error?.message || '加载学员列表失败，请稍后重试', 'error')
    }
}

onMounted(async () => {
    await ensureStudents()
    try {
        await ensureArticleData(blogStore)
        await coursewareStore.loadWorkspace(blogStore.blogData)
    } catch (error) {
        showToast(error?.message || '加载课件目录失败，请稍后重试', 'error')
    }
})
</script>

<style scoped>
.answers-view {
    display: flex;
    flex-direction: column;
    gap: 18px;
}

.answers-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
}

.answers-bar__switcher {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 0 1 360px;
    min-width: 240px;
}

.answers-bar__hint {
    display: flex;
    align-items: center;
    gap: 7px;
    margin: 0;
    font-size: 0.78rem;
    line-height: 1.5;
    color: var(--text-2);
}

.answers-bar__hint i {
    color: var(--text-3);
}

@media (max-width: 760px) {
    .answers-bar {
        align-items: flex-start;
    }

    .answers-bar__switcher {
        flex-basis: 100%;
    }
}

@media (max-width: 520px) {
    .answers-bar__switcher {
        flex-direction: column;
        align-items: stretch;
        gap: 6px;
    }
}
</style>
