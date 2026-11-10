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
          <h2 id="guide-title" class="modal__title">
            <i class="fas fa-book-open-reader" aria-hidden="true"></i>
            课件发布教程
          </h2>
          <button type="button" class="modal__close" aria-label="关闭教程" @click="$emit('close')">
            <i class="fas fa-times" aria-hidden="true"></i>
          </button>
        </header>

        <div class="modal__body guide__body">
          <p class="guide__lead">
            课件只有两种状态：<strong>草稿</strong>（只有你看得到）和<strong>发布版本</strong>（学员看到的就是它）。
            保存草稿不会影响学员；点「发布」之后，学员立刻看到新内容。
          </p>

          <!-- 格式规范下载 -->
          <section class="guide__section">
            <h3 class="guide__title"><span class="guide__no">1</span>课件格式规范</h3>
            <div class="guide__card">
              <p class="guide__card-text">
                写课件前请先下载格式规范文档，里面包含占位符写法、排版规则、三种布局示例和常见错误。
              </p>
              <a href="/课件规范文档.md" download class="btn btn--primary btn--sm guide__dl">
                <i class="fas fa-download" aria-hidden="true"></i>
                下载课件规范文档.md
              </a>
            </div>
          </section>

          <!-- 2 -->
          <section class="guide__section">
            <h3 class="guide__title"><span class="guide__no">2</span>在线编辑器流程</h3>

            <div class="guide__card">
              <h4 class="guide__card-title"><i class="fas fa-pen-nib"></i> 四步发布一篇课件</h4>
              <ol class="guide__steps">
                <li>左侧「我的课件」选择一篇，或点「新建草稿」。</li>
                <li>写 Markdown：章节用 <code># </code>，题干用 <code>【题干1】</code>，作答占位用 <code>【@1】</code>。</li>
                <li>点「校验」确认题干编号、作答占位符和答案键都对得上。</li>
                <li>点「保存草稿」（只有自己可见），确认无误后点「发布」。</li>
              </ol>
              <p class="guide__note">
                发布是<strong>整科</strong>的：该科目当前所有草稿一起上线，版本号 +1；学员立即看到新版本。
                发布成功后这些草稿会<strong>自动清空</strong>。
              </p>
            </div>
          </section>

          <!-- 3 -->
          <section class="guide__section">
            <h3 class="guide__title"><span class="guide__no">3</span>发布前检查清单</h3>
            <ul class="guide__list">
              <li><strong>科目</strong>必填，否则「发布」按钮是灰的。</li>
              <li>每道题都有 <code>【题干N】</code>，且与 <code>【@N】</code> 编号一一对应。</li>
              <li>题干、选项、<code>【@N】</code> 顶格写，不要用 Tab 缩进（否则会变成代码块）。</li>
              <li>图片必须上传后用 <code>![[文件名]]</code> 引用，不能用外链图片。</li>
              <li>报 <code>409 版本冲突</code>：另一个标签页已经发布过，刷新页面后再发布。</li>
            </ul>
          </section>

          <!-- 4 -->
          <section class="guide__section">
            <h3 class="guide__title"><span class="guide__no">4</span>常见问题</h3>
            <dl class="guide__faq">
              <dt>草稿是怎么冒出来的？</dt>
              <dd>点「保存草稿」会生成一条草稿，只有你自己可见；发布成功后自动清空。</dd>

              <dt>怎么删除草稿？</dt>
              <dd>编辑器底部「删除草稿」，或左侧草稿列表右侧的「删除」。已发布的版本不受影响。</dd>

              <dt>发布后学员多久能看到？</dt>
              <dd>立即。发布成功即切换版本指针，学员刷新页面即可读到新版本。</dd>

              <dt>选项为什么被包成了代码块？</dt>
              <dd>选项行前面不要用 Tab 或 4 个空格缩进，顶格写 <code>A. 选项内容</code> 即可。</dd>
            </dl>
          </section>
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
  width: min(680px, 100%);
}

.guide__body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.guide__lead {
  margin: 0;
  padding: 12px 14px;
  border: 1.5px dashed var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-2);
  font-size: 0.84rem;
  line-height: 1.7;
  color: var(--text-1);
}

.guide__section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guide__title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 0.95rem;
  color: var(--ink);
}

.guide__no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1.5px solid var(--ink);
  border-radius: 50%;
  background: var(--butter, #ffe9a8);
  font-family: var(--font-mono);
  font-size: 0.74rem;
  font-weight: 700;
}

.guide__card {
  padding: 12px 14px;
  border: 1.5px solid var(--ink);
  border-radius: var(--r-sm);
  background: var(--paper-1);
  box-shadow: 2px 2px 0 var(--ink);
}

.guide__card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 0.88rem;
  color: var(--ink);
}

.guide__card-text {
  margin: 0 0 10px;
  font-size: 0.82rem;
  line-height: 1.7;
  color: var(--text-2);
}

.guide__dl {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  text-decoration: none;
}

.guide__steps {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.82rem;
  line-height: 1.7;
  color: var(--text-2);
}

.guide__note {
  margin: 8px 0 0;
  font-size: 0.78rem;
  line-height: 1.7;
  color: var(--text-2);
}

.guide__list {
  margin: 0;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 0.82rem;
  line-height: 1.7;
  color: var(--text-2);
}

.guide__faq {
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 0.82rem;
  line-height: 1.7;
}

.guide__faq dt {
  font-weight: 700;
  color: var(--ink);
}

.guide__faq dd {
  margin: 0 0 4px;
  color: var(--text-2);
}

.guide code {
  padding: 1px 4px;
  border-radius: 4px;
  background: var(--paper-2);
  font-family: var(--font-mono);
  font-size: 0.76rem;
}
</style>
