<!-- src/components/admin/StudentSwitcher.vue -->
<template>
    <div ref="rootRef" class="switcher">
        <button ref="triggerRef" type="button" class="switcher__trigger" :class="{ 'is-open': open }"
            aria-haspopup="listbox" :aria-expanded="open ? 'true' : 'false'" aria-label="切换学员" @click="toggleMenu">
            <span class="avatar switcher__avatar" aria-hidden="true">{{ initialOf(currentStudent?.username) }}</span>
            <span class="switcher__current">
                <span class="switcher__name">{{ currentStudent?.username || '未选择学员' }}</span>
                <span class="switcher__meta">
                    {{ currentStudent ? currentActivityLabel : '点击选择学员' }}
                </span>
            </span>
            <span v-if="currentStudent && isTestStudent(currentStudent)" class="chip switcher__test-flag"
                :class="testBadgeClass(currentStudent)" :title="testBadgeTitle(currentStudent)"
                :aria-label="testBadgeTitle(currentStudent)">
                {{ testBadgeLabel(currentStudent) }}
            </span>
            <span v-if="currentStudent" class="switcher__claim" :class="`is-${currentClaimState}`">
                {{ currentStudent && currentClaimState === 'mine' ? '我的' : currentClaimState === 'unclaimed' ? '未认领' : `由 ${currentStudent.claimedByUsername || '其他管理员'} 认领` }}
            </span>
            <i class="fas fa-chevron-down switcher__caret" aria-hidden="true"></i>
        </button>

        <div v-if="open" ref="panelRef" class="switcher__panel" role="listbox" aria-label="学员列表"
            tabindex="-1" @keydown="onPanelKeydown">
            <div v-if="students.length > 0" class="switcher__search">
                <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
                <input ref="searchRef" v-model="query" class="switcher__input" type="search" autocomplete="off"
                    placeholder="搜索学员用户名" aria-label="搜索学员" />
            </div>

            <div ref="listRef" class="switcher__list">
                <p v-if="students.length === 0" class="switcher__empty">
                    还没有学员，请先到「学生账户」或顶部「新增」创建第一位学员。
                </p>
                <p v-else-if="options.length === 0" class="switcher__empty">
                    没有匹配“{{ query }}”的学员。
                </p>

                <template v-else>
                    <div v-for="section in sections" :key="section.key" class="switcher__group" role="group"
                        :aria-label="section.title">
                        <p class="switcher__group-title">
                            {{ section.title }}
                            <span class="switcher__group-count">{{ section.items.length }}</span>
                        </p>
                        <div v-for="student in section.items" :key="student.id" :id="`switcher-option-${student.id}`"
                            class="switcher__option"
                            :class="{ 'is-current': isCurrent(student), 'is-highlighted': isHighlighted(student) }"
                            role="option" :aria-selected="isCurrent(student)" @mouseenter="highlight(student)"
                            @click="choose(student)">
                            <span class="avatar switcher__avatar" aria-hidden="true">{{ initialOf(student.username) }}</span>
                            <span class="switcher__option-main">
                                <span class="switcher__option-name">
                                    {{ student.username }}
                                    <span v-if="isTestStudent(student)" class="chip switcher__test-chip"
                                        :class="testBadgeClass(student)" :title="testBadgeTitle(student)"
                                        :aria-label="testBadgeTitle(student)">
                                        {{ testBadgeLabel(student) }}
                                    </span>
                                    <span v-if="adminStore.hasUnsavedChanges(student.id)" class="switcher__dirty"
                                        role="img" aria-label="有未保存的权限改动"></span>
                                </span>
                                <span class="switcher__option-sub">
                                    <span><i class="far fa-clock" aria-hidden="true"></i>
                                        {{ formatRelativeActivity(student.lastActivityAt) }}</span>
                                    <span aria-hidden="true">·</span>
                                    <span class="switcher__claim" :class="`is-${claimState(student, accountId)}`">
                                        {{ claimLabel(student) }}
                                    </span>
                                </span>
                            </span>
                            <span class="switcher__row-actions" @click.stop>
                                <button v-if="claimState(student, accountId) === 'unclaimed'" type="button"
                                    class="switcher__row-btn" :disabled="busyStudentId !== null"
                                    @click.stop="claim(student)">
                                    {{ busyStudentId === student.id ? '认领中…' : '认领' }}
                                </button>
                                <button v-else-if="claimState(student, accountId) === 'mine'" type="button"
                                    class="switcher__row-btn" :disabled="busyStudentId !== null"
                                    @click.stop="release(student)">
                                    {{ busyStudentId === student.id ? '释放中…' : '释放' }}
                                </button>
                            </span>
                        </div>
                    </div>
                </template>
            </div>

            <p v-if="students.length > 0 && options.length > 0" class="switcher__hint">
                <span class="kbd">↑</span><span class="kbd">↓</span> 移动
                <span class="kbd">Enter</span> 选择
                <span class="kbd">Esc</span> 关闭
            </p>
        </div>
    </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useAdminStore } from '@/stores/adminStore'
