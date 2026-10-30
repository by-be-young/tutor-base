<!--
  WrongTrainingView.vue - 训练舱
  ----------------------------------------------------------------------------
  功能说明：
    1. 独立页面，对错题本中的题目进行重做训练
    2. 训练流程：设置（学科/范围）→ 逐题作答 → 对照正确答案 → 自我判定
    3. 做错的题 wrong_count + 1，累积到总做错次数（写回错题本）
    4. 做对的题可标记为「已掌握」
  视觉：Riso Campus「训练舱」—— 试卷纸卡、墨线进度、彩色印章对错标记。
-->
<template>
    <div class="page page--wide wt">
        <!-- 未登录 -->
        <section v-if="!authStore.isLoggedIn" class="panel panel--pad wt-guest">
            <span class="empty__icon"><i class="fas fa-lock"></i></span>
            <h1 class="wt-guest__title">登录后进入训练舱</h1>
            <p class="empty__text">训练记录、做错次数与掌握状态都会写回你的错题本。</p>
            <router-link to="/" class="btn btn--primary">
                <i class="fas fa-right-to-bracket"></i> 去登录
            </router-link>
        </section>

        <template v-else>
            <!-- ==================== 设置区 ==================== -->
            <template v-if="phase === 'setup'">
                <header class="page-head page-head--row">
                    <div class="wt-head">
                        <span class="eyebrow">NEST / 训练舱</span>
                        <h1 class="page-title">训练<span class="text-gradient">舱</span></h1>
                        <p class="page-sub">温故而知新 —— 重做错题，直到它们不再是错题。</p>
                    </div>
                    <router-link to="/wrong-questions" class="btn btn--ghost">
                        <i class="fas fa-arrow-left"></i> 返回错题本
                    </router-link>
                </header>

                <div class="wt-setup">
                    <!-- 配置面板 -->
                    <section class="panel panel--glow panel--pad wt-setup__panel">
                        <div class="wt-setup__group">
                            <span class="eyebrow">01 / 学科</span>
                            <select v-model="subjectFilter" class="select" aria-label="选择训练学科">
                                <option value="">全部学科</option>
                                <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
                            </select>
                        </div>

                        <div class="wt-setup__group">
                            <span class="eyebrow">02 / 范围</span>
                            <div class="wt-scope" role="group" aria-label="训练范围">
                                <button type="button" class="wt-scope__btn"
                                    :class="{ 'is-active': scope === 'unmastered' }" :aria-pressed="scope === 'unmastered'"
                                    @click="scope = 'unmastered'">
                                    <span class="wt-scope__icon is-coral"><i class="fas fa-hourglass-half"></i></span>
                                    <span class="wt-scope__text">
                                        <strong>未掌握</strong>
                                        <small>{{ unmasteredCount }} 道待攻克</small>
                                    </span>
                                </button>
                                <button type="button" class="wt-scope__btn"
                                    :class="{ 'is-active': scope === 'all' }" :aria-pressed="scope === 'all'"
                                    @click="scope = 'all'">
                                    <span class="wt-scope__icon is-cyan"><i class="fas fa-layer-group"></i></span>
                                    <span class="wt-scope__text">
                                        <strong>全部错题</strong>
                                        <small>{{ allCount }} 道全量复习</small>
                                    </span>
                                </button>
                            </div>
                        </div>

                        <div class="wt-launch">
                            <div class="wt-preview">
                                <span class="wt-preview__label">本次可训练</span>
                                <span class="wt-preview__num text-gradient">
                                    <CountUp :key="`trainable-${trainableQuestions.length}`"
                                        :value="trainableQuestions.length" />
                                </span>
                                <span class="wt-preview__unit">道题</span>
                            </div>
                            <button type="button" class="btn btn--primary btn--lg btn--block"
                                :disabled="!booted || trainableQuestions.length === 0" @click="startTraining">
                                <i class="fas fa-play"></i> 开始训练
                            </button>
                            <p v-if="!booted" class="field-hint wt-setup__hint">
                                <span class="spinner"></span> 正在载入错题…
                            </p>
                            <p v-else-if="trainableQuestions.length === 0" class="field-error wt-setup__empty">
                                <i class="fas fa-circle-info"></i>
                                当前条件下没有可训练的题目，先添加或收集一些错题吧。
                            </p>
                            <p v-else class="field-hint wt-setup__hint">
                                作答后对照正确答案自行判定；判错的题会累计做错次数。
                            </p>
                            <p v-if="redoQuestionId && !redoQuestionFound" class="field-error wt-setup__empty">
                                <i class="fas fa-circle-info"></i>
                                未找到要重做的错题，可能已被删除，请从错题本重新选择。
                            </p>
                        </div>
                    </section>

                    <!-- 训练说明 -->
                    <aside class="panel panel--pad wt-brief">
                        <h2 class="panel-title"><i class="fas fa-circle-info"></i> 训练规则</h2>
                        <ol class="wt-brief__list">
                            <li>
                                <span class="wt-brief__idx mono">01</span>
                                <div>
                                    <strong>凭记忆作答</strong>
                                    <p>不看原答案，把过程完整写下来。</p>
                                </div>
                            </li>
                            <li>
                                <span class="wt-brief__idx mono">02</span>
                                <div>
                                    <strong>对照答案自判</strong>
                                    <p>诚实判定：做对 / 还做错。</p>
                                </div>
                            </li>
                            <li>
                                <span class="wt-brief__idx mono">03</span>
                                <div>
                                    <strong>写回错题本</strong>
                                    <p>做错次数累加，做对可标记掌握。</p>
                                </div>
                            </li>
                        </ol>

                        <div class="wt-brief__stats">
                            <div class="wt-brief__stat is-paper">
                                <span class="wt-brief__num">{{ wrongQuestionsStore.questions.length }}</span>
                                <span class="wt-brief__cap">错题总数</span>
                            </div>
                            <div class="wt-brief__stat is-sky">
                                <span class="wt-brief__num">{{ unmasteredCount }}</span>
                                <span class="wt-brief__cap">未掌握</span>
                            </div>
                        </div>

                        <router-link to="/wrong-questions" class="btn btn--soft btn--block">
                            <i class="fas fa-book-open"></i> 先去整理错题
                        </router-link>
                    </aside>
                </div>
            </template>

            <!-- ==================== 训练区 ==================== -->
            <template v-else-if="phase === 'training'">
                <div class="wt-shell">
                    <!-- HUD -->
                    <section class="wt-hud panel panel--tight" aria-label="训练进度">
                        <div class="wt-hud__top">
                            <div class="wt-hud__title">
                                <span class="eyebrow">训练舱 · 进行中</span>
                                <strong>第 {{ currentIndex + 1 }} / {{ trainList.length }} 题</strong>
                                <span v-if="redoQuestionId" class="tag">单题重做</span>
                            </div>
                            <div class="wt-hud__counters">
                                <span class="chip chip--lime"><i class="fas fa-check"></i> 对 {{ stats.correct }}</span>
                                <span class="chip wt-chip-wrong"><i class="fas fa-xmark"></i> 错 {{ stats.wrong }}</span>
                                <button type="button" class="btn btn--ghost btn--sm" @click="quitTraining">
                                    <i class="fas fa-right-from-bracket"></i> 退出
                                </button>
                            </div>
                        </div>

                        <div class="progress wt-hud__progress" role="progressbar" aria-label="训练进度"
                            :aria-valuenow="progressPercent" aria-valuemin="0" aria-valuemax="100">
                            <span class="progress__bar" :style="{ width: progressPercent + '%' }"></span>
                        </div>

                        <div class="wt-hud__dots" aria-hidden="true">
                            <span v-for="(t, idx) in trainList" :key="`dot-${t.q.id}`" class="wt-dot" :class="{
                                'is-current': idx === currentIndex,
                                'is-correct': t.status === 'correct',
                                'is-wrong': t.status === 'wrong'
                            }"></span>
                        </div>
                    </section>

                    <!-- 题卡（试卷纸） -->
                    <article ref="trainingCard" class="wt-card panel">
                        <div class="wt-card__head">
                            <span class="chip" :class="subjectChipClass(item.resolved.subject)">
                                <i class="fas fa-bookmark"></i>
                                {{ item.resolved.subject || '未分类' }}
                            </span>
                            <span class="tag">
                                第 {{ item.resolved.order ?? item.q.source_question_id }} 题
                            </span>
                            <span class="wt-wrong-times" :title="`已错 ${item.q.wrong_count} 次`">
                                <i class="fas fa-rotate-left"></i> 错 {{ item.q.wrong_count }} 次
                            </span>
                            <span v-if="item.q.mastered" class="chip chip--lime">
                                <i class="fas fa-circle-check"></i> 已掌握
                            </span>
                        </div>

                        <!-- 题干（:key 强制重建，避免 KaTeX 节点与 Vue patch 冲突） -->
                        <div class="wt-question-text"
                            v-html="item.resolved.questionText ? renderQuestionText(item.resolved.questionText) : '（无题目内容）'"
                            :key="'stem-' + item.q.id"></div>

                        <!-- 作答 -->
                        <template v-if="item.status === 'answering'">
                            <div class="field wt-answer">
                                <label class="field-label">
                                    你的作答
                                    <span class="field-hint">凭记忆写完整，写完再对照</span>
                                </label>
                                <textarea v-model="item.answer" class="textarea wt-answer__input" rows="4"
                                    placeholder="凭记忆写出你的答案…"></textarea>
                            </div>
                            <div class="wt-actions">
                                <span class="wt-hint">
                                    <span class="kbd">Ctrl</span> + <span class="kbd">Enter</span> 提交
                                </span>
                                <button type="button" class="btn btn--primary" :disabled="!item.answer.trim()"
                                    @click="submitAnswer">
                                    <i class="fas fa-check"></i> 提交判定
                                </button>
                            </div>
                        </template>

                        <!-- 对照区（未开启自动批阅：显示答案，由用户自行评判） -->
                        <template v-else-if="item.status === 'comparing'">
                            <div class="wt-compare">
                                <div class="wt-compare__col is-my">
                                    <span class="wt-compare__label"><i class="fas fa-pen"></i> 我的答案</span>
                                    <div class="wt-compare__text" v-html="safeText(item.answer)"></div>
                                </div>
                                <div class="wt-compare__col is-correct">
                                    <span class="wt-compare__label"><i class="fas fa-check"></i> 正确答案</span>
                                    <div class="wt-compare__text"
                                        v-html="item.resolved.correctAnswer ? safeText(item.resolved.correctAnswer) : '（未设置答案，请自行判断）'">
                                    </div>
                                </div>
                            </div>

                            <div class="wt-judge-tip">
                                <i class="fas fa-user-check"></i>
                                对照答案，如实自判 —— 这一步决定错题本是否可信。
                            </div>

                            <div class="wt-actions wt-actions--center">
                                <button type="button" class="btn btn--lg wt-btn-correct" @click="judge(true)">
                                    <i class="fas fa-thumbs-up"></i> 做对了
                                    <span class="kbd">1</span>
                                </button>
                                <button type="button" class="btn btn--lg wt-btn-wrong" @click="judge(false)">
                                    <i class="fas fa-thumbs-down"></i> 还做错了
                                    <span class="kbd">2</span>
                                </button>
                            </div>
                        </template>

                        <!-- 结果区（彩色印章） -->
                        <template v-else>
                            <div class="wt-result" :class="item.status === 'correct' ? 'is-correct' : 'is-wrong'">
                                <span class="wt-result__icon">
                                    <i :class="item.status === 'correct' ? 'fas fa-circle-check' : 'fas fa-circle-xmark'"></i>
                                </span>
                                <div class="wt-result__body">
                                    <strong class="wt-result__title">
                                        {{ item.status === 'correct' ? '做对了！' : '又错了，再接再厉' }}
                                        <span v-if="item.autoJudged" class="tag">自动评判</span>
                                    </strong>
                                    <span class="wt-result__sub">
                                        {{ item.status === 'wrong'
                                            ? `已累计一次错误（做错次数 ${item.q.wrong_count} 次）`
                                            : '本题可以标记为已掌握' }}
                                    </span>
                                </div>
                                <button v-if="item.status === 'correct' && !item.masteredDone" type="button"
                                    class="btn btn--warm btn--sm" @click="markMastered">
                                    <i class="fas fa-star"></i> 标记掌握
                                </button>
                                <span v-if="item.masteredDone" class="chip chip--lime">
                                    <i class="fas fa-star"></i> 已掌握
                                </span>
                            </div>

                            <div class="wt-actions">
                                <span class="wt-hint">
                                    <span class="kbd">Alt</span> + <span class="kbd">←</span> /
                                    <span class="kbd">→</span> 切换题目
                                </span>
                                <button v-if="currentIndex < trainList.length - 1" type="button"
                                    class="btn btn--primary" @click="nextQuestion">
                                    下一题 <i class="fas fa-arrow-right"></i>
                                </button>
                                <button v-else type="button" class="btn btn--primary" @click="finishTraining">
                                    完成训练 <i class="fas fa-flag-checkered"></i>
                                </button>
                            </div>
                        </template>
                    </article>

                    <!-- 上下题导航（不回答也能切换） -->
                    <nav class="wt-nav" aria-label="题目切换">
                        <button type="button" class="btn btn--ghost" :disabled="!hasPrev" @click="goPrev">
                            <i class="fas fa-arrow-left"></i> 上一题
                        </button>
                        <span class="wt-nav__hint">无需作答也可切换题目</span>
                        <button type="button" class="btn btn--ghost" :disabled="!hasNext" @click="goNext">
                            下一题 <i class="fas fa-arrow-right"></i>
                        </button>
                    </nav>

                    <!-- 退出训练（单题重做模式返回错题本，普通模式回到设置页） -->
                    <div class="wt-quit-row">
                        <button type="button" class="btn btn--ghost btn--sm" @click="quitTraining">
                            <i class="fas fa-right-from-bracket"></i> 退出训练
                        </button>
                    </div>
                </div>
            </template>

            <!-- ==================== 完成区 ==================== -->
            <template v-else>
                <div class="wt-done">
                    <!-- 庆祝纸屑 -->
                    <div class="wt-done__confetti" aria-hidden="true">
                        <span v-for="i in confettiPieces" :key="`confetti-${i}`" class="wt-confetti"
                            :style="confettiStyle(i)"></span>
                    </div>
                    <!-- 印刷错位色块 -->
                    <span class="wt-done__shape wt-done__shape--circle" aria-hidden="true"></span>
                    <span class="wt-done__shape wt-done__shape--rect" aria-hidden="true"></span>

                    <header class="wt-done__head">
                        <span class="eyebrow">NEST / 训练舱 · 结算</span>
                        <h1 class="page-title">训练<span class="text-gradient">完成</span>！</h1>
                        <p class="page-sub">本次共训练 {{ trainList.length }} 道题，记录已写回错题本。</p>
                    </header>

                    <section class="panel panel--glow wt-done__panel" aria-label="训练结算">
                        <div class="wt-done__stats">
                            <div class="wt-done__stat wt-done__stat--total">
                                <span class="wt-done__num">
                                    <CountUp :key="`done-total-${trainList.length}`" :value="trainList.length" />
                                </span>
                                <span class="wt-done__label">总题数</span>
                            </div>
                            <div class="wt-done__stat wt-done__stat--correct">
                                <span class="wt-done__num">
                                    <CountUp :key="`done-correct-${stats.correct}`" :value="stats.correct" />
                                </span>
                                <span class="wt-done__label">做对</span>
                            </div>
                            <div class="wt-done__stat wt-done__stat--wrong">
                                <span class="wt-done__num">
                                    <CountUp :key="`done-wrong-${stats.wrong}`" :value="stats.wrong" />
                                </span>
                                <span class="wt-done__label">做错</span>
                            </div>
                            <div class="wt-done__stat wt-done__stat--rate">
                                <span class="wt-done__num">
                                    <CountUp :key="`done-rate-${accuracyPercent}`" :value="accuracyPercent"
                                        suffix="%" />
                                </span>
                                <span class="wt-done__label">正确率</span>
                            </div>
                        </div>

                        <div class="progress wt-done__progress" aria-hidden="true">
                            <span class="progress__bar" :style="{ width: accuracyPercent + '%' }"></span>
                        </div>

                        <div class="wt-done__tip" :class="stats.wrong > 0 ? 'is-wrong' : 'is-correct'">
                            <i :class="stats.wrong > 0 ? 'fas fa-triangle-exclamation' : 'fas fa-trophy'"></i>
                            <span v-if="stats.wrong > 0">
                                有 {{ stats.wrong }} 道题仍未掌握，做错次数已累积到错题本，建议继续巩固。
                            </span>
                            <span v-else>全部做对，太棒了！记得把做对的题标记为已掌握。</span>
                        </div>

                        <div class="wt-done__actions">
                            <button type="button" class="btn btn--primary btn--lg" @click="restart">
                                <i class="fas fa-rotate-right"></i> 再练一次
                            </button>
                            <router-link to="/wrong-questions" class="btn btn--soft btn--lg">
                                <i class="fas fa-book-medical"></i> 返回错题本
                            </router-link>
                        </div>
                    </section>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useWrongQuestionsStore } from '@/stores/wrongQuestionsStore'
