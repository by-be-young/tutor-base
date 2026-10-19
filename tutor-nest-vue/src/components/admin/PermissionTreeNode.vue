<!-- src/components/admin/PermissionTreeNode.vue -->
<template>
    <div v-if="node.isFile" class="perm-file" :class="[`is-tone-${tone}`, { 'is-granted': isChecked }]">
        <label class="perm-file__label">
            <input type="checkbox" class="perm-check" :checked="isChecked" :aria-label="`授权 ${node.name}`"
                @change="onToggle" />
            <span class="perm-file__body">
                <span class="perm-file__name">{{ node.name }}</span>
                <span class="perm-file__meta">
                    <span v-if="node.date" class="tag">{{ node.date }}</span>
                    <span v-if="node.series" class="tag">{{ node.series }}</span>
                </span>
            </span>
            <span class="perm-file__state" :class="isChecked ? 'is-on' : 'is-off'">
                <i aria-hidden="true" class="fas" :class="isChecked ? 'fa-circle-check' : 'fa-circle-minus'"></i>
                {{ isChecked ? '已授权' : '未授权' }}
            </span>
        </label>
    </div>

    <div v-else class="perm-folder" :class="`is-tone-${tone}`">
        <div class="perm-folder__head">
            <button type="button" class="perm-folder__toggle" :aria-expanded="expanded"
                @click="expanded = !expanded">
                <span class="perm-folder__chevron" :class="{ 'is-collapsed': !expanded }">
                    <i aria-hidden="true" class="fas fa-chevron-down"></i>
                </span>
                <span class="perm-folder__icon"><i aria-hidden="true" class="fas fa-folder-open"></i></span>
                <span class="perm-folder__name">{{ node.name }}</span>
                <span class="perm-folder__count">{{ fileCount }} 篇</span>
            </button>

            <label v-if="fileIds.length" class="perm-all"
                :title="allChecked ? '取消全选本目录' : '全选本目录'">
                <input type="checkbox" class="perm-check" :checked="allChecked"
                    :indeterminate="someChecked && !allChecked" :aria-label="`全选 ${node.name}`"
                    @change="onToggleAll" />
                <span class="perm-all__text">{{ allChecked ? '取消全选' : '全选' }}</span>
            </label>
        </div>

        <div v-show="expanded" class="perm-folder__children">
            <PermissionTreeNode v-for="child in node.children"
                :key="child.name + (child.isFile ? child.blogId : '')" :node="child" :permissions="permissions"
                :tone="tone" :initially-expanded="initiallyExpanded"
                @toggle="(blogId, checked) => emit('toggle', blogId, checked)"
                @toggle-many="(ids, checked) => emit('toggle-many', ids, checked)" />
        </div>
    </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
    node: { type: Object, required: true },
    permissions: { type: Array, default: () => [] },
    tone: { type: String, default: 'violet' },
    initiallyExpanded: { type: Boolean, default: false }
})

const emit = defineEmits(['toggle', 'toggle-many'])

const expanded = ref(props.initiallyExpanded)

watch(() => props.initiallyExpanded, (value) => {
    if (value) expanded.value = true
})

const fileIds = computed(() => {
    const ids = []
    const walk = (node) => {
        if (!node) return
        if (node.isFile && node.blogId !== undefined) ids.push(Number(node.blogId))
        if (Array.isArray(node.children)) node.children.forEach(walk)
    }
    walk(props.node)
    return ids
})

const fileCount = computed(() => fileIds.value.length)

const isChecked = computed(() => {
    const blogId = props.node.blogId
    return blogId !== undefined && props.permissions.includes(Number(blogId))
})

const allChecked = computed(() => fileIds.value.length > 0
    && fileIds.value.every(id => props.permissions.includes(id)))

const someChecked = computed(() => fileIds.value.some(id => props.permissions.includes(id)))

function onToggle(event) {
    const blogId = props.node.blogId
    if (blogId !== undefined) {
        emit('toggle', Number(blogId), event.target.checked)
    }
}

function onToggleAll(event) {
    if (!fileIds.value.length) return
    emit('toggle-many', fileIds.value, event.target.checked)
}
</script>

