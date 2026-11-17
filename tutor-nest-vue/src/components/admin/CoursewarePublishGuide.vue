<!--
  CoursewarePublishGuide.vue - 课件发布教程弹窗（教师端）
  ----------------------------------------------------------------------------
  面向其他老师：只讲在线编辑器流程，附课件规范文档下载按钮。
-->
<template>
  <Teleport to="body">
    <div v-if="open" class="backdrop" @click.self="$emit('close')">
      <div class="modal guide" role="dialog" aria-modal="true" aria-labelledby="guide-title">
        <header class="modal__head">
          <h2 id="guide-title" class="modal__title">课件发布教程</h2>
          <button type="button" class="modal__close" aria-label="关闭教程" @click="$emit('close')">
            <i class="fas fa-times" aria-hidden="true"></i>
          </button>
        </header>

        <div class="modal__body guide__body">
          <p class="guide__lead">
            课件没有草稿。点<strong>「保存并更新」</strong>就会立即发布新版本，学员刷新页面即可看到。
          </p>

          <ol class="guide__steps">
            <li class="guide__step">
              <span class="guide__no">1</span>
              <div class="guide__step-body">
                <h3>从课件页进入</h3>
                <p>
                  在<strong>课件</strong>页的目录里点开要改的课件，就进入编辑页。
                  新建课件也在课件页的目录里完成；编辑页不提供新建或切换课件。
                </p>
              </div>
            </li>

            <li class="guide__step">
              <span class="guide__no">2</span>
              <div class="guide__step-body">
                <h3>写正文</h3>
                <p>
                  章节用 <code># 标题</code>。顶部工具栏可以插入布局、题目和图片；
                  题目写成 <code>【题干1】</code>，作答位置写 <code>【@1】</code>，编号一一对应。
                </p>
              </div>
            </li>

            <li class="guide__step">
              <span class="guide__no">3</span>
              <div class="guide__step-body">
                <h3>保存并更新</h3>
                <p>
                  点右上角<strong>「保存并更新」</strong>。该科目版本号 +1，这一篇立即上线；
                  其他篇目保持原样。保存前可用「预览」按学员视角检查，或导出 PDF。
                </p>
              </div>
            </li>

            <li class="guide__step">
              <span class="guide__no">4</span>
              <div class="guide__step-body">
                <h3>设置答案</h3>
                <p>
                  点「设置答案」逐题填写标准答案、开启自动批阅。答案保存后同样立即生效；
                  正文有未保存改动时，会先保存再打开答案页。
                </p>
              </div>
            </li>
          </ol>

          <div class="guide__notes">
            <h3>注意</h3>
            <ul>
              <li>题干、选项、<code>【@N】</code> 顶格写，不要用 Tab 或 4 个空格缩进。</li>
              <li>图片先用「插入图片」上传，正文里会自动写入 <code>![[文件名]]</code>。</li>
              <li>提示 <code>409</code> 版本冲突时，刷新页面后重新保存。</li>
            </ul>
          </div>

          <a href="/课件规范文档.md" download class="guide__dl">
            <i class="fas fa-download" aria-hidden="true"></i>
            下载课件格式规范
          </a>
        </div>

        <footer class="modal__foot">
          <button type="button" class="btn btn--primary btn--sm" @click="$emit('close')">知道了</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

function handleKeydown(event) {
  if (event.key === 'Escape' && props.open) emit('close')
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<style scoped>
.guide {
  width: min(640px, 100%);
}

.guide__body {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.guide__lead {
  margin: 0;
  padding: 12px 14px;
  border-radius: 12px;
  background: var(--paper-2, #f4f0e6);
  font-size: 0.82rem;
  line-height: 1.7;
  color: var(--text-1, #33302a);
}

.guide__lead strong {
  color: var(--ink, #22201c);
}

.guide__steps {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.guide__step {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr);
  gap: 12px;
}

.guide__no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--ink, #22201c);
  color: var(--paper-1, #fffdf8);
  font-family: var(--font-mono);
  font-size: 0.72rem;
}

.guide__step-body h3 {
  margin: 2px 0 5px;
  font-size: 0.9rem;
  color: var(--ink, #22201c);
}

.guide__step-body p {
  margin: 0;
  font-size: 0.8rem;
  line-height: 1.75;
  color: var(--text-2, #5b574e);
}

.guide__step-body code {
  padding: 1px 5px;
  border-radius: 5px;
  background: var(--paper-2, #f4f0e6);
  font-family: var(--font-mono);
  font-size: 0.76rem;
  color: var(--ink, #22201c);
}

.guide__notes {
  padding: 12px 14px;
  border: 1px solid var(--line, #d9d5cb);
  border-radius: 12px;
  background: var(--paper-1, #fffdf8);
}

.guide__notes h3 {
  margin: 0 0 6px;
  font-size: 0.82rem;
  color: var(--ink, #22201c);
}

.guide__notes ul {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 0.78rem;
  line-height: 1.65;
  color: var(--text-2, #5b574e);
}

.guide__notes code {
  font-family: var(--font-mono);
  font-size: 0.74rem;
  color: var(--ink, #22201c);
}

.guide__dl {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  font-size: 0.79rem;
  color: var(--ink, #22201c);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.guide__dl:hover {
  color: var(--text-2, #5b574e);
}

@media (max-width: 560px) {
  .guide__step {
    grid-template-columns: 24px minmax(0, 1fr);
    gap: 10px;
  }

  .guide__no {
    width: 24px;
    height: 24px;
  }
}
</style>