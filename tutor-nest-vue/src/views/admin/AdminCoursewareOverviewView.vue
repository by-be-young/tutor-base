<!-- src/views/admin/AdminCoursewareOverviewView.vue -->
<template>
  <div class="overview">
    <section class="panel panel--tight overview__lead">
      <p class="eyebrow">课件托管</p>
      <p class="overview__lead-text">
        每个科目一个发布版本；草稿保存后学员端看不到，点「发布」才生效。
        获取他人的课件、分享自己的课件都在「共享」页。
      </p>
    </section>

    <div class="stat-grid">
      <div class="stat-card">
        <span class="stat-label">科目</span>
        <span class="stat-value">{{ subjects.length }}</span>
        <span class="stat-sub">已发布版本</span>
      </div>
      <div class="stat-card stat-card--amber">
        <span class="stat-label">待发布草稿</span>
        <span class="stat-value">{{ data.pendingDrafts }}</span>
        <span class="stat-sub">草稿需点发布才生效</span>
      </div>
      <div class="stat-card stat-card--cyan">
        <span class="stat-label">获取的课件</span>
        <span class="stat-value">{{ data.acquiredCount }}</span>
        <span class="stat-sub">来自其他老师</span>
      </div>
      <div class="stat-card" :class="{ 'stat-card--coral': data.pendingUpdates > 0 }">
        <span class="stat-label">有更新</span>
        <span class="stat-value">{{ data.pendingUpdates }}</span>
        <span class="stat-sub">来源发布了新版本</span>
      </div>
    </div>

    <section class="panel panel--pad">
      <header class="panel-head overview__head">
        <h2 class="panel-title">存储空间</h2>
        <span class="chip" :class="usageChipClass">{{ usageLabel }}</span>
      </header>
      <div class="panel-body overview__usage">
        <div class="progress" :class="usageProgressClass" role="img"
          :aria-label="`已用 ${formatBytes(usage.totalBytes)}，配额 ${quotaText}`">
          <span class="progress__bar" :style="{ width: usageWidth }"></span>
        </div>
        <p class="overview__usage-line">
          <strong>{{ formatBytes(usage.totalBytes) }}</strong>
          <span class="text-muted"> / {{ quotaText }}</span>
          <span v-if="usage.orphanCount" class="text-dim">
            · 其中 {{ usage.orphanCount }} 个孤儿文件（{{ formatBytes(usage.orphanBytes) }}）可回收
          </span>
        </p>
      </div>
    </section>

    <div v-if="loading" class="empty" role="status">
      <span class="spinner" aria-hidden="true"></span>
      <p class="empty__text">正在加载课件概览…</p>
    </div>

    <div v-else-if="!subjects.length" class="empty">
      <i class="fas fa-folder-open empty__icon" aria-hidden="true"></i>
      <p class="empty__title">还没有发布任何科目</p>
      <p class="empty__text">到「编辑」页新建草稿并发布；或到「共享」页从其他老师那里获取课件。</p>
      <div class="empty__actions">
        <router-link class="btn btn--primary btn--sm" to="/admin/editor">去新建课件</router-link>
        <router-link class="btn btn--ghost btn--sm" to="/admin/sharing">去共享页</router-link>
      </div>
    </div>

    <div v-else class="grid--3 overview__grid">
      <article v-for="item in subjects" :key="item.subject" class="panel overview__card">
        <header class="overview__card-head">
          <h2 class="overview__card-title">{{ item.subject }}</h2>
          <span class="chip chip--lime mono">v{{ item.version }}</span>
        </header>
        <dl class="overview__meta">
          <div><dt>篇数</dt><dd class="mono">{{ item.articleCount }}</dd></div>
          <div><dt>草稿</dt><dd class="mono">{{ item.draftCount }}</dd></div>
          <div><dt>被获取</dt><dd class="mono">{{ item.copiedCount }}</dd></div>
          <div><dt>分享</dt><dd class="mono">{{ item.shareCount }}</dd></div>
        </dl>
        <p class="overview__published text-dim">发布于 {{ formatDate(item.publishedAt) }}</p>
        <div class="row overview__actions">
          <router-link class="btn btn--soft btn--sm" to="/admin/editor">编辑</router-link>
          <router-link class="btn btn--ghost btn--sm" to="/admin/sharing">共享</router-link>
        </div>
      </article>
    </div>

    <section class="panel panel--tight overview__legacy">
      <h2 class="panel-title">旧「认领」页即将下线</h2>
      <p class="text-dim overview__legacy-text">
        课件归属已改为「老师命名空间 + 发布版本」，不再需要认领。
        旧页面仅用于查看历史认领记录，写入操作可被服务端配置下线。
      </p>
      <router-link class="btn btn--ghost btn--sm" to="/admin/content">查看旧认领页</router-link>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { coursewareGateway } from '@/gateways/coursewareGateway'
