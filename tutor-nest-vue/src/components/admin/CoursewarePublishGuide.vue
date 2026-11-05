<!--
  CoursewarePublishGuide.vue - 课件发布教程弹窗
  ----------------------------------------------------------------------------
  编辑器里所有"怎么发布"的说明集中在这里：概念、三条发布路线、发布前检查、
  常见问题与 CLI 命令。原先散落在页面顶部与侧栏的教学文案已全部并入本弹窗。
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

          <!-- 1 -->
          <section class="guide__section">
            <h3 class="guide__title"><span class="guide__no">1</span>三条发布路线，选一条就行</h3>

            <div class="guide__card">
              <h4 class="guide__card-title"><i class="fas fa-pen-nib"></i> A. 在线编辑器（最常用）</h4>
              <ol class="guide__steps">
                <li>左侧「我的课件」选择一篇，或点「新建草稿」按布局起稿。</li>
                <li>写 Markdown：章节用 <code># </code>，布局用 <code>&lt;!-- @layout:A --&gt;</code>，题干用 <code>【题干1】</code>，作答占位用 <code>【@1】</code>。</li>
                <li>点「校验」确认题干编号、作答占位符和答案键都对得上。</li>
                <li>点「保存草稿」（只有自己可见），确认无误后点「发布」。</li>
              </ol>
              <p class="guide__note">
                发布是<strong>整科</strong>的：该科目当前所有草稿一起上线，版本号 +1；学员立即看到新版本。
                发布成功后这些草稿会<strong>自动清空</strong>，列表里不会再挂着已发布的草稿。
              </p>
            </div>

            <div class="guide__card">
              <h4 class="guide__card-title"><i class="fas fa-box-archive"></i> B. 站内静态课件接管</h4>
              <p class="guide__card-text">
                目录里标着「静态讲义」的篇目，正文还在仓库的 <code>public/articles</code> 里，从未走过发布。
                接管后正文交给服务端托管，学员端仍按原编号阅读，<strong>不需要重新授权</strong>。
              </p>
              <ol class="guide__steps">
                <li><strong>单篇</strong>：点开该篇 →「编辑这篇」→ 保存草稿 → 发布。</li>
                <li><strong>批量</strong>：回「我的课件」，点右上角「一键接管静态课件（N）」，会按原编号建草稿并按科目整科发布。</li>
              </ol>
              <p class="guide__note guide__note--warn">
                不要用「导入 zip」接管旧课件：导入会重新分配 <code>article_id</code>，学员的旧授权匹配不到，等于新开一篇。
              </p>
            </div>

            <div class="guide__card">
              <h4 class="guide__card-title"><i class="fas fa-code-branch"></i> C. Git 工作区（本地编辑器，可选）</h4>
              <p class="guide__card-text">
                习惯用本地编辑器时：<code>pull</code> 拉下来 → 本地改 → <code>push</code> 推回服务端。
                <strong>push 只落草稿</strong>，发布仍要在网页点「发布」。
              </p>
              <ol class="guide__steps guide__steps--cmd">
                <li v-for="(step, index) in cliSteps" :key="index">
                  <div class="guide__cmd-item">
                    <span class="guide__cmd-title">{{ index + 1 }}. {{ step.title }}</span>
                    <code v-if="step.command" class="guide__cmd">{{ step.command }}</code>
                    <span v-if="step.hint" class="guide__hint">{{ step.hint }}</span>
                  </div>
                  <button v-if="step.command" type="button" class="btn btn--ghost btn--sm guide__copy"
                    @click="copyCommand(step.command)">
                    复制
                  </button>
                </li>
              </ol>
            </div>
          </section>

          <!-- 2 -->
          <section class="guide__section">
            <h3 class="guide__title"><span class="guide__no">2</span>发布前检查清单</h3>
            <ul class="guide__list">
              <li><strong>科目</strong>必填，否则「发布」按钮是灰的。</li>
              <li>题干编号、作答占位符编号连续；缺答案的题按空答案发布（之后可在答案面板补齐）。</li>
              <li>图片放在 <code>public/articles/图片/</code>，否则发布后路径解析不到。</li>
              <li>报 <code>409 版本冲突</code>：另一个标签页或 CLI 已经发布过，刷新页面后再发布。</li>
            </ul>
          </section>

          <!-- 3 -->
          <section class="guide__section">
            <h3 class="guide__title"><span class="guide__no">3</span>常见问题</h3>
            <dl class="guide__faq">
              <dt>草稿是怎么冒出来的？</dt>
              <dd>点「保存草稿」、或点「发布」前的自动保存，都会生成一条草稿。它只存在服务端，学员看不到；
                发布成功后，本次并入版本的草稿会自动清空。</dd>

              <dt>怎么删除草稿？</dt>
              <dd>编辑器底部「删除草稿」，或左侧「我的草稿」每条右侧的「删除」。已发布的版本不受影响，学员看到的仍是上一版。</dd>

              <dt>为什么有的课件提示「正文缺失 · 需重新发布」？</dt>
              <dd>该篇在版本清单里，但正文文件（blob）不在存储里（存储卷重建、历史环境被清空）。
                打开时会自动用站内讲义顶上，保存草稿再点「发布」就把正文写回去了。</dd>

              <dt>发布后学员多久能看到？</dt>
              <dd>立即。发布成功即切换版本指针，学员刷新页面即可读到新版本。</dd>

              <dt>旧认领页还要用吗？</dt>
              <dd>不需要。课件归属已经改为「老师命名空间 + 发布版本」，旧认领页只保留历史记录。</dd>
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
import { showToast } from '@/utils/toast'

