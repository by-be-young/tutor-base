<!-- src/components/common/CommandPalette.vue -->
<template>
  <teleport to="body">
    <transition name="palette">
      <div v-if="uiStore.paletteOpen" class="palette-backdrop" @click.self="uiStore.closePalette()">
        <div class="palette" role="dialog" aria-modal="true" aria-label="命令面板">
          <div class="palette__search">
            <i class="fas fa-magnifying-glass" aria-hidden="true"></i>
            <input ref="inputRef" v-model="query" type="text" class="palette__input"
              placeholder="搜索页面、学科或操作…" aria-label="搜索页面、学科或操作" @keydown.down.prevent="move(1)"
              @keydown.up.prevent="move(-1)" @keydown.enter.prevent="run(selectedCommand)" />
            <kbd class="kbd">ESC</kbd>
          </div>

          <div class="palette__body">
            <template v-if="filteredGroups.length">
              <section v-for="group in filteredGroups" :key="group.label" class="palette__group">
                <p class="palette__group-label">{{ group.label }}</p>
                <button v-for="item in group.items" :key="item.id" type="button" class="palette__item"
                  :class="{ 'is-selected': item.id === selectedCommand?.id }" @mouseenter="selectedId = item.id"
                  @click="run(item)">
                  <span class="palette__item-icon" :style="item.tint ? { background: item.tint } : null">
                    <i :class="item.icon" aria-hidden="true"></i>
                  </span>
                  <span class="palette__item-text">
                    <span class="palette__item-label">{{ item.label }}</span>
                    <span class="palette__item-hint">{{ item.hint }}</span>
                  </span>
                  <span v-if="item.meta" class="palette__item-meta">{{ item.meta }}</span>
                  <i class="fas fa-arrow-right palette__item-arrow" aria-hidden="true"></i>
                </button>
              </section>
            </template>
            <div v-else class="palette__empty">
              <i class="fas fa-satellite-dish" aria-hidden="true"></i>
              <p>没有匹配的结果</p>
              <span>{{ authStore.isAdministrator ? '试试「学员」「批阅」或「课件」' : '试试「签到」「高中化学」或「错题」' }}</span>
            </div>
          </div>

          <div class="palette__foot">
            <span><kbd class="kbd">↑</kbd><kbd class="kbd">↓</kbd> 选择</span>
            <span><kbd class="kbd">↵</kbd> 打开</span>
            <span><kbd class="kbd">ESC</kbd> 关闭</span>
            <span class="palette__brand">NEST · 学巢</span>
          </div>
        </div>
      </div>
    </transition>
  </teleport>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useCheckinStore } from '@/stores/checkinStore'
import { useUiStore } from '@/stores/uiStore'
import { showToast } from '@/utils/toast'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const checkinStore = useCheckinStore()
const uiStore = useUiStore()

const query = ref('')
const selectedId = ref('')
const inputRef = ref(null)

const SUBJECT_TINTS = {
  高中化学: 'linear-gradient(135deg, #0f8f7c, #7cc5f0)',
  高中英语: 'linear-gradient(135deg, #ed4b2a, #ffc53d)',
  初中英语: 'linear-gradient(135deg, #1d4ed8, #c9ef4a)'
}

