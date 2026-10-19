<!--
  FileNode.vue - 知识地图文件行（Riso Campus 纸面行）
  ----------------------------------------------------------------------------
  职责：
    1. 渲染目录树中的单个文档（叶子节点）
    2. 展示文档状态点、发布日期、待提交题数徽章与进入箭头
    3. 点击后向外层派发 click 事件（CategoryView / FolderNode 转成路由跳转）
  契约（不可破坏）：
    props  : node / depth / statusMap（+ 可选的 accent 用于学科印刷色）
    emits  : click
-->
<template>
  <button type="button" class="file-row" :class="[accentClass, { 'has-pending': pendingCount > 0 }]"
    :style="{ '--depth': depth }" :aria-label="`打开文档 ${node.name}`" @click="$emit('click')">
    <!-- 状态点：有待提交 = 朱红，无待提交 = 墨绿 -->
    <span class="file-row__dot" aria-hidden="true"></span>

    <span class="file-row__icon" aria-hidden="true">
      <i class="fas fa-file-lines"></i>
    </span>

    <span class="file-row__body">
      <span class="file-row__name">{{ node.name }}</span>
      <span class="file-row__meta">
        <span v-if="formattedDate" class="file-row__date mono">{{ formattedDate }}</span>
        <span v-if="pendingCount > 0" class="file-row__pending">
          <span class="badge">{{ pendingCount }}</span>
          <span class="file-row__pending-text">题待提交</span>
        </span>
        <span v-else class="file-row__ok">无待提交</span>
      </span>
    </span>

    <span class="file-row__arrow" aria-hidden="true">
      <i class="fas fa-arrow-right"></i>
    </span>
  </button>
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
  },
  /** 学科印刷色：cyan / violet / amber / coral / pink / lime */
  accent: {
    type: String,
    default: 'violet'
  }
})

defineEmits(['click'])

const ACCENTS = ['cyan', 'violet', 'amber', 'coral', 'pink', 'lime']
const accentClass = computed(() => `accent--${ACCENTS.includes(props.accent) ? props.accent : 'violet'}`)

const pendingCount = computed(() => {
  return props.statusMap.get(Number(props.node.blogId)) || 0
})

const formattedDate = computed(() => {
  const date = props.node.date
  if (!date || typeof date !== 'string') return ''
  return date.replace(/-/g, '.')
})
</script>

<style scoped>
.file-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 46px;
  padding: 10px 12px 10px calc(var(--depth, 0) * 12px + 8px);
  border: 1.5px solid transparent;
  border-radius: var(--r-sm);
  background: transparent;
  color: var(--text-2);
  font-family: var(--font-body);
  font-size: 0.93rem;
  text-align: left;
  transition: background var(--dur-fast), border-color var(--dur-fast), color var(--dur-fast),
    transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.file-row:hover {
  background: var(--paper-1);
  border-color: var(--ink);
  color: var(--text-1);
  transform: translate(-1px, -1px);
  box-shadow: 2px 2px 0 var(--ink);
}

.file-row:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}

/* 状态点：默认完成态 */
.file-row__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--green);
  border: 1.5px solid var(--ink);
  flex-shrink: 0;
  transition: background var(--dur-fast);
}

.file-row.has-pending .file-row__dot {
  background: var(--vermilion);
}

.file-row__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 7px;
  font-size: 0.76rem;
  color: var(--ink);
  background: var(--paper-2);
  border: 1.5px solid var(--ink);
  flex-shrink: 0;
  transition: background var(--dur-fast);
}

.file-row:hover .file-row__icon {
  background: var(--accent-fill, var(--butter));
}

.file-row__body {
  display: flex;
  flex-direction: row;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px 12px;
  min-width: 0;
  flex: 1;
}

.file-row__name {
  flex: 1 1 auto;
  min-width: 0;
  font-weight: 700;
  color: inherit;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.file-row__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-left: auto;
  font-size: 0.72rem;
  color: var(--text-3);
  flex-shrink: 0;
}

.file-row__date {
  font-size: 0.72rem;
  letter-spacing: 0.02em;
}

.file-row .badge {
  min-width: 22px;
  height: 22px;
  font-size: 0.74rem;
}

.file-row__pending {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--vermilion);
}

.file-row__pending-text {
  font-weight: 800;
}

.file-row__ok {
  color: var(--text-3);
}

.file-row__arrow {
  color: var(--text-3);
  font-size: 0.76rem;
  opacity: 0.45;
  transform: translateX(-2px);
  transition: opacity var(--dur-fast), transform var(--dur-fast) var(--ease-out), color var(--dur-fast);
  flex-shrink: 0;
}

.file-row:hover .file-row__arrow {
  opacity: 1;
  color: var(--ink);
  transform: translateX(2px);
}

/* ---------- 学科印刷色：hover 时图标块取色 ---------- */
.accent--cyan { --accent-fill: var(--sky); }
.accent--violet { --accent-fill: var(--cobalt); }
.accent--amber { --accent-fill: var(--butter); }
.accent--coral { --accent-fill: var(--coral); }
.accent--pink { --accent-fill: var(--pink); }
.accent--lime { --accent-fill: var(--lime); }

.accent--violet:hover .file-row__icon {
  color: var(--paper-1);
}

@media (max-width: 760px) {
  .file-row {
    gap: 8px;
    padding: 9px 10px 9px calc(var(--depth, 0) * 7px + 6px);
    font-size: 0.9rem;
  }

  .file-row__body {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .file-row__meta {
    margin-left: 0;
  }

  .file-row__date {
    display: none;
  }
}
</style>
