<!--
  FolderNode.vue - 知识地图文件夹节点（Riso Campus 纸面卡片）
  ----------------------------------------------------------------------------
  职责：
    1. 渲染一个文件夹卡片（印刷色标签块 + 展开箭头 + 名称 + 文件数 + 待提交徽章）
    2. 展开后递归渲染子文件夹 / 文件行，层级通过 depth 递增
    3. 冒泡文件点击事件给 CategoryView 做路由跳转
  契约（不可破坏）：
    props  : node / depth / statusMap（+ 可选的 accent / expand-token / expand-to）
    emits  : file-click
  说明：
    - 默认展开规则沿用原实现：数据到达后若子树存在待提交题目则自动展开一次
    - expand-token 变化时由父级统一展开/收起（“全部展开/收起”、搜索时自动展开）
-->
<template>
  <div class="tree-branch" :class="accentClass" :style="{ '--depth': depth }">
    <button type="button" class="folder-card" :aria-expanded="isExpanded"
      :aria-label="`文件夹 ${node.name}，${totalFileCount} 篇文档${pendingCount > 0 ? `，${pendingCount} 题待提交` : ''}`"
      @click.stop="toggleExpand">
      <span class="folder-card__tab" aria-hidden="true"></span>

      <span class="folder-card__chevron" :class="{ 'is-open': isExpanded }" aria-hidden="true">
        <i class="fas fa-chevron-right"></i>
      </span>

      <span class="folder-card__icon" aria-hidden="true">
        <i :class="isExpanded ? 'fas fa-folder-open' : 'fas fa-folder'"></i>
      </span>

      <span class="folder-card__name">{{ node.name }}</span>

      <span class="folder-card__meta">
        <span class="tag folder-card__count">{{ totalFileCount }} 篇</span>
        <span v-if="pendingCount > 0" class="badge folder-card__badge">{{ pendingCount }}</span>
        <span v-else-if="totalFileCount === 0" class="tag folder-card__empty">空</span>
      </span>
    </button>

    <div v-show="isExpanded" class="tree-children">
      <template v-for="child in node.children" :key="child.isFile ? `file-${child.blogId}` : `dir-${child.name}`">
        <FolderNode v-if="!child.isFile" :node="child" :depth="depth + 1" :status-map="statusMap" :accent="accent"
          :expand-token="expandToken" :expand-to="expandTo" @file-click="$emit('file-click', $event)" />
        <FileNode v-else :node="child" :depth="depth + 1" :status-map="statusMap" :accent="accent"
          @click="$emit('file-click', child.blogId)" />
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
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
  },
  /** 学科印刷色：cyan / violet / amber / coral / pink / lime */
  accent: {
    type: String,
    default: 'violet'
  },
  /** 父级展开信号：每次数值变化时按 expandTo 统一设置展开状态 */
  expandToken: {
    type: Number,
    default: 0
  },
  expandTo: {
    type: Boolean,
    default: false
  }
})

defineEmits(['file-click'])

const ACCENTS = ['cyan', 'violet', 'amber', 'coral', 'pink', 'lime']
const accentClass = computed(() => `accent--${ACCENTS.includes(props.accent) ? props.accent : 'violet'}`)

/** 当前文件夹子树内的文件总数（含嵌套文件夹） */
const totalFileCount = computed(() => {
  function countNode(n) {
    if (n.isFile) return 1
    return (n.children || []).reduce((sum, child) => sum + countNode(child), 0)
  }
  return (props.node.children || []).reduce((sum, child) => sum + countNode(child), 0)
})

/** 子树内待提交题目总数 */
const pendingCount = computed(() => {
  function sumPending(n) {
    if (n.isFile) {
      return props.statusMap.get(Number(n.blogId)) || 0
    }
    return (n.children || []).reduce((sum, child) => sum + sumPending(child), 0)
  }
  return (props.node.children || []).reduce((sum, child) => sum + sumPending(child), 0)
})

/** 子树内是否存在待提交题目（沿用的自动展开依据） */
const hasPendingChild = computed(() => pendingCount.value > 0)