<style scoped>
/* 学科色调 */
.is-tone-cyan { --tone-color: var(--cyan); --tone-soft: rgba(34, 211, 238, 0.12); --tone-line: rgba(34, 211, 238, 0.32); }
.is-tone-violet { --tone-color: var(--violet-2); --tone-soft: rgba(139, 92, 246, 0.14); --tone-line: rgba(139, 92, 246, 0.34); }
.is-tone-amber { --tone-color: var(--amber); --tone-soft: rgba(251, 191, 36, 0.12); --tone-line: rgba(251, 191, 36, 0.32); }
.is-tone-pink { --tone-color: var(--pink); --tone-soft: rgba(244, 114, 182, 0.12); --tone-line: rgba(244, 114, 182, 0.32); }
.is-tone-lime { --tone-color: var(--lime); --tone-soft: rgba(163, 230, 53, 0.12); --tone-line: rgba(163, 230, 53, 0.32); }

/* 复选框 */
.perm-check {
    appearance: none;
    -webkit-appearance: none;
    position: relative;
    width: 22px;
    height: 22px;
    flex-shrink: 0;
    border-radius: 7px;
    border: 1px solid var(--line-strong);
    background: rgba(255, 255, 255, 0.05);
    cursor: pointer;
    transition: background var(--dur), border-color var(--dur), box-shadow var(--dur);
}

.perm-check:hover {
    border-color: var(--tone-line, var(--line-accent));
}

.perm-check:checked {
    border-color: transparent;
    background: var(--grad-primary);
    box-shadow: 0 8px 18px -10px rgba(99, 102, 241, 0.95);
}

.perm-check:checked::after {
    content: '\f00c';
    font-family: 'Font Awesome 6 Free';
    font-weight: 900;
    font-size: 0.64rem;
    color: #fff;
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
}

.perm-check:indeterminate {
    border-color: var(--tone-line, var(--line-accent));
    background: var(--tone-soft, rgba(139, 92, 246, 0.2));
}

.perm-check:indeterminate::after {
    content: '';
    position: absolute;
    left: 50%;
    top: 50%;
    width: 10px;
    height: 2px;
    border-radius: 2px;
    background: #fff;
    transform: translate(-50%, -50%);
}

/* 文件卡片 */
.perm-file {
    border-radius: var(--r-sm);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
    transition: border-color var(--dur), background var(--dur), box-shadow var(--dur), transform var(--dur) var(--ease-out);
}

.perm-file:hover {
    border-color: var(--line-strong);
    background: rgba(255, 255, 255, 0.055);
    transform: translateY(-1px);
}

.perm-file.is-granted {
    border-color: var(--tone-line);
    background: linear-gradient(120deg, var(--tone-soft), rgba(255, 255, 255, 0.02) 62%);
    box-shadow: inset 3px 0 0 var(--tone-color);
}

.perm-file__label {
    display: flex;
    align-items: center;
    gap: 13px;
    min-height: 54px;
    padding: 10px 14px;
    cursor: pointer;
}

.perm-file__body {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1;
    min-width: 0;
}

.perm-file__name {
    font-size: 0.88rem;
    font-weight: 600;
    color: var(--text-1);
    line-height: 1.35;
}

.perm-file__meta {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
}

.perm-file__meta .tag {
    font-size: 0.66rem;
    padding: 1px 7px;
}

.perm-file__state {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 24px;
    padding: 0 10px;
    border-radius: var(--r-full);
    font-size: 0.68rem;
    font-weight: 700;
    white-space: nowrap;
    flex-shrink: 0;
}

.perm-file__state.is-on {
    color: #d9f99d;
    background: rgba(163, 230, 53, 0.12);
    border: 1px solid rgba(163, 230, 53, 0.3);
}

.perm-file__state.is-off {
    color: var(--text-4);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.02);
}

/* 目录卡片 */
.perm-folder {
    border-radius: var(--r-md);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.025);
    overflow: hidden;
}

.perm-folder__head {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px 8px 8px;
}

.perm-folder__toggle {
    display: flex;
    align-items: center;
    gap: 10px;
    flex: 1;
    min-width: 0;
    padding: 6px 8px;
    border-radius: var(--r-xs);
    text-align: left;
    color: var(--text-1);
    transition: background var(--dur);
}