import { useWrongQuestionsResolve } from '@/composables/useWrongQuestionsResolve'
import { useKatex } from '@/composables/useKatex'
import { useImageEmbed } from '@/composables/useImageEmbed'
import { renderMarkdownTable } from '@/utils/questionText'
import { showToast } from '@/utils/toast'
import CountUp from '@/components/ui/CountUp.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const wrongQuestionsStore = useWrongQuestionsStore()
const { resolvedOf, resolveQuestions } = useWrongQuestionsResolve()
const { renderMath } = useKatex()

// 图片嵌入解析（与文章页一致：![[图片]] → <img>）
const { processMarkdown, setBasePath } = useImageEmbed()
setBasePath('articles/图片/')

// ========== 渲染工具 ==========
/** 题干内容渲染：markdown 表格 → <table>，图片嵌入（![[图片]] → <img>），换行转 <br>，$...$ 公式由 KaTeX 渲染 */
function renderQuestionText(text) {
    return processMarkdown(renderMarkdownTable(text)).replace(/\n/g, '<br>')
}

function safeText(text) {
    return String(text ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/\n/g, '<br>')
}

// 学科 → 徽章配色（Riso 印刷色）
function subjectChipClass(subject) {
    if (subject === '高中化学') return 'chip--cyan'
    if (subject === '高中英语') return 'chip--pink'
    if (subject === '初中英语') return 'chip--amber'
    return 'chip--violet'
}

