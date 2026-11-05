<!--
  CoursewarePreviewPane.vue - 课件正文预览（编辑器与「我的课件」共用）
  ----------------------------------------------------------------------------
  用站点正文同一套切分规则（marked + `#` 章节 + `---` 双栏 + 布局指令 + 占位符）
  渲染成纸面卡片，尽量贴近学员在正文页看到的样子。
  只负责"看着像"，不解析题目交互（那是 ArticleDetailView 的职责）。
-->
<template>
  <div class="preview-pane">
    <header v-if="title || status" class="preview-pane__head">
      <div class="preview-pane__titles">
        <h2 v-if="title" class="preview-pane__title">{{ title }}</h2>
        <p v-if="subtitle" class="preview-pane__subtitle">{{ subtitle }}</p>
      </div>
      <span v-if="status" class="preview-pane__status" :class="`is-${statusTone}`">{{ status }}</span>
    </header>

    <slot name="actions" />

    <div v-if="loading" class="preview-pane__empty" role="status">
      <span class="spinner" aria-hidden="true"></span>
      <span>正在读取正文…</span>
    </div>

    <div v-else-if="html" class="preview-pane__body" v-html="html"></div>

    <div v-else class="preview-pane__empty">
      <i class="fas fa-file-lines" aria-hidden="true"></i>
      <span>{{ emptyText }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { renderCoursewarePreview } from '@/utils/coursewarePreview'

const props = defineProps({
  markdown: { type: String, default: '' },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  status: { type: String, default: '' },
  /** live / draft / static / missing：决定状态色 */
  statusTone: { type: String, default: 'live' },
  loading: { type: Boolean, default: false },
  emptyText: { type: String, default: '正文还是空的。' }
})

const html = computed(() => renderCoursewarePreview(props.markdown))
</script>

<style scoped>
.preview-pane {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.preview-pane__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.preview-pane__titles {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.preview-pane__title {
  margin: 0;
  font-size: 1.05rem;
  line-height: 1.4;
  color: var(--ink);
}

.preview-pane__subtitle {
  margin: 0;
  font-size: 0.76rem;
  color: var(--text-2);
  word-break: break-all;
}

.preview-pane__status {
  flex: none;
  padding: 2px 8px;
  border: 1.5px solid var(--ink);
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 700;
  white-space: nowrap;
}

.preview-pane__status.is-live {
  background: var(--lime, #d9f99d);
  color: var(--ink);
}

.preview-pane__status.is-draft {
  background: var(--amber, #fde68a);
  color: var(--ink);
}

.preview-pane__status.is-static {
  background: var(--paper-2, #f4f1e8);
  color: var(--ink);
}

.preview-pane__status.is-missing {
  background: var(--coral, #fecaca);
  color: var(--ink);
}

.preview-pane__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 2px 12px;
  color: var(--text-1);
  font-size: 0.9rem;
  line-height: 1.8;
}

.preview-pane__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 160px;
  color: var(--text-2);
  font-size: 0.82rem;
  text-align: center;
}

.preview-pane__empty i {
  font-size: 1.4rem;
  opacity: 0.5;
}

/* ---- 正文排版：贴近学员正文页 ---- */
.preview-pane :deep(.preview-section) {
  margin: 0 0 16px;
  padding: 16px 18px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  box-shadow: 3px 3px 0 var(--ink);
}

.preview-pane :deep(.layout) {
  display: inline-block;
  margin: 0 0 10px;
  padding: 1px 8px;
  border: 1px dashed var(--ink);
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--text-2);
}

.preview-pane :deep(.preview-section h1) {
  margin: 0 0 10px;
  padding-bottom: 6px;
  border-bottom: 2px solid var(--ink);
  font-size: 1.12rem;
  line-height: 1.45;
  color: var(--ink);
}

.preview-pane :deep(.preview-section h2) {
  margin: 16px 0 8px;
  font-size: 1rem;
  color: var(--ink);
}

.preview-pane :deep(.preview-section h3) {
  margin: 12px 0 6px;
  font-size: 0.92rem;
  color: var(--ink);
}

.preview-pane :deep(p) {
  margin: 0 0 10px;
  white-space: normal;
  word-break: break-word;
}

.preview-pane :deep(strong) {
  color: var(--ink);
}

.preview-pane :deep(blockquote) {
  margin: 0 0 10px;
  padding: 6px 12px;
  border-left: 3px solid var(--ink);
  background: var(--paper-2, #f7f5ee);
  color: var(--text-2);
}

.preview-pane :deep(ul),
.preview-pane :deep(ol) {
  margin: 0 0 10px;
  padding-left: 20px;
}

.preview-pane :deep(table) {
  width: 100%;
  margin: 0 0 12px;
  border-collapse: collapse;
  font-size: 0.82rem;
}

.preview-pane :deep(th),
.preview-pane :deep(td) {
  padding: 6px 8px;
  border: 1px solid var(--line, #d8d8d8);
  text-align: left;
  vertical-align: top;
}

.preview-pane :deep(th) {
  background: var(--paper-2, #f7f5ee);
  color: var(--ink);
}

.preview-pane :deep(code) {
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--paper-2, #f2efe6);
  font-size: 0.85em;
}

.preview-pane :deep(.preview-columns) {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr);
  gap: 16px;
}

.preview-pane :deep(.preview-side) {
  padding-left: 14px;
  border-left: 1.5px dashed var(--line, #cfcfcf);
}

.preview-pane :deep(.preview-stem) {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--amber, #fde68a);
  font-weight: 700;
}

.preview-pane :deep(.slot) {
  display: inline-block;
  margin: 4px 0;
  padding: 6px 14px;
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-2, #f7f5ee);
  color: var(--text-2);
  font-size: 0.78rem;
}

@media (max-width: 720px) {
  .preview-pane :deep(.preview-columns) {
    grid-template-columns: minmax(0, 1fr);
  }

  .preview-pane :deep(.preview-side) {
    padding-left: 0;
    border-left: none;
    border-top: 1.5px dashed var(--line, #cfcfcf);
    padding-top: 12px;
  }
}
</style>
