<!-- src/components/admin/ClickableTreeNode.vue -->
<template>
    <button v-if="node.isFile" type="button" class="click-file" :class="[`is-tone-${tone}`, { 'is-marked': isMarked }]"
        @click="emit('file-click', node.blogId)">
        <span class="click-file__icon"><i aria-hidden="true" class="fas fa-file-lines"></i></span>
        <span class="click-file__body">
            <span class="click-file__name">{{ node.name }}</span>
            <span class="click-file__meta">
                <span v-if="node.date" class="tag">{{ node.date }}</span>
                <span v-if="node.series" class="tag">{{ node.series }}</span>
            </span>
        </span>
        <span v-if="badgeCount > 0" class="click-file__badge is-pending">
            <i aria-hidden="true" class="fas fa-hourglass-half"></i>
            {{ badgeCount }} {{ badgeLabel }}
        </span>
        <span v-else-if="isMarked" class="click-file__badge is-marked">
            <i aria-hidden="true" class="fas fa-circle-check"></i>
            {{ markedLabel }}
        </span>
        <span class="click-file__arrow"><i aria-hidden="true" class="fas fa-arrow-right"></i></span>
    </button>

    <div v-else class="click-folder" :class="`is-tone-${tone}`">
        <button type="button" class="click-folder__toggle" :aria-expanded="expanded" @click="expanded = !expanded">
            <span class="click-folder__chevron" :class="{ 'is-collapsed': !expanded }">
                <i aria-hidden="true" class="fas fa-chevron-down"></i>
            </span>
            <span class="click-folder__icon"><i aria-hidden="true" class="fas fa-folder-open"></i></span>
            <span class="click-folder__name">{{ node.name }}</span>
            <span class="click-folder__count">{{ fileCount }} 篇</span>
        </button>

        <div v-show="expanded" class="click-folder__children" :class="{ 'is-scrollable': depth === 0 }">
            <ClickableTreeNode v-for="child in node.children"
                :key="child.name + (child.isFile ? child.blogId : '')" :node="child" :badge-map="badgeMap"
                :badge-label="badgeLabel" :marked-ids="markedIds" :marked-label="markedLabel" :tone="tone"
                :initially-expanded="initiallyExpanded" :depth="depth + 1"
                @file-click="(blogId) => emit('file-click', blogId)" />
        </div>
    </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
    node: { type: Object, required: true },
    badgeMap: { type: Map, default: () => new Map() },
    badgeLabel: { type: String, default: '题待批阅' },
    markedIds: { type: Array, default: () => [] },
    markedLabel: { type: String, default: '已设置' },
    tone: { type: String, default: 'violet' },
    initiallyExpanded: { type: Boolean, default: false },
    depth: { type: Number, default: 0 }
})

const emit = defineEmits(['file-click'])

const expanded = ref(props.initiallyExpanded)

watch(() => props.initiallyExpanded, (value) => {
    if (value) expanded.value = true
})

const fileCount = computed(() => {
    let count = 0
    const walk = (node) => {
        if (!node) return
        if (node.isFile) count += 1
        if (Array.isArray(node.children)) node.children.forEach(walk)
    }
    walk(props.node)
    return count
})

const badgeCount = computed(() => {
    if (props.node.blogId === undefined) return 0
    return Number(props.badgeMap.get(Number(props.node.blogId))) || 0
})

const isMarked = computed(() => {
    if (props.node.blogId === undefined) return false
    return props.markedIds.includes(Number(props.node.blogId))
})
</script>

<style scoped>
.is-tone-cyan { --tone-color: var(--cyan); --tone-soft: rgba(34, 211, 238, 0.12); --tone-line: rgba(34, 211, 238, 0.32); }
.is-tone-violet { --tone-color: var(--violet-2); --tone-soft: rgba(139, 92, 246, 0.14); --tone-line: rgba(139, 92, 246, 0.34); }
.is-tone-amber { --tone-color: var(--amber); --tone-soft: rgba(251, 191, 36, 0.12); --tone-line: rgba(251, 191, 36, 0.32); }
.is-tone-pink { --tone-color: var(--pink); --tone-soft: rgba(244, 114, 182, 0.12); --tone-line: rgba(244, 114, 182, 0.32); }
.is-tone-lime { --tone-color: var(--lime); --tone-soft: rgba(163, 230, 53, 0.12); --tone-line: rgba(163, 230, 53, 0.32); }

/* 文章卡片 */
.click-file {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    min-height: 56px;
    padding: 10px 14px;
    border-radius: var(--r-sm);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.03);
    color: var(--text-1);
    text-align: left;
    transition: border-color var(--dur), background var(--dur), transform var(--dur) var(--ease-out), box-shadow var(--dur);
}

.click-file:hover {
    transform: translateY(-1px);
    border-color: var(--tone-line);
    background: linear-gradient(120deg, var(--tone-soft), rgba(255, 255, 255, 0.03) 65%);
    box-shadow: 0 16px 34px -28px var(--tone-color);
}

.click-file.is-marked {
    border-color: rgba(163, 230, 53, 0.28);
}

.click-file__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    border-radius: 10px;
    background: var(--tone-soft);
    border: 1px solid var(--tone-line);
    color: var(--tone-color);
    font-size: 0.8rem;
    flex-shrink: 0;
}