import { useAuthStore } from '@/stores/authStore'
import {
    claimState,
    formatRelativeActivity,
    groupStudentsForSwitcher,
    isSpecialTestStudent,
    isTestStudent,
    studentMatchesQuery
} from '@/utils/adminStudents'
import { showToast } from '@/utils/toast'

const emit = defineEmits(['select'])

const adminStore = useAdminStore()
const authStore = useAuthStore()

const rootRef = ref(null)
const triggerRef = ref(null)
const panelRef = ref(null)
const listRef = ref(null)
const searchRef = ref(null)

const open = ref(false)
const query = ref('')
const highlightedIndex = ref(0)
const busyStudentId = ref(null)

const accountId = computed(() => authStore.currentUser?.accountId ?? null)
const students = computed(() => adminStore.students)
const currentStudent = computed(() => adminStore.currentStudent)
const currentClaimState = computed(() => claimState(currentStudent.value, accountId.value))
const currentActivityLabel = computed(() => formatRelativeActivity(currentStudent.value?.lastActivityAt))

const filteredStudents = computed(() =>
    students.value.filter(student => studentMatchesQuery(student, query.value))
)

const groups = computed(() => groupStudentsForSwitcher(filteredStudents.value, accountId.value))

const sections = computed(() => [
    { key: 'mine', title: '我的学员', items: groups.value.mine },
    { key: 'others', title: '其他学员', items: groups.value.others },
    { key: 'test', title: '测试学员', items: groups.value.test }
].filter(section => section.items.length > 0))

const options = computed(() => [
    ...groups.value.mine,
    ...groups.value.others,
    ...groups.value.test
])

function testBadgeLabel(student) {
    return isSpecialTestStudent(student) ? '特殊测试' : '测试'
}

function testBadgeTitle(student) {
    return isSpecialTestStudent(student)
        ? '特殊测试学员：拥有全部课件权限，积分固定为 999999999 且不会减少'
        : '测试学员：用于功能验证，不计入活跃度统计与学员动态'
}

function testBadgeClass(student) {
    return isSpecialTestStudent(student) ? 'chip--coral' : 'chip--amber'
}

function initialOf(username) {
    return Array.from(String(username || '?'))[0]?.toUpperCase() || '?'
}

function claimLabel(student) {
    const state = claimState(student, accountId.value)
    if (state === 'mine') return '我的'
    if (state === 'unclaimed') return '未认领'
    return `由 ${student.claimedByUsername || '其他管理员'} 认领`
}

function isCurrent(student) {
    return String(student.id) === String(adminStore.currentStudentId)
}

function isHighlighted(student) {
    return options.value[highlightedIndex.value]?.id === student.id
}

function highlight(student) {
    const index = options.value.findIndex(option => option.id === student.id)
    if (index >= 0) highlightedIndex.value = index
}

async function focusSearch() {
    await nextTick()
    if (searchRef.value) searchRef.value.focus()
    else panelRef.value?.focus()
    panelRef.value?.querySelector('.switcher__list')?.scrollTo({ top: 0 })
}

async function openMenu() {
    open.value = true
    query.value = ''
    const currentIndex = options.value.findIndex(option => isCurrent(option))
    highlightedIndex.value = currentIndex >= 0 ? currentIndex : 0
    await focusSearch()
}

function closeMenu(restoreFocus = false) {
    if (!open.value) return
    open.value = false
    query.value = ''
    if (restoreFocus) triggerRef.value?.focus()
}

