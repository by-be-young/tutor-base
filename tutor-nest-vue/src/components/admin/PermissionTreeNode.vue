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
    }
})

const emit = defineEmits<{
    (e: 'toggle', blogId: number, checked: boolean): void
}>()

const expanded = ref(false)

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
/* ... 保持原有样式不变 ... */
</style>