<!-- src/components/blogs/FolderNode.vue -->
<template>
    <div class="tree-folder-wrapper" :style="paddingStyle">
        <div class="tree-item tree-folder" @click.stop="toggleExpand">
            <span class="folder-toggle">
                <i :class="`fas ${isExpanded ? 'fa-chevron-down' : 'fa-chevron-right'}`"></i>
            </span>
            <span class="folder-icon"><i class="fas fa-folder"></i></span>
            <span class="folder-name">{{ node.name }}</span>
            <span class="folder-count">({{ fileCount }})</span>
        </div>
        <div class="tree-children" v-show="isExpanded" :style="{ paddingLeft: '20px' }">
            <template v-for="child in node.children" :key="child.name">
                <FolderNode v-if="!child.isFile" :node="child" :depth="depth + 1" :status-map="statusMap" />
                <FileNode v-else :node="child" :depth="depth + 1" :status-map="statusMap"
                    @click="$emit('file-click', child.blogId)" />
            </template>
        </div>
    </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import FileNode from './FileNode.vue'

const props = defineProps({
    node: {
        type: Object,
        required: true
    },
    depth: {
        type: Number,
        default: 0
    },
    statusMap: {
        type: Map,
        default: () => new Map()
    }
})

const emit = defineEmits(['file-click'])

const fileCount = computed(() => {
    return props.node.children?.filter(c => c.isFile).length || 0
})

const hasUnsubmittedChild = computed(() => {
    function checkNode(n) {
        if (n.isFile) {
            return props.statusMap.get(Number(n.blogId)) || false
        }
        return n.children?.some(checkNode) || false
    }
    return props.node.children?.some(checkNode) || false
})

const isExpanded = ref(hasUnsubmittedChild.value)
const paddingStyle = computed(() => ({
    paddingLeft: `${props.depth * 20 + 8}px`
}))

function toggleExpand() {
    isExpanded.value = !isExpanded.value
}
</script>

<style scoped>
.tree-item {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    border-radius: 8px;
    cursor: pointer;
    transition: background 0.2s;
    gap: 8px;
    font-size: 0.95rem;
}

.tree-item:hover {
    background: rgba(255, 255, 255, 0.4);
}

.tree-folder {
    font-weight: 500;
    color: #2d4a3a;
}

.folder-toggle {
    width: 20px;
    text-align: center;
    color: var(--gray);
}

.folder-icon {
    color: #7ab8a0;
    margin-right: 4px;
}

.folder-name {
    flex: 1;
}

.folder-count {
    font-size: 0.75rem;
    color: var(--gray);
    background: rgba(120, 170, 155, 0.1);
    padding: 0 10px;
    border-radius: 30px;
}

.tree-children {
    border-left: 2px dashed rgba(120, 170, 155, 0.3);
}

@media (min-width: 1024px) {
    .tree-item {
        font-size: 1.1rem;
        padding: 10px 16px;
    }
}

@media (max-width: 640px) {
    .tree-item {
        font-size: 0.88rem;
        padding: 6px 8px;
    }
}
</style>