// ========== 阶段与设置 ==========
const phase = ref('setup')   // setup | training | done
const subjectFilter = ref('')
const scope = ref('unmastered')

// 单题重做模式（错题本「重做该题」进入）：?question=错题id，只训练这一题
const redoQuestionId = computed(() => route.query.question || '')
const redoQuestionFound = computed(() =>
    Boolean(redoQuestionId.value) && wrongQuestionsStore.questions
        .some(x => String(x.id) === String(redoQuestionId.value)))

const subjectOptions = computed(() => {
    const set = new Set()
    wrongQuestionsStore.questions.forEach(q => {
        const subject = resolvedOf(q).subject
        if (subject) set.add(subject)
    })
    return Array.from(set).sort((a, b) => a.localeCompare(b))
})

// 待训练题目（按学科 + 范围过滤）
const trainableQuestions = computed(() => {
    return wrongQuestionsStore.questions.filter(q => {
        if (subjectFilter.value && resolvedOf(q).subject !== subjectFilter.value) return false
        if (scope.value === 'unmastered' && q.mastered) return false
        return true
    })
})

const allCount = computed(() => wrongQuestionsStore.questions.length)
const unmasteredCount = computed(() => wrongQuestionsStore.questions.filter(q => !q.mastered).length)

// ========== 训练会话 ==========
// 训练项：{ q, resolved, answer, status, masteredDone }
const trainList = ref([])
const currentIndex = ref(0)
const trainingCard = ref(null)