const allGroups = computed(() => {
  // 管理员只保留管理中心入口、修改密码与退出，不暴露任务/错题/收藏等学员端操作。
  if (authStore.isAdministrator) {
    return [
      {
        label: '管理中心',
        items: [
          { id: 'admin-dashboard', label: '工作台', hint: '概览、学员动态与快捷入口', icon: 'fas fa-chart-line', to: '/admin', meta: '页面' },
          { id: 'admin-learners', label: '学员', hint: '账户、科目与权限管理', icon: 'fas fa-users', to: '/admin/learners', meta: '页面' },
          { id: 'admin-reviews', label: '批阅', hint: '待批阅作业与标准答案', icon: 'fas fa-pen-nib', to: '/admin/reviews', meta: '页面' },
          { id: 'admin-content', label: '课件', hint: '认领学科与课件', icon: 'fas fa-book-open', to: '/admin/content', meta: '页面' },
          { id: 'admin-profile', label: '我的', hint: '管理员资料与认领概览', icon: 'fas fa-user-gear', to: '/admin/me', meta: '页面' }
        ]
      },
      {
        label: '快捷操作',
        items: [
          { id: 'admin-password', label: '修改密码', hint: '账户安全设置', icon: 'fas fa-key', to: '/settings/password', meta: '账户' },
          {
            id: 'act-logout',
            label: '退出登录',
            hint: '结束当前会话',
            icon: 'fas fa-arrow-right-from-bracket',
            action: 'logout',
            meta: '账户',
            tint: 'linear-gradient(135deg, #ff9ebb, #ed4b2a)'
          }
        ]
      }
    ]
  }

  const navigation = [
    { id: 'nav-home', label: '首页', hint: '回到学习空间', icon: 'fas fa-house', to: '/', meta: '页面' }
  ]
  const actions = []

  if (authStore.isLoggedIn) {
    navigation.push(
      { id: 'nav-tasks', label: '任务中心', hint: '签到、抽卡与成就', icon: 'fas fa-list-check', to: '/tasks', meta: '页面' },
      { id: 'nav-achievements', label: '成就', hint: '等级、成就与积分奖励', icon: 'fas fa-medal', to: '/tasks?tab=achievements', meta: '页面' },
      { id: 'nav-wrong', label: '错题本', hint: '整理、掌握与重做', icon: 'fas fa-book-medical', to: '/wrong-questions', meta: '页面' },
      { id: 'nav-training', label: '错题训练', hint: '沉浸式重做错题', icon: 'fas fa-dumbbell', to: '/wrong-training', meta: '页面' },
      { id: 'nav-password', label: '修改密码', hint: '账户安全设置', icon: 'fas fa-key', to: '/settings/password', meta: '页面' }
    )
    actions.push({
      id: 'act-checkin',
      label: checkinStore.todayCheckedIn ? '今日已签到' : '立即签到',
      hint: checkinStore.todayCheckedIn ? '今天已经签过啦' : '领取今日积分',
      icon: 'fas fa-calendar-check',
      action: 'checkin',
      meta: checkinStore.todayCheckedIn ? '已完成' : '+100',
      tint: 'linear-gradient(135deg, #ffc53d, #ed4b2a)'
    })
    actions.push({
      id: 'act-logout',
      label: '退出登录',
      hint: '结束当前会话',
      icon: 'fas fa-arrow-right-from-bracket',
      action: 'logout',
      meta: '账户',
      tint: 'linear-gradient(135deg, #ff9ebb, #ed4b2a)'
    })
  } else {
    actions.push(
      { id: 'act-login', label: '登录', hint: '进入你的学习空间', icon: 'fas fa-right-to-bracket', action: 'login', meta: '账户' },
      { id: 'act-register', label: '注册', hint: '创建新账户', icon: 'fas fa-user-plus', action: 'register', meta: '账户' }
    )
  }

  const subjects = [...new Set(blogStore.blogData.map(article => article.series).filter(Boolean))]
  const subjectItems = subjects.map(name => ({
    id: `subject-${name}`,
    label: name,
    hint: '查看学科知识地图',
    icon: 'fas fa-layer-group',
    to: `/category?subject=${encodeURIComponent(name)}`,
    meta: '学科',
    tint: SUBJECT_TINTS[name]
  }))

  return [
    { label: '快速跳转', items: navigation },
    ...(subjectItems.length ? [{ label: '学科地图', items: subjectItems }] : []),
    { label: '快捷操作', items: actions }
  ]
})

const filteredGroups = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  return allGroups.value
    .map(group => ({
      ...group,
      items: group.items.filter(item =>
        !keyword ||
        `${item.label} ${item.hint} ${item.meta || ''}`.toLowerCase().includes(keyword)
      )
    }))
    .filter(group => group.items.length)
})

const flatCommands = computed(() => filteredGroups.value.flatMap(group => group.items))

const selectedCommand = computed(() =>
  flatCommands.value.find(item => item.id === selectedId.value) || flatCommands.value[0]
)

watch(flatCommands, (items) => {
  if (!items.some(item => item.id === selectedId.value)) {
    selectedId.value = items[0]?.id || ''
  }
})

watch(query, () => {
  selectedId.value = flatCommands.value[0]?.id || ''
})

watch(() => uiStore.paletteOpen, async (open) => {
  if (open) {
    query.value = ''
    selectedId.value = ''
    await nextTick()
    inputRef.value?.focus()
    if (authStore.isLoggedIn && !blogStore.blogData.length) {
      blogStore.loadArticleData()
    }
  }
})

function move(step) {
  const items = flatCommands.value
  if (!items.length) return
  const currentIndex = items.findIndex(item => item.id === selectedCommand.value?.id)
  const nextIndex = (currentIndex + step + items.length) % items.length
  selectedId.value = items[nextIndex].id
}