function toggleMenu() {
    if (open.value) closeMenu(true)
    else openMenu()
}

function choose(student) {
    emit('select', student.id)
    closeMenu(true)
}

function moveHighlight(step) {
    if (options.value.length === 0) return
    const next = Math.min(Math.max(highlightedIndex.value + step, 0), options.value.length - 1)
    highlightedIndex.value = next
    nextTick(() => {
        listRef.value?.querySelector('.is-highlighted')?.scrollIntoView({ block: 'nearest' })
    })
}

function onPanelKeydown(event) {
    // 行内「认领/释放」按钮获得焦点时，Enter 交给按钮自身处理，避免同时切换学员。
    if (event.key === 'Enter' && event.target instanceof HTMLElement
        && event.target.closest('.switcher__row-actions')) {
        return
    }

    if (event.key === 'ArrowDown') {
        event.preventDefault()
        moveHighlight(1)
    } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        moveHighlight(-1)
    } else if (event.key === 'Enter') {
        const student = options.value[highlightedIndex.value]
        if (student) {
            event.preventDefault()
            choose(student)
        }
    } else if (event.key === 'Escape') {
        event.preventDefault()
        closeMenu(true)
    }
}

function onDocumentPointerDown(event) {
    if (!open.value) return
    if (rootRef.value && !rootRef.value.contains(event.target)) closeMenu()
}

async function claim(student) {
    if (busyStudentId.value !== null) return
    busyStudentId.value = student.id
    try {
        await adminStore.claimStudent(student.id)
        showToast(`已认领学员“${student.username}”`, 'success')
    } catch (error) {
        showToast(error.message || '认领学员失败，请稍后重试', 'error')
    } finally {
        busyStudentId.value = null
    }
}

async function release(student) {
    if (busyStudentId.value !== null) return
    busyStudentId.value = student.id
    try {
        await adminStore.releaseStudent(student.id)
        showToast(`已释放学员“${student.username}”的认领`, 'success')
    } catch (error) {
        showToast(error.message || '释放认领失败，请稍后重试', 'error')
    } finally {
        busyStudentId.value = null
    }
}

watch(query, () => {
    highlightedIndex.value = 0
})

onMounted(() => {
    document.addEventListener('mousedown', onDocumentPointerDown)
})

onBeforeUnmount(() => {
    document.removeEventListener('mousedown', onDocumentPointerDown)
})
</script>

<style scoped>
.switcher {
    position: relative;
    min-width: 0;
    width: 100%;
}

.switcher__trigger {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    min-height: 44px;
    padding: 5px 11px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    background: var(--paper-1);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
    text-align: left;
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out), background var(--dur-fast);
}

.switcher__trigger:hover,
.switcher__trigger.is-open {
    transform: translate(-1px, -1px);
    background: var(--paper-2);
    box-shadow: 3px 3px 0 var(--ink);
}

.switcher__avatar {
    width: 28px;
    height: 28px;
    font-size: 0.72rem;
    background: var(--butter);
    border: 1.5px solid var(--ink);
}

.switcher__current {
    display: flex;
    flex-direction: column;
    gap: 1px;
    flex: 1 1 auto;
    min-width: 0;
    line-height: 1.2;
}