const item = computed(() => trainList.value[currentIndex.value] || null)

const hasPrev = computed(() => currentIndex.value > 0)
const hasNext = computed(() => currentIndex.value < trainList.value.length - 1)

const progressPercent = computed(() => {
    if (!trainList.value.length) return 0
    return Math.round(((currentIndex.value + (item.value?.status === 'done' ? 1 : 0)) / trainList.value.length) * 100)
})

const stats = computed(() => {
    let correct = 0
    let wrong = 0
    trainList.value.forEach(t => {
        if (t.status === 'correct') correct++
        else if (t.status === 'wrong') wrong++
    })
    return { correct, wrong }
})

const accuracyPercent = computed(() => {
    if (!trainList.value.length) return 0
    return Math.round((stats.value.correct / trainList.value.length) * 100)
})

// 构造单个训练项
function makeTrainingItem(q) {
    return {
        q: { ...q },
        resolved: { ...resolvedOf(q) },
        answer: '',
        status: 'answering',
        masteredDone: false
    }
}

function startTraining() {
    if (!trainableQuestions.value.length) return
    trainList.value = trainableQuestions.value.map(q => makeTrainingItem(q))
    currentIndex.value = 0
    phase.value = 'training'
}

// 答案比较（与文章页自动批阅一致：忽略换行符差异后全等）
function normalizeAnswer(text) {
    return String(text ?? '').replace(/\r\n/g, '\n').trim()
}

async function submitAnswer() {
    const it = item.value
    if (!it?.answer.trim()) return

    const { autoGrade, correctAnswer } = it.resolved

    if (autoGrade && correctAnswer) {
        // 文章已开启自动批阅 → 自动评判对错
        const isCorrect = normalizeAnswer(it.answer) === normalizeAnswer(correctAnswer)
        it.autoJudged = true
        if (isCorrect) {
            it.status = 'correct'
        } else {
            // 做错 → 错题次数 + 1 累积
            try {
                await wrongQuestionsStore.updateQuestion(it.q.id, {
                    wrong_count: (it.q.wrong_count || 0) + 1
                })
                it.q.wrong_count = (it.q.wrong_count || 0) + 1
                it.status = 'wrong'
            } catch (e) {
                showToast(e.message || '更新失败', 'error')
                return
            }
        }
    } else {
        // 未开启自动批阅 → 显示答案，由用户自行评判
        it.autoJudged = false
        it.status = 'comparing'
    }
    scheduleFormulaRender()
}