.click-file__body {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1;
    min-width: 0;
}

.click-file__name {
    font-size: 0.88rem;
    font-weight: 600;
    color: var(--text-1);
    line-height: 1.35;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.click-file__meta {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
}

.click-file__meta .tag {
    font-size: 0.64rem;
    padding: 1px 7px;
}

.click-file__badge {
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

.click-file__badge.is-pending {
    color: #fde68a;
    background: rgba(251, 191, 36, 0.12);
    border: 1px solid rgba(251, 191, 36, 0.32);
}

.click-file__badge.is-marked {
    color: #d9f99d;
    background: rgba(163, 230, 53, 0.12);
    border: 1px solid rgba(163, 230, 53, 0.3);
}

.click-file__arrow {
    color: var(--text-4);
    font-size: 0.72rem;
    flex-shrink: 0;
    transition: transform var(--dur) var(--ease-out), color var(--dur);
}

.click-file:hover .click-file__arrow {
    color: var(--tone-color);
    transform: translateX(3px);
}

/* 目录 */
.click-folder {
    border-radius: var(--r-md);
    border: 1px solid var(--line);
    background: rgba(255, 255, 255, 0.025);
    overflow: hidden;
}

.click-folder__toggle {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 8px 12px;
    text-align: left;
    color: var(--text-1);
    transition: background var(--dur);
}

.click-folder__toggle:hover {
    background: var(--glass);
}

.click-folder__chevron {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    font-size: 0.62rem;
    color: var(--text-3);
    transition: transform var(--dur) var(--ease-out);
}

.click-folder__chevron.is-collapsed {
    transform: rotate(-90deg);
}

.click-folder__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 32px;
    height: 32px;
    border-radius: 10px;
    background: var(--tone-soft);
    border: 1px solid var(--tone-line);
    color: var(--tone-color);
    font-size: 0.78rem;
    flex-shrink: 0;
}

.click-folder__name {
    flex: 1;
    min-width: 0;
    font-size: 0.88rem;
    font-weight: 700;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.click-folder__count {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    color: var(--text-3);
    background: rgba(255, 255, 255, 0.05);
    border-radius: var(--r-full);
    padding: 2px 9px;
    white-space: nowrap;
}

.click-folder__children {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin: 2px 12px 12px 22px;
    padding-left: 14px;
    border-left: 1px dashed var(--line);
}

/* 只限制最外层文件夹的展开内容高度，避免嵌套文件夹出现多级滚动。 */
.click-folder__children.is-scrollable {
    max-height: 320px;
    overflow-y: auto;
    overscroll-behavior: contain;
    padding-right: 6px;
    scrollbar-width: thin;
}

@media (max-width: 600px) {
    .click-file {
        padding: 9px 11px;
        gap: 10px;
    }

    .click-file__badge {
        display: none;
    }

    .click-folder__children {
        margin-left: 12px;
        padding-left: 10px;
    }
}
</style>

<style scoped>
/* ==========================================================================
   Riso Campus：纸白文章卡、墨线、实色状态标签。
   ========================================================================== */
.click-file {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.click-file:hover {
    border-color: var(--ink);
    background: var(--paper-2);
    transform: translate(-1px, -1px);
    box-shadow: 3px 3px 0 var(--ink);
}

.click-file.is-marked {
    border-color: var(--ink);
    background: var(--paper-1);
    box-shadow: inset 5px 0 0 var(--lime), 2px 2px 0 var(--ink);
}

.click-file__icon {
    border: 1.5px solid var(--ink);
    background: var(--tone-color);
    color: var(--tone-text);
}

.click-file__name {
    color: var(--ink);
}

.click-file__meta .tag {
    background: var(--paper-2);
}

.click-file__badge {
    border: 1.5px solid var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
    transform: rotate(-2deg);
}

.click-file__badge.is-pending {
    color: var(--ink);
    background: var(--butter);
    border-color: var(--ink);
}

.click-file__badge.is-marked {
    color: var(--ink);
    background: var(--lime);
    border-color: var(--ink);
}

.click-file__arrow {
    color: var(--ink);
}

.click-file:hover .click-file__arrow {
    color: var(--ink);
}

.click-folder {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 2px 2px 0 var(--ink);
}

.click-folder__toggle {
    color: var(--ink);
}

.click-folder__toggle:hover {
    background: var(--paper-2);
}

.click-folder__chevron {
    color: var(--ink);
}

.click-folder__icon {
    border: 1.5px solid var(--ink);
    background: var(--tone-color);
    color: var(--tone-text);
}

.click-folder__name {
    color: var(--ink);
}

.click-folder__count {
    border: 1px solid var(--ink);
    background: var(--paper-2);
    color: var(--ink);
}

.click-folder__children {
    border-left: 2px solid var(--line-strong);
}

/* 学科色 */
.is-tone-cyan { --tone-color: var(--teal); --tone-text: var(--paper-1); }
.is-tone-violet { --tone-color: var(--cobalt); --tone-text: var(--paper-1); }
.is-tone-amber { --tone-color: var(--butter); --tone-text: var(--ink); }
.is-tone-pink { --tone-color: var(--pink); --tone-text: var(--ink); }
.is-tone-lime { --tone-color: var(--lime); --tone-text: var(--ink); }
</style>
