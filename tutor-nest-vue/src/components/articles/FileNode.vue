<!-- src/components/articles/FileNode.vue -->
<template>
    <div class="tree-item tree-file" :style="paddingStyle" @click="$emit('click')">
        <span class="file-icon"><i class="fas fa-file-alt"></i></span>
        <span class="file-name">
            {{ node.name }}
        </span>
        <span v-if="pendingCount > 0" class="file-pending-badge">{{ pendingCount }}题待提交</span>
        <span class="file-arrow"><i class="fas fa-chevron-right"></i></span>
    </div>
</template>

<script setup>
import { computed } from 'vue'

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

defineEmits(['click'])

const pendingCount = computed(() => {
    return props.statusMap.get(Number(props.node.blogId)) || 0
})

const paddingStyle = computed(() => ({
    paddingLeft: `${props.depth * 20 + 40}px`
}))
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

.tree-file {
    color: #2d4a3a;
}

.file-icon {
    color: var(--teal-dark);
    margin-right: 4px;
    width: 20px;
    text-align: center;
}

.file-name {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
}

.file-arrow {
    color: var(--gray);
    opacity: 0.3;
    transition: 0.3s;
}

.tree-file:hover .file-arrow {
    opacity: 0.9;
    transform: translateX(4px);
}

.file-pending-badge {
    background: #f6c445;
    color: #5a4300;
    font-size: 0.7rem;
    font-weight: 600;
    padding: 2px 10px;
    border-radius: 30px;
    white-space: nowrap;
    flex-shrink: 0;
    border: 1px solid rgba(200, 160, 50, 0.3);
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

    .tree-file {
        padding-left: 24px !important;
    }

    .file-pending-badge {
        font-size: 0.6rem;
        padding: 1px 6px;
    }
}
</style>
