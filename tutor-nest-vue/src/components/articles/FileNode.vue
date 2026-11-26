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
  <div class="file-wrap" :class="accentClass" :style="{ '--depth': depth }">
    <button type="button" class="file-row" :class="{ 'has-pending': pendingCount > 0 }"
      :aria-label="`打开文档 ${node.name}`" @click="$emit('click')">
      <!-- 状态点：有待提交 = 朱红，无待提交 = 墨绿 -->
      <span class="file-row__dot" aria-hidden="true"></span>

      <span class="file-row__icon" aria-hidden="true">
        <i class="fas fa-file-lines"></i>
      </span>

      <span class="file-row__body">
        <span class="file-row__name" :title="node.name">{{ node.name }}</span>
        <span v-if="isAdmin" class="file-row__meta">
          <span v-if="node.meta" class="file-row__meta-text">{{ node.meta }}</span>
          <span v-if="node.path" class="file-row__path mono">{{ node.path }}</span>
        </span>
        <span v-else class="file-row__meta">
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
    <button v-if="isAdmin" type="button" class="file-row__move" title="移动到此课件到其他目录"
      aria-label="移动课件" @click.stop="$emit('move', node.blogId)">
      <i class="fas fa-arrow-right-arrow-left"></i>
      <span>移动</span>
    </button>
    <button v-if="isAdmin" type="button" class="file-row__move file-row__delete" title="删除这篇课件"
      aria-label="删除课件" @click.stop="$emit('delete', node.blogId)">
      <i class="fas fa-trash-can"></i>
      <span>删除</span>
    </button>

    <details v-if="isAdmin" class="file-more">
      <summary class="file-more__trigger" aria-label="更多课件操作">
        <i class="fas fa-ellipsis" aria-hidden="true"></i>
      </summary>
      <div class="file-more__menu">
        <button type="button" @click.stop="handleMenuAction('move', $event)">
          <i class="fas fa-arrow-right-arrow-left" aria-hidden="true"></i>
          移动
        </button>
        <button type="button" class="is-danger" @click.stop="handleMenuAction('delete', $event)">
          <i class="fas fa-trash-can" aria-hidden="true"></i>
          删除
        </button>
      </div>
    </details>
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
  },
  /** 学科印刷色：cyan / violet / amber / coral / pink / lime */
  accent: {
    type: String,
    default: 'violet'
  },
  /** learner = 学员端（日期 + 待提交）；admin = 管理端目录（状态与路径） */
  mode: {
    type: String,
    default: 'learner'
  }
})

const emit = defineEmits(['click', 'move', 'delete'])

function handleMenuAction(action, event) {
  const details = event.currentTarget.closest('details')
  if (details) details.open = false
  emit(action, props.node.blogId)
}

const ACCENTS = ['cyan', 'violet', 'amber', 'coral', 'pink', 'lime']
const accentClass = computed(() => `accent--${ACCENTS.includes(props.accent) ? props.accent : 'violet'}`)
const isAdmin = computed(() => props.mode === 'admin')

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
.file-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.file-row {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 10px 12px;
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

/* 管理端目录行：状态说明 + 逻辑路径 */
.file-row__meta-text {
  color: var(--text-2);
  font-size: 0.74rem;
}

.file-row__path {
  color: var(--text-3);
  font-size: 0.7rem;
  word-break: break-all;
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

/* 管理端移动按钮 */
.file-row__move {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  padding: 6px 10px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-2);
  color: var(--text-2);
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--dur-fast), color var(--dur-fast), transform var(--dur-fast) var(--ease-out);
}

.file-row__move:hover {
  background: var(--cobalt);
  color: var(--paper-1);
  transform: translate(-1px, -1px);
  box-shadow: 2px 2px 0 var(--ink);
}

.file-row__move:active {
  transform: translate(1px, 1px);
  box-shadow: none;
}

.file-row__delete:hover {
  background: var(--vermilion);
  color: var(--paper-1);
}

/* 移动端更多操作菜单：桌面端仍使用并排的“移动 / 删除”按钮 */
.file-more {
  display: none;
}

.file-more__trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 44px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-2);
  color: var(--text-2);
  cursor: pointer;
  list-style: none;
}

.file-more__trigger::-webkit-details-marker {
  display: none;
}

.file-more__trigger i {
  font-size: 0.8rem;
}

.file-more__menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 24;
  display: flex;
  flex-direction: column;
  width: 132px;
  padding: 5px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-md);
  background: var(--paper-1);
  box-shadow: 4px 4px 0 var(--ink);
}

.file-more__menu button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: var(--r-sm);
  color: var(--text-1);
  font-size: 0.76rem;
  font-weight: 700;
  text-align: left;
}

.file-more__menu button:hover,
.file-more__menu button:focus-visible {
  background: var(--paper-2);
}

.file-more__menu button.is-danger {
  color: var(--vermilion);
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
  .file-wrap {
    position: relative;
    align-items: flex-start;
    gap: 4px;
  }

  .file-row {
    gap: 7px;
    min-height: 44px;
    padding: 8px 8px 8px calc(var(--depth, 0) * 4px + 6px);
    font-size: 0.88rem;
  }

  /* 标题是这一行的主信息；装饰性文件图标让位给标题宽度 */
  .file-row__icon {
    display: none;
  }

  .file-row__body {
    flex-direction: column;
    align-items: flex-start;
    gap: 3px;
    width: 100%;
  }

  .file-row__name {
    display: block;
    overflow: visible;
    text-overflow: clip;
    white-space: normal;
    overflow-wrap: anywhere;
    word-break: break-word;
    width: 100%;
    line-height: 1.4;
    font-size: 0.84rem;
    color: var(--ink);
  }

  .file-row__meta {
    width: 100%;
    margin-left: 0;
    gap: 6px;
    font-size: 0.66rem;
  }

  /* 手机上层级已经在树里体现，路径不再重复占一行 */
  .file-row__path {
    display: none;
  }

  .file-row__meta-text {
    font-size: 0.68rem;
  }

  /* 装饰箭头在窄屏没有意义，省下的空间全部留给标题 */
  .file-row__arrow {
    display: none;
  }

  /* 移动 / 删除收进三点菜单，标题不再长期被两个按钮挤窄 */
  .file-row__move {
    display: none;
  }

  .file-more {
    display: block;
    position: relative;
    flex: 0 0 auto;
  }

  .file-row__date {
    display: none;
  }
}
</style>