.switcher__name {
    font-size: 0.85rem;
    font-weight: 700;
    color: var(--ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.switcher__meta {
    font-family: var(--font-mono);
    font-size: 0.6rem;
    letter-spacing: 0.04em;
    color: var(--text-3);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.switcher__claim {
    display: inline-flex;
    align-items: center;
    height: 21px;
    padding: 0 8px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-2);
    color: var(--ink);
    font-size: 0.62rem;
    font-weight: 700;
    white-space: nowrap;
    flex-shrink: 0;
}

.switcher__claim.is-mine {
    background: var(--lime);
}

.switcher__claim.is-unclaimed {
    background: var(--butter);
}

/* 测试/特殊测试标记：沿用全局 chip 配色，只在切换器内收小尺寸 */
.switcher__test-flag {
    flex-shrink: 0;
    height: 21px;
    padding: 0 8px;
    font-size: 0.62rem;
}

.switcher__test-chip {
    height: 18px;
    padding: 0 7px;
    font-size: 0.58rem;
    flex-shrink: 0;
}

.switcher__caret {
    flex-shrink: 0;
    font-size: 0.66rem;
    color: var(--text-3);
    transition: transform var(--dur);
}

.switcher__trigger.is-open .switcher__caret {
    transform: rotate(180deg);
}

.switcher__panel {
    position: absolute;
    top: calc(100% + 10px);
    left: 0;
    z-index: 40;
    display: flex;
    flex-direction: column;
    width: min(380px, calc(100vw - 40px));
    max-height: min(440px, 64vh);
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    box-shadow: 5px 5px 0 var(--ink);
    overflow: hidden;
}

.switcher__search {
    position: relative;
    display: flex;
    align-items: center;
    padding: 10px 12px;
    border-bottom: 1.5px solid var(--ink);
    background: var(--paper-1);
    flex-shrink: 0;
}

.switcher__search > i {
    position: absolute;
    left: 25px;
    color: var(--text-3);
    font-size: 0.76rem;
    pointer-events: none;
}

.switcher__input {
    width: 100%;
    height: 36px;
    padding: 0 12px 0 36px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    background: var(--paper-0);
    color: var(--text-1);
    font-size: 0.82rem;
}

.switcher__input:focus {
    outline: none;
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.switcher__list {
    min-height: 0;
    overflow-y: auto;
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    scrollbar-width: thin;
}

.switcher__group {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.switcher__group + .switcher__group {
    margin-top: 6px;
}

.switcher__group-title {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 8px 4px;
    font-family: var(--font-mono);
    font-size: 0.58rem;
    letter-spacing: 0.16em;
    text-transform: uppercase;
    color: var(--text-3);
}

.switcher__group-count {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border: 1px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-2);
    color: var(--ink);
    font-size: 0.6rem;
    font-weight: 700;
}

.switcher__option {
    display: flex;
    align-items: center;
    gap: 9px;
    padding: 7px 8px;
    border: 1.5px solid transparent;
    border-radius: var(--r-sm);
    cursor: pointer;
    transition: background var(--dur-fast), border-color var(--dur-fast);
}

.switcher__option.is-highlighted {
    border-color: var(--ink);
    background: var(--paper-2);
}

.switcher__option.is-current {
    border-color: var(--ink);
    background: var(--lime);
    box-shadow: 2px 2px 0 var(--ink);
}

.switcher__option.is-current.is-highlighted {
    background: var(--lime);
}

.switcher__option-main {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1 1 auto;
    min-width: 0;
}

.switcher__option-name {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 0.81rem;
    font-weight: 700;
    color: var(--ink);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.switcher__dirty {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    border: 1px solid var(--ink);
    background: var(--vermilion);
    flex-shrink: 0;
}

.switcher__option-sub {
    display: flex;
    align-items: center;
    gap: 5px;
    flex-wrap: wrap;
    font-size: 0.64rem;
    color: var(--text-3);
}

.switcher__option-sub .switcher__claim {
    height: 18px;
    padding: 0 6px;
    font-size: 0.58rem;
    border-width: 1px;
}

.switcher__row-actions {
    display: flex;
    align-items: center;
    gap: 5px;
    flex-shrink: 0;
}

.switcher__row-btn {
    height: 24px;
    padding: 0 9px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-1);
    color: var(--ink);
    font-size: 0.62rem;
    font-weight: 700;
    box-shadow: 1px 1px 0 var(--ink);
}

.switcher__row-btn:hover:not(:disabled) {
    background: var(--butter);
}

.switcher__row-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    box-shadow: none;
}

.switcher__empty {
    padding: 24px 16px;
    text-align: center;
    color: var(--text-3);
    font-size: 0.8rem;
}

.switcher__hint {
    display: flex;
    align-items: center;
    gap: 7px;
    flex-shrink: 0;
    padding: 8px 12px;
    border-top: 1.5px solid var(--ink);
    background: var(--paper-2);
    color: var(--text-3);
    font-size: 0.64rem;
}

@media (max-width: 760px) {
    .switcher__panel {
        width: min(360px, calc(100vw - 44px));
        max-height: min(360px, 58vh);
    }
}

@media (max-width: 420px) {
    .switcher__panel {
        width: min(320px, calc(100vw - 36px));
    }

    .switcher__trigger .switcher__claim {
        display: none;
    }
}
</style>
