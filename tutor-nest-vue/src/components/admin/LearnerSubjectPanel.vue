<!-- src/components/admin/LearnerSubjectPanel.vue -->
<template>
    <div class="panel subject-panel">
        <header class="panel-head subject-head">
            <div class="subject-head__copy">
                <h2 class="panel-title">
                    <i aria-hidden="true" class="fas fa-book-open"></i>
                    学员科目
                </h2>
                <p class="text-sm text-muted">
                    先在这里给学员设定所学科目：开通整科即包含该科目下的全部文章，保存后立即生效。个别文章可在「权限管理」里微调。
                </p>
            </div>
            <span class="chip chip--cyan subject-head__student">
                <i aria-hidden="true" class="fas fa-user-graduate"></i>
                {{ currentStudentName }}
            </span>
        </header>

        <div class="panel-body subject-body">
            <EmptyState v-if="!selectedStudentId" icon="fas fa-user-graduate" title="尚未选择学员"
                text="在顶部切换或新增学员后，即可为他设定所学科目。" />

            <div v-else-if="allSubjects.length === 0" class="subject-skeletons" aria-hidden="true">
                <div class="skeleton subject-skeleton"></div>
                <div class="skeleton subject-skeleton"></div>
                <div class="skeleton subject-skeleton"></div>
            </div>

            <template v-else>
                <div class="subject-grid" role="group" aria-label="学员科目设定">
                    <button v-for="item in subjectCards" :key="item.subject" type="button" class="subject-card"
                        :class="[`is-tone-${item.tone}`, { 'is-enrolled': item.enrolled }]"
                        :aria-pressed="item.enrolled"
                        :aria-label="`${item.enrolled ? '取消' : '开通'} ${item.subject} 科目`"
                        @click="toggleSubject(item.subject)">
                        <span class="subject-card__mark" aria-hidden="true">
                            <i class="fas" :class="item.enrolled ? 'fa-circle-check' : 'fa-circle'"></i>
                        </span>
                        <span class="subject-card__name">{{ item.subject }}</span>
                        <span class="subject-card__state">{{ item.enrolled ? '在学' : '未开通' }}</span>
                        <span v-if="item.partial" class="subject-card__hint">仅部分文章已授权</span>
                    </button>
                </div>

                <p class="subject-tip">
                    <i aria-hidden="true" class="fas fa-circle-info"></i>
                    点击科目卡片即可整科开通或取消；取消整科会一并移除该科目下已授权的文章。
                </p>
            </template>
        </div>

        <footer class="subject-footer">
            <div class="subject-footer__info">
                <span class="chip" :class="dirty ? 'chip--amber' : 'chip--lime'">
                    <i aria-hidden="true" class="fas" :class="dirty ? 'fa-pen' : 'fa-circle-check'"></i>
                    {{ dirty ? '有未保存改动' : '已同步' }}
                </span>
            </div>
            <button type="button" class="btn btn--primary" :disabled="!dirty || saving"
                @click="emit('save-permissions')">
                <i aria-hidden="true" class="fas" :class="saving ? 'fa-circle-notch fa-spin' : 'fa-cloud-arrow-up'"></i>
                {{ saving ? '保存中…' : '保存科目' }}
            </button>
        </footer>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { buildSubjectScope, subjectToggleIds } from '@/utils/adminSubjects'
import EmptyState from '@/components/ui/EmptyState.vue'

const props = defineProps({
    students: { type: Array, default: () => [] },
    selectedStudentId: { type: [String, Number], default: null },
    blogData: { type: Array, default: () => [] },
    allSubjects: { type: Array, default: () => [] },
    dirty: { type: Boolean, default: false },
    saving: { type: Boolean, default: false }
})

const emit = defineEmits(['permission-change', 'save-permissions'])

const adminStore = useAdminStore()

function subjectTone(subject) {
    if (!subject) return 'violet'
    if (subject.includes('初中英语')) return 'amber'
    if (subject.includes('化学')) return 'cyan'
    if (subject.includes('英语')) return 'violet'
    return 'pink'
}

const currentStudent = computed(() => props.students
    .find(item => String(item.id) === String(props.selectedStudentId)) || null)
const currentStudentName = computed(() => currentStudent.value?.username || '未选择学员')
const currentPermissions = computed(() => currentStudent.value?.permissions || [])

const scope = computed(() => buildSubjectScope({
    subjects: props.allSubjects,
    articles: props.blogData,
    permissions: currentPermissions.value
}))

// 整科开通即「在学」；部分文章已授权时提示可在权限管理里微调
const subjectCards = computed(() => scope.value.map(item => ({
    subject: item.subject,
    enrolled: item.granted > 0,
    partial: item.granted > 0 && item.granted < item.total,
    tone: subjectTone(item.subject)
})))

function toggleSubject(subject) {
    const { ids, checked } = subjectToggleIds({
        articles: props.blogData,
        permissions: currentPermissions.value,
        subject
    })
    if (ids.length === 0) return
    ids.forEach(blogId => adminStore.updatePermissions(blogId, checked))
    emit('permission-change')
}
</script>

<style scoped>
.subject-panel {
    overflow: hidden;
}

.subject-head {
    align-items: flex-start;
    gap: 16px;
}

.subject-head__copy {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
}

.subject-panel .panel-title i {
    color: var(--vermilion);
}

.subject-head__student {
    box-shadow: 2px 2px 0 var(--ink);
    flex-shrink: 0;
}

.subject-body {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.subject-skeletons {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 12px;
}

.subject-skeleton {
    height: 96px;
    border-radius: var(--r-md);
}

.subject-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
    gap: 12px;
}

.subject-card {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    padding: 16px 14px;
    border-radius: var(--r-md);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
    color: var(--ink);
    text-align: left;
    transition: transform var(--dur) var(--ease-out), box-shadow var(--dur);
}

.subject-card:hover {
    transform: translate(-1px, -1px);
    box-shadow: 4px 4px 0 var(--ink);
}

.subject-card.is-enrolled {
    background: var(--paper-1);
    border-color: var(--ink);
}

.subject-card.is-enrolled::after {
    content: '';
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--tone-color, var(--teal));
}

.subject-card__mark {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 8px;
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--text-3);
    flex-shrink: 0;
}

.subject-card.is-enrolled .subject-card__mark {
    background: var(--tone-color, var(--teal));
    color: var(--tone-text, var(--paper-1));
}

.subject-card__name {
    font-family: var(--font-display);
    font-size: 0.95rem;
    font-weight: 800;
    color: var(--ink);
}

.subject-card__state {
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--text-3);
}

.subject-card.is-enrolled .subject-card__state {
    color: var(--ink);
}

.subject-card__hint {
    flex-basis: 100%;
    font-size: 0.68rem;
    color: var(--text-3);
}

.subject-tip {
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--text-3);
    font-size: 0.78rem;
}

.subject-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
    padding: 16px 24px;
    border-top: 1.5px solid var(--ink);
    background: var(--paper-2);
}

.subject-footer__info {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.subject-footer .chip {
    box-shadow: 2px 2px 0 var(--ink);
}

.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }

@media (max-width: 760px) {
    .subject-body {
        padding: 18px;
    }

    .subject-grid {
        grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
    }

    .subject-footer {
        padding: 14px 18px;
    }

    .subject-footer .btn {
        width: 100%;
    }
}

@media (max-width: 420px) {
    .subject-grid {
        grid-template-columns: minmax(0, 1fr);
    }
}
</style>