// 自我判定：correct / wrong
async function judge(isCorrect) {
    const it = item.value
    if (!it || it.status !== 'comparing') return

    if (isCorrect) {
        it.status = 'correct'
    } else {
        // 做错 → 错题次数 + 1 累积
        try {
            await wrongQuestionsStore.updateQuestion(it.q.id, {
                wrong_count: (it.q.wrong_count || 0) + 1
            })
            it.q.wrong_count = (it.q.wrong_count || 0) + 1
            it.status = 'wrong'
        } catch (e) {
            showToast(e.message || '更新失败', 'error')
            return
        }
    }
    scheduleFormulaRender()
}

// 标记掌握
async function markMastered() {
    const it = item.value
    if (!it) return
    try {
        await wrongQuestionsStore.toggleMastered(it.q.id, true)
        it.q.mastered = true
        it.masteredDone = true
        showToast('已标记为掌握', 'success')
    } catch (e) {
        showToast(e.message || '操作失败', 'error')
    }
}

function nextQuestion() {
    if (currentIndex.value < trainList.value.length - 1) {
        currentIndex.value++
        scheduleFormulaRender()
    }
}

// 上一题 / 下一题（保留当前作答与判定状态，不回答也能切换）
function goPrev() {
    if (currentIndex.value > 0) {
        currentIndex.value--
        scheduleFormulaRender()
    }
}

function goNext() {
    if (currentIndex.value < trainList.value.length - 1) {
        currentIndex.value++
        scheduleFormulaRender()
    }
}

function finishTraining() {
    phase.value = 'done'
}

function restart() {
    // 单题重做模式：再练一次 = 重新做同一题
    if (redoQuestionId.value) {
        const q = wrongQuestionsStore.questions.find(x => String(x.id) === String(redoQuestionId.value))
        if (q) {
            trainList.value = [makeTrainingItem(q)]
            currentIndex.value = 0
            phase.value = 'training'
            return
        }
    }
    phase.value = 'setup'
    scope.value = 'unmastered'
    subjectFilter.value = ''
    trainList.value = []
}

// 退出训练：单题重做模式返回错题本，普通模式回到设置页
function quitTraining() {
    if (redoQuestionId.value) {
        router.push('/wrong-questions')
    } else {
        phase.value = 'setup'
    }
}

// ========== 完成页庆祝纸屑（朱红 / 钴蓝 / 黄绿 / 奶油黄 / 天蓝 / 粉） ==========
const confettiPieces = 26
const confettiColors = ['#ed4b2a', '#1d4ed8', '#c9ef4a', '#ffc53d', '#7cc5f0', '#ff9ebb']

function confettiStyle(i) {
    const left = (i * 37) % 100
    const drift = ((i * 29) % 120) - 60
    return {
        left: `${left}%`,
        background: confettiColors[i % confettiColors.length],
        animationDelay: `${(i % 9) * 0.13}s`,
        animationDuration: `${2.2 + (i % 5) * 0.32}s`,
        '--confetti-x': `${drift}px`
    }
}

// ========== 键盘快捷键（不覆盖输入框行为） ==========
function handleKeydown(event) {
    if (phase.value !== 'training') return
    const target = event.target
    const tag = (target?.tagName || '').toLowerCase()
    const typing = tag === 'textarea' || tag === 'input' || tag === 'select' || Boolean(target?.isContentEditable)

    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
        event.preventDefault()
        if (item.value?.status === 'answering') submitAnswer()
        return
    }
    if (typing) return

    if (event.altKey && event.key === 'ArrowLeft') {
        event.preventDefault()
        goPrev()
    } else if (event.altKey && event.key === 'ArrowRight') {
        event.preventDefault()
        goNext()
    } else if (event.key === '1' && item.value?.status === 'comparing') {
        event.preventDefault()
        judge(true)
    } else if (event.key === '2' && item.value?.status === 'comparing') {
        event.preventDefault()
        judge(false)
    }
}

// ========== 数据加载 ==========
const booted = ref(false)

async function loadData() {
    try {
        await blogStore.loadArticleData()
        const studentId = wrongQuestionsStore.getStudentId(authStore.currentUser)
        await wrongQuestionsStore.fetchQuestions(studentId)
        await resolveQuestions(wrongQuestionsStore.questions)
    } finally {
        booted.value = true
    }
}

// 错题本「重做该题」进入：加载完成后直接开始单题训练（题目不存在则留在设置页）
function startRedoIfRequested() {
    const qid = redoQuestionId.value
    if (!qid) return
    const q = wrongQuestionsStore.questions.find(x => String(x.id) === String(qid))
    if (!q) return
    trainList.value = [makeTrainingItem(q)]
    currentIndex.value = 0
    phase.value = 'training'
}

// 公式渲染（防竞态）：
// renderMath 会命令式改写 DOM，与 Vue 的 v-if/v-html patch 冲突。
// 方案：只对题干容器渲染（题干带 :key 整块重建，不在状态切换时 patch），
//       渲染时机延后到 Vue patch 完成后，且跳过过期的渲染请求。
let renderToken = 0
async function scheduleFormulaRender() {
    const token = ++renderToken

    // 等 Vue 完成本次 patch（nextTick 后再加一帧，确保 DOM 稳定）
    await nextTick()
    await new Promise(resolve => requestAnimationFrame(() => resolve()))
    if (token !== renderToken) return // 期间又触发渲染，跳过本次

    const card = trainingCard.value
    if (!card) return

    try {
        // 只渲染题干区（不触碰状态区，避免 patch 冲突）
        const stem = card.querySelector('.wt-question-text')
        if (stem) {
            await renderMath(stem)
            // 渲染完成后若元素已被 Vue 移除（切题导致 v-html 重建），无需处理
        }
    } catch (e) {
        console.error('公式渲染失败:', e)
    }
}