// 初始展开状态：有待提交则展开。之后用户可手动切换，不再被 watch 覆盖。
const isExpanded = ref(hasPendingChild.value)
let autoExpanded = hasPendingChild.value // 标记本次是否是自动展开的

watch(hasPendingChild, (now) => {
  // 数据到达后，只有当从未手动操作过时才自动展开
  if (now && isExpanded.value === false && !autoExpanded) {
    isExpanded.value = true
    autoExpanded = true
  }
})

// 父级统一展开 / 收起
watch(() => props.expandToken, () => {
  isExpanded.value = props.expandTo
  autoExpanded = false
})

function toggleExpand() {
  isExpanded.value = !isExpanded.value
  autoExpanded = false // 手动操作后，不再自动展开
}
</script>

<style scoped>
.tree-branch {
  padding-left: calc(var(--depth, 0) * 12px);
}

.folder-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 54px;
  padding: 10px 12px 10px 21px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  color: var(--text-1);
  font-family: var(--font-body);
  font-size: 1rem;
  font-weight: 700;
  text-align: left;
  box-shadow: 3px 3px 0 var(--ink);
  transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out), background var(--dur-fast);
}

/* 印刷色标签块：左侧实色书脊 */
.folder-card__tab {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 11px;
  background: var(--accent-fill, var(--butter));
  border-right: 1.5px solid var(--ink);
  border-radius: calc(var(--r-sm) - 2px) 0 0 calc(var(--r-sm) - 2px);
  pointer-events: none;
}

.folder-card:hover {
  transform: translate(-2px, -2px);
  box-shadow: 5px 5px 0 var(--ink);
  background: var(--paper-1);
}

.folder-card:active {
  transform: translate(1px, 1px);
  box-shadow: 2px 2px 0 var(--ink);
}

.folder-card__chevron {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  color: var(--ink);
  font-size: 0.7rem;
  transition: transform var(--dur-fast) var(--ease-out);
  flex-shrink: 0;
}

.folder-card__chevron.is-open {
  transform: rotate(90deg);
}

.folder-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  font-size: 0.86rem;
  color: var(--accent-ink, var(--ink));
  background: var(--accent-fill, var(--butter));
  border: 1.5px solid var(--ink);
  flex-shrink: 0;
}

.folder-card__name {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}

.folder-card__meta {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.folder-card__count {
  font-variant-numeric: tabular-nums;
  border-color: var(--ink);
  color: var(--ink-soft);
  background: var(--paper-2);
}

.folder-card__badge {
  border-width: 1.5px;
}

.folder-card__empty {
  opacity: 0.7;
}

.tree-children {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 8px 0 2px 8px;
  padding-left: 10px;
  border-left: 1.5px dashed var(--line-strong);
  animation: tree-open var(--dur) var(--ease-out);
}

@keyframes tree-open {
  from {
    opacity: 0;
    transform: translateY(-5px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

/* ---------- 学科印刷色映射（只用 theme.css token） ---------- */
.accent--cyan {
  --accent-fill: var(--sky);
  --accent-ink: var(--ink);
}

.accent--violet {
  --accent-fill: var(--cobalt);
  --accent-ink: var(--paper-1);
}

.accent--amber {
  --accent-fill: var(--butter);
  --accent-ink: var(--ink);
}

.accent--coral {
  --accent-fill: var(--coral);
  --accent-ink: var(--ink);
}

.accent--pink {
  --accent-fill: var(--pink);
  --accent-ink: var(--ink);
}

.accent--lime {
  --accent-fill: var(--lime);
  --accent-ink: var(--ink);
}

@media (max-width: 760px) {
  .tree-branch {
    padding-left: calc(var(--depth, 0) * 7px);
  }

  .folder-card {
    gap: 8px;
    min-height: 50px;
    padding: 9px 11px 9px 18px;
    font-size: 0.92rem;
    box-shadow: 2px 2px 0 var(--ink);
  }

  .folder-card:hover {
    box-shadow: 3px 3px 0 var(--ink);
  }

  .folder-card__tab {
    width: 9px;
  }

  .folder-card__icon {
    width: 26px;
    height: 26px;
    border-radius: 7px;
  }

  .tree-children {
    margin-left: 3px;
    padding-left: 7px;
  }
}
</style>