const props = defineProps({
  open: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

function handleKeydown(event) {
  if (event.key === 'Escape' && props.open) emit('close')
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))

const cliSteps = [
  {
    title: '安装 Node.js 18+ 与 Git',
    command: '',
    hint: 'https://nodejs.org/ 与 https://git-scm.com/ ，Windows 默认选项即可'
  },
  {
    title: '登录后取会话 cookie，填进环境变量',
    command: "$env:TUTOR_SESSION='<浏览器 cookie 值>'",
    hint: '开发者工具 → Application → Cookies → TUTOR_SESSION'
  },
  {
    title: '自检环境',
    command: 'node scripts/courseware-cli.mjs doctor',
    hint: '未通过时会直接告诉你缺什么'
  },
  {
    title: '拉取 → 本地编辑 → 推送',
    command: 'node scripts/courseware-cli.mjs pull --subject 高中化学 --dir ./courseware',
    hint: '改完用同样的 --subject 执行 push；冲突会提示先 pull，不会覆盖你的改动'
  }
]

async function copyCommand(command) {
  try {
    await navigator.clipboard.writeText(command)
    showToast('命令已复制', 'success')
  } catch {
    showToast('复制失败，请手动选择命令文本', 'warn')
  }
}
</script>

<style scoped>
.guide {
  width: min(760px, 100%);
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
  margin: 0 0 8px;
  font-size: 0.82rem;
  line-height: 1.7;
  color: var(--text-2);
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

.guide__steps--cmd {
  padding-left: 0;
  list-style: none;
}

.guide__steps--cmd li {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.guide__cmd-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.guide__cmd-title {
  color: var(--ink);
  font-weight: 700;
}

.guide__cmd {
  display: block;
  padding: 3px 8px;
  border: 1px solid var(--line, #d8d8d8);
  border-radius: 6px;
  background: var(--paper-2);
  font-family: var(--font-mono);
  font-size: 0.74rem;
  color: var(--ink);
  word-break: break-all;
}

.guide__hint {
  font-size: 0.74rem;
  color: var(--text-3);
}

.guide__copy {
  flex: none;
  align-self: center;
  padding: 3px 10px;
  font-size: 0.72rem;
}

.guide__note {
  margin: 8px 0 0;
  font-size: 0.78rem;
  line-height: 1.7;
  color: var(--text-2);
}

.guide__note--warn {
  color: var(--vermilion);
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