// ========== 生命周期 ==========
onMounted(async () => {
    window.addEventListener('keydown', handleKeydown)
    if (authStore.isLoggedIn) {
        await loadData()
        startRedoIfRequested()
    }
})

onBeforeUnmount(() => {
    window.removeEventListener('keydown', handleKeydown)
})

watch(() => authStore.isLoggedIn, async (loggedIn) => {
    if (loggedIn) await loadData()
})

watch([trainList, currentIndex], () => {
    scheduleFormulaRender()
})
</script>

<style scoped>
/* ==========================================================================
   Riso Campus · 训练舱
   规则：奶油纸底 / 纸白试卷 / 1.5px 墨线 / 3~5px 错位硬阴影 / 平涂印刷色。
   不使用毛玻璃、发光、深色底与紫青渐变。
   ========================================================================== */

/* ========== 页面骨架 ========== */
.wt {
    display: flex;
    flex-direction: column;
}

.wt-head {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.wt-head .text-gradient {
    padding: 0 0.06em;
}

.wt-shell {
    width: 100%;
    max-width: 960px;
    margin: 0 auto;
    display: flex;
    flex-direction: column;
    gap: 24px;
}

/* ========== 未登录 ========== */
.wt-guest {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
    padding: 64px 24px;
    text-align: center;
    margin-top: 6vh;
}

.wt-guest__title {
    font-size: 1.4rem;
}

/* ========== 设置区 ========== */
.wt-setup {
    display: grid;
    grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr);
    gap: 20px;
    align-items: start;
    width: 100%;
    max-width: 1120px;
    margin: 0 auto;
}

.wt-setup__panel {
    display: flex;
    flex-direction: column;
    gap: 24px;
}

.wt-setup__group {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.wt-scope {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
}

.wt-scope__btn {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px;
    border-radius: var(--r-md);
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
    color: var(--ink);
    text-align: left;
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out),
        background var(--dur-fast);
}

.wt-scope__btn:hover {
    transform: translate(-2px, -2px);
    box-shadow: 5px 5px 0 var(--ink);
}

.wt-scope__btn.is-active {
    background: var(--lime);
    transform: translate(-2px, -2px);
    box-shadow: 5px 5px 0 var(--ink);
}

.wt-scope__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 10px;
    border: 1.5px solid var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
    flex-shrink: 0;
    font-size: 1rem;
}

.wt-scope__icon.is-coral {
    background: var(--vermilion);
    color: var(--paper-1);
}

.wt-scope__icon.is-cyan {
    background: var(--sky);
    color: var(--ink);
}

.wt-scope__text {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
}

.wt-scope__text strong {
    font-size: 0.92rem;
    color: var(--ink);
}

.wt-scope__text small {
    font-size: 0.74rem;
    color: var(--text-3);
    font-family: var(--font-mono);
}

.wt-launch {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-top: 20px;
    border-top: 1.5px dashed var(--line-strong);
}

.wt-preview {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.wt-preview__num {
    font-family: var(--font-display);
    font-size: clamp(2.4rem, 5vw, 3.2rem);
    font-weight: 800;
    line-height: 1.05;
    letter-spacing: -0.04em;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.wt-preview__label {
    color: var(--text-2);
    font-size: 0.88rem;
    font-weight: 700;
}

.wt-preview__unit {
    color: var(--text-3);
    font-size: 0.78rem;
    font-family: var(--font-mono);
}

.wt-setup__hint,
.wt-setup__empty {
    text-align: center;
}

.wt-setup__hint {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
}

.wt-setup__hint .spinner {
    width: 14px;
    height: 14px;
    flex-shrink: 0;
}

.wt-setup__empty {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
}

/* 训练说明（便签） */
.wt-brief {
    display: flex;
    flex-direction: column;
    gap: 16px;
    position: sticky;
    top: calc(var(--header-h) + 16px);
}

.wt-brief__list {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.wt-brief__list li {
    display: flex;
    gap: 12px;
    align-items: flex-start;
}

.wt-brief__idx {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 30px;
    height: 30px;
    border-radius: 8px;
    font-size: 0.72rem;
    font-weight: 700;
    color: var(--ink);
    background: var(--paper-1);
    border: 1.5px solid var(--ink);
    box-shadow: 2px 2px 0 var(--ink);
}

.wt-brief__list strong {
    display: block;
    font-size: 0.88rem;
    color: var(--ink);
}

.wt-brief__list p {
    margin-top: 2px;
    color: var(--text-3);
    font-size: 0.78rem;
    line-height: 1.55;
}

.wt-brief__stats {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
}

.wt-brief__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 12px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    box-shadow: 2px 2px 0 var(--ink);
}

.wt-brief__stat.is-paper {
    background: var(--paper-1);
}

.wt-brief__stat.is-sky {
    background: var(--sky);
}

.wt-brief__num {
    font-family: var(--font-display);
    font-size: 1.5rem;
    font-weight: 800;
    color: var(--ink);
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
}

.wt-brief__cap {
    color: var(--text-2);
    font-size: 0.72rem;
    font-weight: 600;
}

/* ========== HUD（顶部墨线进度） ========== */
.wt-hud {
    display: flex;
    flex-direction: column;
    gap: 12px;
    position: sticky;
    top: calc(var(--header-h) + 10px);
    z-index: 6;
}

.wt-hud__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
}

.wt-hud__title {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.wt-hud__title strong {
    font-family: var(--font-display);
    font-size: 1.05rem;
    font-weight: 800;
    color: var(--ink);
    letter-spacing: -0.01em;
}

.wt-hud__counters {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.wt-chip-wrong {
    background: var(--vermilion);
    color: var(--paper-1);
}

.wt-hud__progress {
    height: 12px;
}

.wt-hud__dots {
    display: flex;
    gap: 5px;
    flex-wrap: wrap;
}

/* 进度点 = 小印刷色块 */
.wt-dot {
    width: 12px;
    height: 12px;
    border-radius: 3px;
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
    transition: background var(--dur-fast);
}

.wt-dot.is-current {
    background: var(--ink);
}

.wt-dot.is-correct {
    background: var(--lime);
}

.wt-dot.is-wrong {
    background: var(--vermilion);
}

/* ========== 题卡（试卷） ========== */
.wt-card {
    padding: 24px 26px;
    position: relative;
}

.wt-card__head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.wt-card__head .tag {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--text-2);
    font-weight: 700;
}

.wt-wrong-times {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    margin-left: auto;
    padding: 2px 9px;
    border: 1.5px solid var(--vermilion);
    border-radius: 6px;
    background: var(--paper-1);
    color: var(--vermilion);
    font-family: var(--font-mono);
    font-size: 0.72rem;
    font-weight: 600;
    white-space: nowrap;
    transform: rotate(-1.5deg);
}

/* 题干 = 试卷题目区（左侧红边） */
.wt-question-text {
    margin-top: 16px;
    padding: 12px 16px;
    border-left: 3px solid var(--vermilion);
    background: var(--paper-0);
    border-radius: 0 var(--r-xs) var(--r-xs) 0;
    color: var(--ink);
    font-size: 1.06rem;
    line-height: 1.82;
    word-break: break-word;
}

/* 题干内容为 v-html 注入，须用 :deep() 才能命中 */
.wt-question-text :deep(img) {
    max-width: 100%;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-xs);
    margin: 8px 0;
    background: var(--paper-1);
}

.wt-question-text :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    font-size: 0.94em;
}