function run(item) {
  if (!item) return
  uiStore.closePalette()
  if (item.to) {
    router.push(item.to)
    return
  }
  if (item.action === 'checkin') {
    if (checkinStore.todayCheckedIn) {
      showToast('今天已经签到过了', 'info')
      return
    }
    if (router.currentRoute.value.path !== '/tasks') router.push('/tasks')
    checkinStore.openModal()
    return
  }
  if (item.action === 'login' || item.action === 'register') {
    uiStore.requestAuth(item.action)
    if (router.currentRoute.value.path !== '/') router.push('/')
    return
  }
  if (item.action === 'logout') {
    authStore.logout().finally(() => router.push('/'))
  }
}

function onGlobalKeydown(event) {
  const key = String(event.key || '').toLowerCase()
  if ((event.metaKey || event.ctrlKey) && key === 'k') {
    event.preventDefault()
    uiStore.togglePalette()
    return
  }
  if (!uiStore.paletteOpen) return
  if (event.key === 'Escape') {
    event.preventDefault()
    uiStore.closePalette()
  }
}

onMounted(() => {
  document.addEventListener('keydown', onGlobalKeydown)
  if (!blogStore.blogData.length) blogStore.loadArticleData()
})

onBeforeUnmount(() => document.removeEventListener('keydown', onGlobalKeydown))
</script>

<style scoped>
.palette-backdrop {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 12vh 18px 24px;
  background: rgba(28, 26, 20, 0.4);
}

.palette {
  width: min(680px, 100%);
  border-radius: 16px;
  border: 2px solid var(--ink);
  background: var(--paper-1);
  box-shadow: var(--shadow-float);
  overflow: hidden;
}

.palette__search {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1.5px solid var(--ink);
  background: var(--paper-0);
}

.palette__search > i {
  color: var(--vermilion);
  font-size: 1rem;
}

.palette__input {
  flex: 1;
  height: 30px;
  border: 0;
  background: transparent;
  color: var(--ink);
  font-size: 1rem;
  font-weight: 600;
  outline: none;
}

.palette__body {
  max-height: min(52vh, 440px);
  padding: 8px;
  overflow-y: auto;
}

.palette__group + .palette__group {
  margin-top: 4px;
}

.palette__group-label {
  padding: 10px 12px 6px;
  font-family: var(--font-mono);
  font-size: 0.6rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--text-4);
  font-weight: 600;
}

.palette__item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 9px 12px;
  border-radius: 9px;
  border: 1.5px solid transparent;
  text-align: left;
  color: var(--text-2);
  transition: background var(--dur-fast), border-color var(--dur-fast), color var(--dur-fast);
}

.palette__item.is-selected {
  background: var(--lime);
  border-color: var(--ink);
  color: var(--ink);
}

.palette__item-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: 1.5px solid var(--ink);
  background: var(--paper-2);
  color: var(--ink);
  font-size: 0.8rem;
  flex-shrink: 0;
}

.palette__item-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.palette__item-label {
  font-size: 0.9rem;
  font-weight: 700;
}

.palette__item-hint {
  font-size: 0.72rem;
  color: var(--text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.palette__item-meta {
  margin-left: auto;
  padding: 2px 8px;
  border-radius: var(--r-full);
  border: 1px solid var(--line);
  background: var(--paper-1);
  color: var(--text-3);
  font-size: 0.66rem;
  font-weight: 600;
  white-space: nowrap;
}

.palette__item-arrow {
  font-size: 0.68rem;
  color: var(--text-4);
  opacity: 0;
  transition: opacity var(--dur-fast), transform var(--dur-fast);
}

.palette__item.is-selected .palette__item-arrow {
  opacity: 1;
  transform: translateX(2px);
  color: var(--ink);
}

.palette__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 44px 20px;
  color: var(--text-3);
  text-align: center;
}

.palette__empty i {
  font-size: 1.8rem;
  color: var(--vermilion);
  margin-bottom: 6px;
}

.palette__empty span {
  font-size: 0.78rem;
  color: var(--text-4);
}

.palette__foot {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 18px;
  border-top: 1.5px solid var(--ink);
  background: var(--paper-0);
  color: var(--text-3);
  font-size: 0.72rem;
}

.palette__foot span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.palette__brand {
  margin-left: auto;
  font-family: var(--font-mono);
  letter-spacing: 0.14em;
  color: var(--text-3);
}

.palette-enter-active,
.palette-leave-active {
  transition: opacity 0.18s var(--ease-out), transform 0.18s var(--ease-out);
}

.palette-enter-from,
.palette-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

@media (max-width: 768px) {
  .palette-backdrop {
    padding-top: 8vh;
  }

  .palette__foot {
    display: none;
  }

  .palette__body {
    max-height: 60vh;
  }
}
</style>