import { showToast } from '@/utils/toast'

const loading = ref(true)
const data = reactive({
  subjects: [],
  pendingDrafts: 0,
  acquiredCount: 0,
  pendingUpdates: 0,
  unreadNotifications: 0
})
const usage = reactive({
  totalBytes: 0,
  orphanCount: 0,
  orphanBytes: 0,
  quotaBytes: 0,
  usageRatio: 0,
  level: 'ok'
})

const subjects = computed(() => data.subjects)

const percentText = computed(() => `${Math.round(usage.usageRatio * 100)}%`)
const quotaText = computed(() => (usage.quotaBytes > 0 ? formatBytes(usage.quotaBytes) : '未设限'))
const usageWidth = computed(() => `${Math.min(100, Math.round(usage.usageRatio * 100))}%`)

const usageChipClass = computed(() => ({
  'chip--lime': usage.level === 'ok',
  'chip--amber': usage.level === 'warn',
  'chip--coral': usage.level === 'critical'
}))

const usageLabel = computed(() => {
  if (usage.quotaBytes <= 0) return '未设限'
  if (usage.level === 'critical') return `容量告急 · ${percentText.value}`
  if (usage.level === 'warn') return `接近上限 · ${percentText.value}`
  return `余量充足 · ${percentText.value}`
})

const usageProgressClass = computed(() => ({
  'progress--warm': usage.level === 'warn',
  'progress--pink': usage.level === 'critical'
}))

function formatBytes(value) {
  const bytes = Number(value) || 0
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

function formatDate(value) {
  if (!value) return '—'
  return String(value).slice(0, 10)
}

async function loadUsage() {
  try {
    Object.assign(usage, await coursewareGateway.getUsage())
  } catch (error) {
    // 用量只是辅助信息：失败不打断概览主流程
    if (typeof console !== 'undefined') console.warn('加载存储用量失败', error?.message)
  }
}

async function loadOverview() {
  try {
    const overview = await coursewareGateway.getOverview()
    data.subjects = overview.subjects
    data.pendingDrafts = overview.pendingDrafts
    data.acquiredCount = overview.acquiredCount
    data.pendingUpdates = overview.pendingUpdates
    data.unreadNotifications = overview.unreadNotifications
  } catch (error) {
    showToast(error?.message || '加载概览失败', 'error')
  }
}

onMounted(async () => {
  await Promise.all([loadOverview(), loadUsage()])
  loading.value = false
})
</script>

<style scoped>
.overview {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.overview__lead {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.overview__lead-text {
  margin: 0;
  font-size: 0.82rem;
  line-height: 1.6;
  color: var(--text-2);
}

.overview__head {
  align-items: center;
}

.overview__usage {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.overview__usage-line {
  margin: 0;
  font-size: 0.86rem;
  color: var(--text-2);
}

.overview__grid {
  align-items: stretch;
}

.overview__card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}

.overview__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.overview__card-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 800;
  letter-spacing: 0.01em;
  color: var(--ink);
}

.overview__meta {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin: 0;
}

.overview__meta dt {
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-3);
}

.overview__meta dd {
  margin: 2px 0 0;
  font-size: 1rem;
  font-weight: 700;
  color: var(--ink);
}

.overview__published {
  margin: 0;
  font-size: 0.76rem;
}

.overview__actions {
  margin-top: auto;
  gap: 8px;
  flex-wrap: wrap;
}

.overview__legacy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.overview__legacy-text {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .overview__meta {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