.wt-question-text :deep(th),
.wt-question-text :deep(td) {
    border: 1px solid var(--ink);
    padding: 6px 12px;
    text-align: left;
    vertical-align: middle;
}

.wt-question-text :deep(th) {
    background: var(--butter);
    font-weight: 800;
}

/* 作答 */
.wt-answer {
    margin-top: 20px;
}

.wt-answer__input {
    min-height: 108px;
}

.wt-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    flex-wrap: wrap;
    margin-top: 20px;
}

.wt-actions--center {
    justify-content: center;
}

.wt-hint {
    margin-right: auto;
    color: var(--text-3);
    font-size: 0.74rem;
    font-family: var(--font-mono);
    display: inline-flex;
    align-items: center;
    gap: 5px;
}

.wt-actions--center .wt-hint {
    display: none;
}

/* 对照区：我的答案 = 待判；正确答案 = 对（黄绿浅块） */
.wt-compare {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
    margin-top: 20px;
}

.wt-compare__col {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 12px 14px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    box-shadow: 3px 3px 0 var(--ink);
    background: var(--paper-1);
    min-width: 0;
}

.wt-compare__col.is-my {
    border-top: 9px solid var(--sky);
    background: color-mix(in srgb, var(--sky) 20%, var(--paper-1));
}

.wt-compare__col.is-correct {
    border-top: 9px solid var(--lime);
    background: color-mix(in srgb, var(--lime) 26%, var(--paper-1));
}

.wt-compare__label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.76rem;
    font-weight: 800;
    color: var(--ink);
}

.wt-compare__col.is-my .wt-compare__label i {
    color: var(--cobalt);
}

.wt-compare__col.is-correct .wt-compare__label i {
    color: var(--green);
}

.wt-compare__text {
    color: var(--ink);
    font-size: 0.95rem;
    line-height: 1.72;
    word-break: break-word;
}

/* 自判提示（奶油黄便签） */
.wt-judge-tip {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 16px;
    padding: 10px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    box-shadow: 2px 2px 0 var(--ink);
    background: var(--butter);
    color: var(--ink);
    font-size: 0.82rem;
    font-weight: 600;
}

/* 自判按钮 */
.wt-btn-correct,
.wt-btn-wrong {
    border: 1.5px solid var(--ink);
    box-shadow: 4px 4px 0 var(--ink);
}

.wt-btn-correct {
    background: var(--lime);
    color: var(--ink);
}

.wt-btn-correct:hover {
    background: #d7f76b;
    transform: translate(-1px, -1px);
    box-shadow: 5px 5px 0 var(--ink);
}

.wt-btn-wrong {
    background: var(--vermilion);
    color: var(--paper-1);
}

.wt-btn-wrong:hover {
    background: #ff5b37;
    transform: translate(-1px, -1px);
    box-shadow: 5px 5px 0 var(--ink);
}

/* 结果区（彩色印章） */
.wt-result {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
    margin-top: 20px;
    padding: 16px 18px;
    border: 2px solid var(--ink);
    border-radius: var(--r-md);
    box-shadow: 4px 4px 0 var(--ink);
}

.wt-result.is-correct {
    background: color-mix(in srgb, var(--lime) 24%, var(--paper-1));
}

.wt-result.is-wrong {
    background: color-mix(in srgb, var(--vermilion) 10%, var(--paper-1));
}

.wt-result__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 52px;
    height: 52px;
    border-radius: 10px;
    border: 2px solid var(--ink);
    font-size: 1.5rem;
    flex-shrink: 0;
    transform: rotate(-4deg);
    background: var(--paper-1);
}

