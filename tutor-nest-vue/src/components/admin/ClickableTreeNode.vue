<!-- src/components/admin/ClickableTreeNode.vue -->
<template>
    <div v-if="node.isFile" class="tree-item tree-file tree-clickable" :style="paddingStyle"
        @click="$emit('file-click', node.blogId)">
        <span class="file-icon"><i class="fas fa-file-alt"></i></span>
        <span class="file-name">{{ node.name }}</span>
        <span v-if="badgeCount > 0" class="tree-badge is-warning">
            待批阅 {{ badgeCount }}
        </span>
        <span class="file-arrow"><i class="fas fa-chevron-right"></i></span>
    </div>

    <div v-else class="tree-folder-wrapper" :style="paddingStyle">
        <div class="tree-item tree-folder" @click.stop="expanded = !expanded">
            <span class="folder-icon"><i class="fas fa-folder"></i></span>
            <span class="folder-name">{{ node.name }}</span>
            <span class="folder-count">({{ fileCount }})</span>
        </div>
        <div v-show="expanded" class="tree-children" style="padding-left: 20px;">
            <ClickableTreeNode v-for="child in node.children" :key="child.name + (child.isFile ? child.blogId : '')"
                :node="child" :depth="depth + 1" :badge-map="badgeMap"
                :initially-expanded="initiallyExpanded"
                @file-click="(blogId) => $emit('file-click', blogId)" />
        </div>
    </div>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
    node: { type: Object, required: true },
    depth: { type: Number, default: 0 },
    badgeMap: { type: Map, default: () => new Map() },
    initiallyExpanded: { type: Boolean, default: false }
})

defineEmits(['file-click'])

const expanded = ref(props.initiallyExpanded)

const paddingStyle = computed(() => ({
    paddingLeft: `${props.depth * 20 + 8}px`
}))

const fileCount = computed(() => {
    return props.node.children?.filter(c => c.isFile).length || 0
})

const badgeCount = computed(() => {
    return props.badgeMap.get(Number(props.node.blogId)) || 0
})
</script>

<style scoped>
.tree-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 12px;
    border-radius: 8px;
}

.tree-folder {
    cursor: pointer;
    user-select: none;
}

.tree-folder:hover {
    background: rgba(255, 248, 214, 0.4);
}

.tree-clickable {
    cursor: pointer;
}

.tree-clickable:hover {
    background: rgba(255, 248, 214, 0.65);
}

.file-icon {
    color: var(--teal-dark);
    width: 20px;
    text-align: center;
}

.file-name {
    flex: 1;
    font-size: 0.95rem;
}

.folder-icon {
    color: #7ab8a0;
}

.folder-name {
    flex: 1;
    font-weight: 500;
    color: #2d4a3a;
}

.folder-count {
    font-size: 0.75rem;
    color: var(--gray);
    background: rgba(120, 170, 155, 0.1);
    padding: 0 10px;
    border-radius: 30px;
}

.file-arrow {
    color: #b0c8c0;
    font-size: 0.75rem;
    margin-left: 4px;
    flex-shrink: 0;
}

.tree-children {
    border-left: 2px dashed rgba(120, 170, 155, 0.25);
    margin-left: 20px;
    padding-left: 0;
}

.tree-badge {
    background: #f6c445;
    color: #5a4300;
    font-size: 0.7rem;
    font-weight: 600;
    padding: 2px 12px;
    border-radius: 30px;
    margin-left: 6px;
    white-space: nowrap;
    display: inline-block;
    border: 1px solid rgba(200, 160, 50, 0.3);
    flex-shrink: 0;
}

.tree-badge.is-warning {
    background: #f6c445;
}

@media (max-width: 600px) {
    .tree-item {
        font-size: 0.9rem;
        padding: 6px 10px;
    }

    .tree-children {
        margin-left: 12px;
    }

    .tree-badge {
        font-size: 0.6rem;
        padding: 1px 8px;
    }
}
</style>
