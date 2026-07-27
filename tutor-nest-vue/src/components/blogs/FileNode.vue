<!-- src/components/blogs/FileNode.vue -->
<template>
    <div class="tree-item tree-file" :style="paddingStyle" @click="$emit('click')">
        <span class="file-icon"><i class="fas fa-file-alt"></i></span>
        <span class="file-name">
            {{ node.name }}
            <span v-if="hasUnsubmitted" class="file-warning">
                <i class="fas fa-circle-exclamation"></i>
            </span>
        </span>
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

const hasUnsubmitted = computed(() => {
    return props.statusMap.get(Number(props.node.blogId)) || false
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

.file-warning {
    color: #d57587;
    font-size: 1.1rem;
    animation: pulse-warning 1.5s ease-in-out infinite;
}

@keyframes pulse-warning {

    0%,
    100% {
        opacity: 1;
    }

    50% {
        opacity: 0.4;
    }
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
}
</style>