.perm-folder__toggle:hover {
    background: var(--glass);
}

.perm-folder__chevron {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    font-size: 0.62rem;
    color: var(--text-3);
    transition: transform var(--dur) var(--ease-out);
}

.perm-folder__chevron.is-collapsed {
    transform: rotate(-90deg);
}

.perm-folder__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: var(--tone-soft, rgba(139, 92, 246, 0.14));
    border: 1px solid var(--tone-line, var(--line));
    color: var(--tone-color, var(--violet-2));
    font-size: 0.82rem;
    flex-shrink: 0;
}

.perm-folder__name {
    flex: 1;
    min-width: 0;
    font-size: 0.9rem;
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.perm-folder__count {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    color: var(--text-3);
    background: rgba(255, 255, 255, 0.05);
    border-radius: var(--r-full);
    padding: 2px 9px;
    white-space: nowrap;
}

.perm-all {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    padding: 5px 10px;
    border-radius: var(--r-full);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
    cursor: pointer;
    flex-shrink: 0;
    transition: border-color var(--dur), background var(--dur);
}

.perm-all:hover {
    border-color: var(--tone-line, var(--line-accent));
    background: var(--glass);
}

.perm-all .perm-check {
    width: 18px;
    height: 18px;
    border-radius: 6px;
}

.perm-all .perm-check:checked::after {
    font-size: 0.55rem;
}

.perm-all__text {
    font-size: 0.7rem;
    font-weight: 600;
    color: var(--text-2);
    white-space: nowrap;
}

.perm-folder__children {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin: 2px 10px 12px 22px;
    padding-left: 14px;
    border-left: 1px dashed var(--line);
}

@media (max-width: 600px) {
    .perm-file__label {
        padding: 9px 11px;
        gap: 10px;
        min-height: 50px;
    }

    .perm-file__state {
        display: none;
    }

    .perm-all__text {
        display: none;
    }

    .perm-folder__children {
        margin-left: 12px;
        padding-left: 10px;
    }
}
</style>

<style scoped>
/* ==========================================================================
   Riso Campus：纸张卡片、墨线、实色学科块与印章状态。
   ========================================================================== */
.perm-check {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
}

.perm-check:hover {
    border-color: var(--ink);
    background: var(--paper-2);
}

.perm-check:checked {
    border-color: var(--ink);
    background: var(--ink);
    box-shadow: 2px 2px 0 rgba(28, 26, 20, 0.25);
}

.perm-check:checked::after {
    color: var(--lime);
}

.perm-check:indeterminate {
    border-color: var(--ink);
    background: var(--butter);
}

.perm-check:indeterminate::after {
    background: var(--ink);
}

.perm-file {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.perm-file:hover {
    border-color: var(--ink);
    background: var(--paper-2);
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

.perm-file.is-granted {
    border-color: var(--ink);
    background: var(--paper-1);
    box-shadow: inset 5px 0 0 var(--tone-color), 2px 2px 0 var(--ink);
}

.perm-file__name {
    color: var(--ink);
}

.perm-file__meta .tag {
    background: var(--paper-2);
}

.perm-file__state {
    border: 1.5px solid var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
    transform: rotate(-2deg);
}

.perm-file__state.is-on {
    color: var(--ink);
    background: var(--lime);
    border-color: var(--ink);
}

.perm-file__state.is-off {
    color: var(--text-3);
    background: var(--paper-2);
    border-color: var(--ink);
}

.perm-folder {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.perm-folder__toggle {
    color: var(--ink);
}

.perm-folder__toggle:hover {
    background: var(--paper-2);
}

.perm-folder__chevron {
    color: var(--ink);
}

.perm-folder__icon {
    border: 1.5px solid var(--ink);
    background: var(--tone-color);
    color: var(--tone-text);
}

.perm-folder__name {
    color: var(--ink);
}

.perm-folder__count {
    border: 1px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
}

.perm-all {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.perm-all:hover {
    border-color: var(--ink);
    background: var(--paper-2);
}

.perm-all__text {
    color: var(--ink);
}

.perm-folder__children {
    border-left: 2px solid var(--line-strong);
}

/* 学科色 */
.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }
.is-tone-lime { --tone-color: var(--lime); --tone-text: var(--ink); }
</style>