.wt-result.is-correct .wt-result__icon {
    color: var(--green);
    box-shadow: inset 0 0 0 3px var(--lime), 2px 2px 0 var(--ink);
}

.wt-result.is-wrong .wt-result__icon {
    color: var(--vermilion);
    box-shadow: inset 0 0 0 3px #ffd2c7, 2px 2px 0 var(--ink);
}

.wt-result__body {
    display: flex;
    flex-direction: column;
    gap: 2px;
    flex: 1;
    min-width: 160px;
}

.wt-result__title {
    font-size: 1rem;
    color: var(--ink);
    display: inline-flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.wt-result__title .tag {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--text-2);
    font-weight: 700;
}

.wt-result__sub {
    color: var(--text-2);
    font-size: 0.8rem;
}

/* 上下题导航 */
.wt-nav {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
}

.wt-nav__hint {
    color: var(--text-3);
    font-size: 0.76rem;
    font-family: var(--font-mono);
}

.wt-quit-row {
    display: flex;
    justify-content: center;
    padding-bottom: 8px;
}

/* ========== 完成区 ========== */
.wt-done {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 22px;
    padding: 12px 0 20px;
    width: 100%;
}

/* 庆祝纸屑 */
.wt-done__confetti {
    position: fixed;
    inset: 0;
    z-index: 3;
    pointer-events: none;
    overflow: hidden;
}

.wt-confetti {
    position: absolute;
    top: -12vh;
    width: 8px;
    height: 14px;
    border-radius: 2px;
    border: 1.5px solid var(--ink);
    opacity: 0;
    animation-name: confetti-fall;
    animation-timing-function: var(--ease-out);
    animation-fill-mode: both;
}

/* 印刷错位色块 */
.wt-done__shape {
    position: absolute;
    pointer-events: none;
    border: 1.5px solid var(--ink);
    opacity: 0.9;
}

.wt-done__shape--circle {
    top: -18px;
    left: -10px;
    width: 220px;
    height: 220px;
    border-radius: 50%;
    background: var(--butter);
    mix-blend-mode: multiply;
    opacity: 0.55;
}

.wt-done__shape--rect {
    top: 60px;
    right: -6px;
    width: 160px;
    height: 120px;
    background: var(--sky);
    transform: rotate(9deg);
    mix-blend-mode: multiply;
    opacity: 0.5;
}

.wt-done__head {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    text-align: center;
    animation: pop-in 0.45s var(--ease-spring) both;
}

.wt-done__panel {
    position: relative;
    z-index: 1;
    width: 100%;
    max-width: 760px;
    padding: 28px;
    display: flex;
    flex-direction: column;
    gap: 18px;
    animation: fade-up 0.45s var(--ease-out) 0.08s both;
}

.wt-done__stats {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
}

.wt-done__stat {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 18px 8px 16px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    box-shadow: 3px 3px 0 var(--ink);
    background: var(--paper-1);
}

.wt-done__stat--total {
    background: var(--butter);
}

.wt-done__stat--correct {
    background: var(--lime);
}

.wt-done__stat--wrong {
    background: var(--vermilion);
}

.wt-done__stat--rate {
    background: var(--sky);
}

.wt-done__num {
    font-family: var(--font-display);
    font-size: clamp(1.9rem, 4.4vw, 2.8rem);
    font-weight: 800;
    line-height: 1;
    letter-spacing: -0.05em;
    font-variant-numeric: tabular-nums;
    color: var(--ink);
}

.wt-done__stat--wrong .wt-done__num {
    color: var(--paper-1);
}

.wt-done__stat--wrong .wt-done__label {
    color: #ffe1d8;
}

.wt-done__label {
    color: var(--text-2);
    font-size: 0.76rem;
    font-weight: 700;
}

.wt-done__progress {
    height: 12px;
}

.wt-done__tip {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 14px 16px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    box-shadow: 2px 2px 0 var(--ink);
    font-size: 0.88rem;
    line-height: 1.65;
    color: var(--ink);
}

.wt-done__tip.is-correct {
    background: var(--lime);
}

.wt-done__tip.is-wrong {
    background: var(--butter);
}

.wt-done__tip > i {
    margin-top: 3px;
    color: var(--ink);
}

.wt-done__actions {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
}

.wt-done__actions .btn {
    flex: 1;
    min-width: 180px;
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
    .wt-setup {
        grid-template-columns: minmax(0, 1fr);
        max-width: 760px;
    }

    .wt-brief {
        position: static;
    }
}

@media (max-width: 760px) {
    .wt-shell {
        gap: 14px;
    }

    .wt-card {
        padding: 18px 16px;
    }

    .wt-compare {
        grid-template-columns: minmax(0, 1fr);
    }

    .wt-hint {
        display: none;
    }

    .wt-hud {
        position: static;
    }

    .wt-hud__counters .btn {
        margin-left: auto;
    }

    .wt-nav {
        flex-wrap: wrap;
        justify-content: space-between;
    }

    .wt-nav__hint {
        order: 3;
        width: 100%;
        text-align: center;
    }

    .wt-done__panel {
        padding: 20px 16px;
    }

    .wt-done__stats {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .wt-scope {
        grid-template-columns: minmax(0, 1fr);
    }

    .wt-actions .btn {
        flex: 1;
    }

    .wt-done__actions .btn {
        min-width: 0;
    }

    .wt-done__shape--circle {
        width: 150px;
        height: 150px;
        left: -40px;
    }

    .wt-done__shape--rect {
        width: 110px;
        height: 84px;
        right: -34px;
    }
}

@media (max-width: 420px) {
    .wt-nav__hint {
        display: none;
    }
}
</style>
