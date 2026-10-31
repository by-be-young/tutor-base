<template>
  <section class="overview">
    <header class="overview__head">
      <h1 class="overview__title">我的课件</h1>
      <p class="overview__hint">
        每个科目一个发布版本；草稿保存后学员端看不到，点「发布」才生效。
        获取他人的课件、分享自己的课件都在「共享」页。
      </p>
    </header>

    <div class="overview__stats">
      <div class="overview__stat">
        <span class="overview__stat-value">{{ subjects.length }}</span>
        <span class="overview__stat-label">个科目</span>
      </div>
      <div class="overview__stat">
        <span class="overview__stat-value">{{ data.pendingDrafts }}</span>
        <span class="overview__stat-label">篇待发布草稿</span>
      </div>
      <div class="overview__stat">
        <span class="overview__stat-value">{{ data.acquiredCount }}</span>
        <span class="overview__stat-label">篇获取的课件</span>
      </div>
      <div class="overview__stat">
        <span class="overview__stat-value">{{ data.pendingUpdates }}</span>
        <span class="overview__stat-label">篇有更新</span>
      </div>
    </div>

    <div v-if="loading" class="overview__empty">加载中…</div>
    <div v-else-if="!subjects.length" class="overview__empty">
      <p>还没有发布任何科目。</p>
      <p class="overview__empty-hint">
        到「编辑」页新建草稿并发布；或到「共享」页从其他老师那里获取课件。
      </p>
    </div>

    <div v-else class="overview__grid">
      <article v-for="item in subjects" :key="item.subject" class="overview__card">
        <header class="overview__card-head">
          <h2 class="overview__card-title">{{ item.subject }}</h2>
          <span class="overview__badge">v{{ item.version }}</span>
        </header>
        <dl class="overview__meta">
          <div><dt>篇数</dt><dd>{{ item.articleCount }}</dd></div>
          <div><dt>草稿</dt><dd>{{ item.draftCount }}</dd></div>
          <div><dt>被获取</dt><dd>{{ item.copiedCount }}</dd></div>
          <div><dt>分享</dt><dd>{{ item.shareCount }}</dd></div>
        </dl>
        <p class="overview__published">发布于 {{ formatDate(item.publishedAt) }}</p>
        <div class="overview__actions">
          <router-link class="overview__link" to="/admin/editor">编辑</router-link>
          <router-link class="overview__link" to="/admin/sharing">共享</router-link>
        </div>
      </article>
    </div>

    <div class="overview__legacy">
      <h2 class="overview__legacy-title">旧「认领」页即将下线</h2>
      <p class="overview__legacy-text">
        课件归属已改为「老师命名空间 + 发布版本」，不再需要认领。
        旧页面仅用于查看历史认领记录，写入操作可被服务端配置下线。
      </p>
      <router-link class="overview__link" to="/admin/content">查看旧认领页</router-link>
    </div>
  </section>
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

const subjects = computed(() => data.subjects)

function formatDate(value) {
  if (!value) return '—'
  return String(value).slice(0, 10)
}

onMounted(async () => {
  try {
    const overview = await coursewareGateway.getOverview()
    data.subjects = overview.subjects
    data.pendingDrafts = overview.pendingDrafts
    data.acquiredCount = overview.acquiredCount
    data.pendingUpdates = overview.pendingUpdates
    data.unreadNotifications = overview.unreadNotifications
  } catch (error) {
    showToast(error?.message || '加载概览失败', 'error')
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.overview { display: flex; flex-direction: column; gap: 18px; padding: 4px 0 32px; }
.overview__head { display: flex; flex-direction: column; gap: 6px; }
.overview__title { margin: 0; font-size: 22px; font-weight: 700; }
.overview__hint { margin: 0; color: #6b7280; font-size: 13px; line-height: 1.6; }
.overview__stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 12px; }
.overview__stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}
.overview__stat-value { font-size: 22px; font-weight: 700; color: #111827; }
.overview__stat-label { font-size: 12px; color: #6b7280; }
.overview__grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
.overview__card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fff;
}
.overview__card-head { display: flex; align-items: center; justify-content: space-between; }
.overview__card-title { margin: 0; font-size: 15px; font-weight: 600; }
.overview__badge { font-size: 12px; color: #4338ca; background: #eef2ff; border-radius: 6px; padding: 1px 8px; }
.overview__meta { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin: 0; }
.overview__meta dt { font-size: 11px; color: #9ca3af; }
.overview__meta dd { margin: 0; font-size: 14px; color: #111827; }
.overview__published { margin: 0; font-size: 12px; color: #6b7280; }
.overview__actions { display: flex; gap: 10px; }
.overview__link { font-size: 13px; color: #6366f1; text-decoration: none; }
.overview__link:hover { text-decoration: underline; }
.overview__empty { color: #6b7280; font-size: 13px; }
.overview__empty-hint { margin-top: 6px; color: #9ca3af; }
.overview__legacy {
  margin-top: 8px;
  padding: 14px 16px;
  border: 1px dashed #e5e7eb;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.overview__legacy-title { margin: 0; font-size: 13px; font-weight: 600; color: #4b5563; }
.overview__legacy-text { margin: 0; font-size: 12px; color: #9ca3af; line-height: 1.6; }
</style>
