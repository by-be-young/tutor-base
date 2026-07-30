<template>
    <div v-if="node.isFile" class="tree-item tree-file" :style="paddingStyle">
        <span class="file-icon"><i class="fas fa-file-alt"></i></span>
        <span class="file-name">{{ node.name }}</span>
        <label class="perm-checkbox-label">
            <input type="checkbox" class="perm-checkbox" :checked="isChecked" @change="onToggle">
            <span>授权</span>
        </label>
    </div>

    <div v-else class="tree-folder-wrapper" :style="paddingStyle">
        <div class="tree-item tree-folder" @click.stop="expanded = !expanded">
            <span class="folder-icon"><i class="fas fa-folder"></i></span>
            <span class="folder-name">{{ node.name }}</span>
            <span class="folder-count">({{ fileCount }})</span>
        </div>
        <div v-show="expanded" class="tree-children">
            <PermissionTreeNode v-for="child in node.children" :key="child.name + (child.isFile ? child.blogId : '')"
                :node="child" :depth="depth + 1" :permissions="permissions"
                :initially-expanded="initiallyExpanded"
                @toggle="(blogId, checked) => emit('toggle', blogId, checked)" />
        </div>
    </div>
</template>

<script setup lang="ts">
import { ref, computed, PropType } from 'vue'

interface TreeNode {
    name: string
    isFile: boolean
    blogId?: number
    children?: TreeNode[]
}

const props = defineProps({
    node: {
        type: Object as PropType<TreeNode>,
        required: true
    },
    depth: {
        type: Number,
        default: 0
    },
    permissions: {
        type: Array as PropType<number[]>,
        default: () => []
    },
    initiallyExpanded: {
        type: Boolean,
        default: false
    }
})

const emit = defineEmits<{
    (e: 'toggle', blogId: number, checked: boolean): void
}>()

const expanded = ref(props.initiallyExpanded)

const paddingStyle = computed(() => ({
    paddingLeft: `${props.depth * 20 + 8}px`
}))

const fileCount = computed(() => {
    return props.node.children?.filter((c: TreeNode) => c.isFile).length || 0
})

const isChecked = computed(() => {
    const blogId = props.node.blogId
    return blogId !== undefined && props.permissions.includes(blogId)
})

// 处理切换事件
const onToggle = (event: Event) => {
    const blogId = props.node.blogId
    if (blogId !== undefined) {
        const checked = (event.target as HTMLInputElement).checked
        emit('toggle', blogId, checked)
    }
}
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

.tree-file {
    cursor: default;
}

.tree-file:hover {
    background: rgba(120, 170, 155, 0.06);
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

.tree-children {
    border-left: 2px dashed rgba(120, 170, 155, 0.25);
    margin-left: 20px;
}

/* 授权复选框 */
.perm-checkbox-label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.82rem;
    color: #4c6b5b;
    cursor: pointer;
    padding: 4px 12px;
    border-radius: 30px;
    border: 1px solid rgba(120, 170, 155, 0.15);
    background: rgba(255, 255, 255, 0.7);
    transition: all 0.2s ease;
    flex-shrink: 0;
    user-select: none;
}

.perm-checkbox-label:hover {
    background: rgba(255, 248, 214, 0.5);
    border-color: rgba(224, 199, 106, 0.3);
}

.perm-checkbox {
    width: 16px;
    height: 16px;
    accent-color: #7BC8C4;
    cursor: pointer;
    margin: 0;
}

/* 当复选框被选中时，标签高亮 */
.perm-checkbox:checked + span {
    font-weight: 600;
    color: #2d8cf0;
}

@media (max-width: 600px) {
    .tree-item {
        font-size: 0.9rem;
        padding: 6px 10px;
    }

    .tree-children {
        margin-left: 12px;
    }
}
</style>
