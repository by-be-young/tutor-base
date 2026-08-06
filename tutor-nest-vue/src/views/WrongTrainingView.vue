<!--
  WrongTrainingView.vue - 错题训练
  ----------------------------------------------------------------------------
  功能说明：
    1. 独立页面，对错题本中的题目进行重做训练
    2. 训练流程：设置（学科/范围）→ 逐题作答 → 对照正确答案 → 自我判定
    3. 做错的题 wrong_count + 1，累积到总做错次数（写回错题本）
    4. 做对的题可标记为「已掌握」
-->
<template>
    <div class="wt-container">
        <div v-if="!authStore.isLoggedIn" class="empty-tip">
            请先 <router-link to="/" class="tip-link">登录</router-link> 后使用错题训练。
        </div>

        <template v-else>
            <!-- ========== 设置区 ========== -->
            <div v-if="phase === 'setup'" class="wt-setup">
                <div class="wq-header">
                    <h1 class="wq-title">错题训练</h1>
                    <p class="wq-subtitle">
                        温故而知新 —— 重做错题本中的题目，直到真正掌握。
                    </p>
                </div>

                <div class="wt-setup-card">
                    <div class="form-row">
                        <label class="form-label">学科</label>
                        <select v-model="subjectFilter" class="form-select">
                            <option value="">全部学科</option>
                            <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
                        </select>
                    </div>

                    <div class="form-row">
                        <label class="form-label">训练范围</label>
                        <div class="wt-scope-btns">
                            <button class="wt-scope-btn" :class="{ 'is-active': scope === 'unmastered' }"
                                @click="scope = 'unmastered'">未掌握（{{ unmasteredCount }} 题）</button>
                            <button class="wt-scope-btn" :class="{ 'is-active': scope === 'all' }"
                                @click="scope = 'all'">全部（{{ allCount }} 题）</button>
                        </div>
                    </div>

                    <div class="wt-setup-info">
                        <i class="fas fa-info-circle"></i>
                        本次将训练 {{ trainableQuestions.length }} 道题。作答后对照正确答案，自行判定对错；
                        判为错误的题会累计到该题的做错次数。
                    </div>

                    <button class="wt-start-btn" :disabled="trainableQuestions.length === 0" @click="startTraining">
                        <i class="fas fa-play"></i> 开始训练
                    </button>
                    <p v-if="trainableQuestions.length === 0" class="wt-empty-hint">
                        当前条件下没有可训练的题目，先添加或收集一些错题吧。
                    </p>
                </div>
            </div>

            <!-- ========== 训练区 ========== -->
            <div v-else-if="phase === 'training'" class="wt-training">
                <div class="wq-header">
                    <h1 class="wq-title">错题训练</h1>
                    <p class="wq-subtitle">第 {{ currentIndex + 1 }} / {{ trainList.length }} 题</p>
                </div>

                <!-- 进度条 -->
                <div class="wt-progress">
                    <div class="wt-progress-bar">
                        <div class="wt-progress-fill" :style="{ width: progressPercent + '%' }"></div>
                    </div>
                    <div class="wt-progress-stats">
                        <span class="is-correct"><i class="fas fa-check-circle"></i> 对 {{ stats.correct }}</span>
                        <span class="is-wrong"><i class="fas fa-times-circle"></i> 错 {{ stats.wrong }}</span>
                    </div>
                </div>

                <div class="wt-question-card" ref="trainingCard">
                    <!-- 题卡头部 -->
                    <div class="wq-card-head">
                        <div class="wq-subject-badge" :class="subjectBadgeClass(item.resolved.subject)">
                            {{ item.resolved.subject || '未分类' }}
                        </div>
                        <span class="wq-status-pill">第 {{ item.resolved.order ?? item.q.source_question_id }} 题</span>
                        <span class="wq-wrong-count" :title="`已错 ${item.q.wrong_count} 次`">
                            <i class="fas fa-times-circle"></i> {{ item.q.wrong_count }}
                        </span>
                    </div>

                    <!-- 题干 -->
                    <div class="wt-question-text"
                        v-html="item.resolved.questionText ? renderQuestionText(item.resolved.questionText) : '（无题目内容）'"
                        :key="'stem-' + item.q.id">
                    </div>

                    <!-- 作答区 / 对照区 / 结果区：加 key 强制整块重建，避免 KaTeX 节点与 Vue patch 冲突 -->
                    <template v-if="item.status === 'answering'">
                        <textarea v-model="item.answer" class="wt-answer-input" rows="4"
                            placeholder="凭记忆写出你的答案…"></textarea>
                        <div class="wt-btn-row">
                            <button class="wt-btn is-primary" :disabled="!item.answer.trim()" @click="submitAnswer">
                                <i class="fas fa-check"></i> 提交判定
                            </button>
                        </div>
                    </template>

                    <!-- 对照区（未开启自动批阅：显示答案，由用户自行评判） -->
                    <template v-else-if="item.status === 'comparing'">
                        <div class="wt-compare">
                            <div class="wt-compare-col">
                                <div class="wq-detail-label is-my"><i class="fas fa-pen"></i> 我的答案</div>
                                <div class="wq-detail-text" v-html="safeText(item.answer)"></div>
                            </div>
                            <div class="wt-compare-col">
                                <div class="wq-detail-label is-correct"><i class="fas fa-check"></i> 正确答案</div>
                                <div class="wq-detail-text"
                                    v-html="item.resolved.correctAnswer ? safeText(item.resolved.correctAnswer) : '（未设置答案，请自行判断）'">
                                </div>
                            </div>
                        </div>
                        <div class="wt-self-judge-tip">
                            <i class="fas fa-user-check"></i> 请对照答案，自行判断是否正确
                        </div>
                        <div class="wt-btn-row">
                            <button class="wt-btn is-correct" @click="judge(true)">
                                <i class="fas fa-thumbs-up"></i> 做对了
                            </button>
                            <button class="wt-btn is-wrong" @click="judge(false)">
                                <i class="fas fa-thumbs-down"></i> 还做错了
                            </button>
                        </div>
                    </template>

                    <!-- 结果区 -->
                    <template v-else>
                        <div class="wt-result" :class="item.status === 'correct' ? 'is-correct' : 'is-wrong'">
                            <i :class="item.status === 'correct' ? 'fas fa-check-circle' : 'fas fa-times-circle'"></i>
                            <div class="wt-result-text">
                                <div class="wt-result-title">
                                    {{ item.status === 'correct' ? '做对了！' : '又错了，再接再厉' }}
                                    <span v-if="item.autoJudged" class="wt-result-judged">自动评判</span>
                                </div>
                                <div v-if="item.status === 'wrong'" class="wt-result-sub">
                                    已累计一次错误（做错次数 {{ item.q.wrong_count }} 次）
                                </div>
                                <div v-else class="wt-result-sub">
                                    本题可标记为已掌握
                                </div>
                            </div>
                            <button v-if="item.status === 'correct' && !item.masteredDone" class="wt-btn is-master"
                                @click="markMastered">
                                <i class="fas fa-check-circle"></i> 标记掌握
                            </button>
                            <button v-if="item.masteredDone" class="wt-btn is-master-done" disabled>
                                <i class="fas fa-star"></i> 已掌握
                            </button>
                        </div>
                        <div class="wt-btn-row">
                            <button v-if="currentIndex < trainList.length - 1" class="wt-btn is-primary"
                                @click="nextQuestion">
                                下一题 <i class="fas fa-arrow-right"></i>
                            </button>
                            <button v-else class="wt-btn is-primary" @click="finishTraining">
                                完成训练 <i class="fas fa-flag-checkered"></i>
                            </button>
                        </div>
                    </template>
                </div>

                <!-- 上/下题导航（不回答也能切换） -->
                <div class="wt-nav">
                    <button class="wt-nav-btn" :disabled="!hasPrev" @click="goPrev">
                        <i class="fas fa-arrow-left"></i> 上一题
                    </button>
                    <span class="wt-nav-hint">无需作答也可切换题目</span>
                    <button class="wt-nav-btn" :disabled="!hasNext" @click="goNext">
                        下一题 <i class="fas fa-arrow-right"></i>
                    </button>
                </div>

                <!-- 退出训练 -->
                <button class="wt-quit" @click="phase = 'setup'">
                    <i class="fas fa-times"></i> 退出训练
                </button>
            </div>

            <!-- ========== 完成区 ========== -->
            <div v-else class="wt-done">
                <div class="wq-header">
                    <h1 class="wq-title">训练完成！</h1>
                    <p class="wq-subtitle">本次共训练 {{ trainList.length }} 道题</p>
                </div>

                <div class="wt-done-card">
                    <div class="wt-done-stats">
                        <div class="wt-done-stat">
                            <div class="wt-done-number is-total">{{ trainList.length }}</div>
                            <div class="wt-done-label">总题数</div>
                        </div>
                        <div class="wt-done-stat">
                            <div class="wt-done-number is-correct">{{ stats.correct }}</div>
                            <div class="wt-done-label">做对</div>
                        </div>
                        <div class="wt-done-stat">
                            <div class="wt-done-number is-wrong">{{ stats.wrong }}</div>
                            <div class="wt-done-label">做错</div>
                        </div>
                    </div>

                    <div v-if="stats.wrong > 0" class="wt-done-tip is-wrong">
                        <i class="fas fa-exclamation-triangle"></i>
                        有 {{ stats.wrong }} 道题仍未掌握，做错次数已累积到错题本，建议继续巩固。
                    </div>
                    <div v-else class="wt-done-tip is-correct">
                        <i class="fas fa-trophy"></i>
                        全部做对，太棒了！记得把做对的题标记为已掌握。
                    </div>

                    <div class="wt-btn-row">
                        <button class="wt-btn is-primary" @click="restart">
                            <i class="fas fa-redo"></i> 再练一次
                        </button>
                        <router-link to="/wrong-questions" class="wt-btn is-link">
                            <i class="fas fa-book-medical"></i> 返回错题本
                        </router-link>
                    </div>
                </div>
            </div>
        </template>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { useBlogStore } from '@/stores/blogStore'
import { useWrongQuestionsStore } from '@/stores/wrongQuestionsStore'
import { useWrongQuestionsResolve } from '@/composables/useWrongQuestionsResolve'
import { useKatex } from '@/composables/useKatex'
import { useImageEmbed } from '@/composables/useImageEmbed'

const authStore = useAuthStore()
const blogStore = useBlogStore()
const wrongQuestionsStore = useWrongQuestionsStore()
const { resolvedOf, resolveQuestions } = useWrongQuestionsResolve()
const { renderMath } = useKatex()

// 图片嵌入解析（与文章页一致：![[图片]] → <img>）
const { processMarkdown, setBasePath } = useImageEmbed()
setBasePath('blogs/图片/')

// ========== 渲染工具 ==========
function renderQuestionText(text) {
    return processMarkdown(String(text ?? '')).replace(/\n/g, '<br>')
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

// ========== 阶段与设置 ==========
const phase = ref('setup')   // setup | training | done
const subjectFilter = ref('')
const scope = ref('unmastered')

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

function startTraining() {
    if (!trainableQuestions.value.length) return
    trainList.value = trainableQuestions.value.map(q => ({
        q: { ...q },
        resolved: { ...resolvedOf(q) },
        answer: '',
        status: 'answering',
        masteredDone: false
    }))
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
    phase.value = 'setup'
    scope.value = 'unmastered'
    subjectFilter.value = ''
    trainList.value = []
}

// ========== 数据加载 ==========
async function loadData() {
    await blogStore.loadBlogData()
    const studentId = wrongQuestionsStore.getStudentId(authStore.currentUser)
    await wrongQuestionsStore.fetchQuestions(studentId)
    await resolveQuestions(wrongQuestionsStore.questions)
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

// ========== Toast ==========
let toastTimer = null
function showToast(message, type = 'info', duration = 3000) {
    const existing = document.querySelector('.custom-toast')
    if (existing) {
        existing.remove()
        if (toastTimer) {
            clearTimeout(toastTimer)
            toastTimer = null
        }
    }

    const toast = document.createElement('div')
    toast.className = `custom-toast toast-${type}`
    toast.textContent = message
    document.body.appendChild(toast)

    requestAnimationFrame(() => toast.classList.add('toast-visible'))

    toastTimer = setTimeout(() => {
        toast.classList.remove('toast-visible')
        setTimeout(() => {
            if (toast.parentNode) toast.parentNode.removeChild(toast)
            toastTimer = null
        }, 300)
    }, duration)
}

// ========== 工具 ==========
function subjectBadgeClass(subject) {
    if (subject === '化学') return 'is-chemistry'
    if (subject === '英语') return 'is-english'
    return 'is-other'
}

// ========== 生命周期 ==========
onMounted(async () => {
    if (authStore.isLoggedIn) {
        await loadData()
    }
})

watch(() => authStore.isLoggedIn, async (loggedIn) => {
    if (loggedIn) await loadData()
})

watch([trainList, currentIndex], () => {
    scheduleFormulaRender()
})
</script>

<style scoped>
.wt-container {
    max-width: 1300px;
    min-width: 800px;
    margin: 0 auto;
    padding: 30px 32px 100px;
    width: 100%;
}

.empty-tip {
    text-align: center;
    padding: 60px 20px;
    color: var(--gray);
    background: rgba(255, 255, 255, 0.3);
    border-radius: 24px;
}

.tip-link {
    color: var(--teal-dark);
}

/* ========== 头部（复用错题本样式） ========== */
.wq-header {
    margin-bottom: 24px;
}

.wq-title {
    font-size: 2.2rem;
    font-weight: 700;
    color: #2d4a3a;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 12px;
}

.wq-title::before {
    content: '\f7e6';
    font-family: 'Font Awesome 6 Free';
    font-weight: 900;
    font-size: 1.8rem;
    color: #d9ba4b;
}

.wq-subtitle {
    color: var(--gray);
    font-size: 0.95rem;
}

/* ========== 设置区 ========== */
.wt-setup-card {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(8px);
    border-radius: 24px;
    padding: 28px 32px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow: 0 8px 32px var(--shadow);
    max-width: 560px;
}

.form-row {
    margin-bottom: 18px;
}

.form-label {
    display: block;
    font-size: 0.9rem;
    font-weight: 600;
    color: #345143;
    margin-bottom: 6px;
}

.form-select {
    width: 100%;
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid rgba(120, 170, 155, 0.25);
    background: rgba(255, 255, 255, 0.9);
    color: #345143;
    font-size: 0.95rem;
    outline: none;
    transition: border-color 0.2s, box-shadow 0.2s;
    font-family: inherit;
}

.form-select:focus {
    border-color: rgba(224, 199, 106, 0.8);
    box-shadow: 0 0 0 4px rgba(243, 227, 162, 0.25);
}

.wt-scope-btns {
    display: flex;
    gap: 10px;
}

.wt-scope-btn {
    flex: 1;
    padding: 10px 14px;
    border-radius: 12px;
    border: 1px solid rgba(120, 170, 155, 0.25);
    background: rgba(255, 255, 255, 0.7);
    color: var(--gray);
    font-size: 0.95rem;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.wt-scope-btn:hover {
    background: rgba(255, 255, 255, 0.95);
}

.wt-scope-btn.is-active {
    background: rgba(91, 168, 164, 0.2);
    color: var(--teal-dark);
    border-color: var(--teal-dark);
    font-weight: 600;
}

.wt-setup-info {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    font-size: 0.88rem;
    color: var(--gray);
    line-height: 1.7;
    background: rgba(245, 248, 246, 0.7);
    border-radius: 12px;
    padding: 12px 16px;
    margin-bottom: 20px;
}

.wt-setup-info i {
    margin-top: 3px;
    color: var(--teal-dark);
}

.wt-start-btn {
    width: 100%;
    padding: 14px;
    border-radius: 30px;
    border: none;
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.98), rgba(243, 227, 162, 0.92));
    color: #705d13;
    font-size: 1.1rem;
    font-weight: 600;
    cursor: pointer;
    box-shadow: 0 8px 18px rgba(217, 186, 75, 0.25);
    transition: all 0.25s ease;
    font-family: inherit;
}

.wt-start-btn:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 12px 24px rgba(217, 186, 75, 0.35);
}

.wt-start-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.wt-empty-hint {
    text-align: center;
    color: var(--gray);
    font-size: 0.88rem;
    margin-top: 12px;
}

/* ========== 训练区 ========== */
.wt-progress {
    margin-bottom: 20px;
}

.wt-progress-bar {
    height: 8px;
    border-radius: 999px;
    background: rgba(120, 170, 155, 0.15);
    overflow: hidden;
}

.wt-progress-fill {
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, #7bc8c4, #5ba8a4);
    transition: width 0.4s ease;
}

.wt-progress-stats {
    display: flex;
    gap: 16px;
    margin-top: 8px;
    font-size: 0.85rem;
}

.wt-progress-stats .is-correct {
    color: #2f7b57;
}

.wt-progress-stats .is-wrong {
    color: #b65661;
}

.wt-question-card {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(8px);
    border-radius: 24px;
    padding: 24px 28px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    border-left: 6px solid #d9ba4b;
    box-shadow: 0 8px 32px var(--shadow);
}

.wq-card-head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
}

.wq-subject-badge {
    padding: 2px 14px;
    border-radius: 999px;
    font-size: 0.8rem;
    font-weight: 600;
    color: #2d4a3a;
}

.wq-subject-badge.is-chemistry {
    background: rgba(91, 168, 164, 0.18);
    color: #2f6a66;
}

.wq-subject-badge.is-english {
    background: rgba(143, 207, 184, 0.25);
    color: #2f6b50;
}

.wq-subject-badge.is-other {
    background: rgba(120, 170, 155, 0.12);
    color: #4c6b5b;
}

.wq-status-pill {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 12px;
    border-radius: 999px;
    font-size: 0.78rem;
    font-weight: 600;
    background: rgba(91, 168, 164, 0.15);
    color: #2f6a66;
}

.wq-wrong-count {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 0.78rem;
    color: #a87b19;
    margin-left: auto;
}

.wt-question-text {
    margin-top: 16px;
    font-size: 1.1rem;
    line-height: 1.8;
    color: #2d4a3a;
    word-break: break-word;
}

.wt-question-text img {
    max-width: 100%;
    border-radius: 8px;
    margin: 6px 0;
}

.wt-answer-input {
    width: 100%;
    margin-top: 18px;
    resize: vertical;
    min-height: 90px;
    border-radius: 14px;
    border: 1px solid rgba(120, 170, 155, 0.25);
    background: rgba(255, 255, 255, 0.95);
    padding: 12px 16px;
    font-size: 1rem;
    color: #345143;
    outline: none;
    line-height: 1.6;
    font-family: inherit;
    transition: border-color 0.2s, box-shadow 0.2s;
}

.wt-answer-input:focus {
    border-color: rgba(224, 199, 106, 0.8);
    box-shadow: 0 0 0 4px rgba(243, 227, 162, 0.25);
}

/* 对照区 */
.wt-compare {
    display: flex;
    gap: 16px;
    margin-top: 18px;
}

.wt-compare-col {
    flex: 1;
    min-width: 0;
}

.wq-detail-label {
    font-size: 0.82rem;
    font-weight: 600;
    margin-bottom: 6px;
    display: flex;
    align-items: center;
    gap: 6px;
}

.wq-detail-label.is-my {
    color: #b6862a;
}

.wq-detail-label.is-correct {
    color: #2f7b57;
}

.wq-detail-text {
    font-size: 0.95rem;
    color: #345143;
    background: rgba(245, 248, 246, 0.7);
    border-radius: 12px;
    padding: 10px 14px;
    word-break: break-word;
    line-height: 1.7;
}

/* 按钮行 */
.wt-btn-row {
    display: flex;
    gap: 12px;
    margin-top: 20px;
    flex-wrap: wrap;
}

.wt-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 10px 24px;
    border-radius: 30px;
    border: none;
    font-size: 0.95rem;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.wt-btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
}

.wt-btn.is-primary {
    background: linear-gradient(135deg, rgba(255, 248, 214, 0.98), rgba(243, 227, 162, 0.92));
    color: #705d13;
    box-shadow: 0 6px 16px rgba(217, 186, 75, 0.25);
}

.wt-btn.is-primary:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 10px 22px rgba(217, 186, 75, 0.35);
}

.wt-btn.is-correct {
    background: rgba(205, 237, 221, 0.75);
    color: #2f7b57;
    border: 1px solid rgba(80, 176, 122, 0.25);
}

.wt-btn.is-correct:hover {
    background: rgba(205, 237, 221, 0.95);
}

.wt-btn.is-wrong {
    background: rgba(255, 224, 224, 0.75);
    color: #b65661;
    border: 1px solid rgba(208, 116, 126, 0.25);
}

.wt-btn.is-wrong:hover {
    background: rgba(255, 224, 224, 0.95);
}

.wt-btn.is-master {
    background: rgba(91, 168, 164, 0.2);
    color: var(--teal-dark);
    border: 1px solid rgba(91, 168, 164, 0.35);
}

.wt-btn.is-master-done {
    background: rgba(120, 170, 155, 0.1);
    color: #5a7a6a;
    cursor: default;
}

.wt-btn.is-link {
    background: rgba(120, 170, 155, 0.1);
    color: #4c6b5b;
    text-decoration: none;
}

.wt-btn.is-link:hover {
    background: rgba(120, 170, 155, 0.2);
}

/* 自行评判提示 */
.wt-self-judge-tip {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 14px;
    font-size: 0.85rem;
    color: #7b6420;
    background: rgba(242, 226, 172, 0.35);
    border-radius: 10px;
    padding: 8px 14px;
}

/* 自动评判标记 */
.wt-result-judged {
    font-size: 0.72rem;
    font-weight: 600;
    color: #4c6b5b;
    background: rgba(120, 170, 155, 0.15);
    border-radius: 999px;
    padding: 1px 10px;
    margin-left: 8px;
    vertical-align: middle;
}

/* 结果区 */
.wt-result {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-top: 18px;
    padding: 14px 18px;
    border-radius: 16px;
}

.wt-result.is-correct {
    background: rgba(205, 237, 221, 0.4);
    border: 1px solid rgba(80, 176, 122, 0.2);
    color: #2f7b57;
}

.wt-result.is-wrong {
    background: rgba(255, 224, 224, 0.4);
    border: 1px solid rgba(208, 116, 126, 0.2);
    color: #b65661;
}

.wt-result > i {
    font-size: 1.6rem;
}

.wt-result-text {
    flex: 1;
}

.wt-result-title {
    font-weight: 700;
    font-size: 1.05rem;
}

.wt-result-sub {
    font-size: 0.85rem;
    opacity: 0.85;
    margin-top: 2px;
}

/* 上/下题导航 */
.wt-nav {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-top: 20px;
}

.wt-nav-btn {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 8px 20px;
    border-radius: 30px;
    border: 1px solid rgba(120, 170, 155, 0.2);
    background: rgba(255, 255, 255, 0.5);
    color: #4c6b5b;
    font-size: 0.9rem;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.wt-nav-btn:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.85);
    color: var(--teal-dark);
    border-color: var(--teal-dark);
}

.wt-nav-btn:disabled {
    opacity: 0.4;
    cursor: not-allowed;
}

.wt-nav-hint {
    font-size: 0.8rem;
    color: rgba(90, 122, 106, 0.6);
}

.wt-quit {
    margin-top: 20px;
    padding: 8px 20px;
    border-radius: 30px;
    border: 1px solid transparent;
    background: transparent;
    color: var(--gray);
    font-size: 0.88rem;
    cursor: pointer;
    transition: all 0.2s ease;
    font-family: inherit;
}

.wt-quit:hover {
    background: rgba(213, 117, 135, 0.08);
    border-color: rgba(213, 117, 135, 0.2);
    color: #d57587;
}

/* ========== 完成区 ========== */
.wt-done-card {
    background: rgba(255, 255, 255, 0.55);
    backdrop-filter: blur(8px);
    border-radius: 24px;
    padding: 32px;
    border: 1px solid rgba(255, 255, 255, 0.5);
    box-shadow: 0 8px 32px var(--shadow);
    max-width: 560px;
}

.wt-done-stats {
    display: flex;
    gap: 16px;
    margin-bottom: 20px;
}

.wt-done-stat {
    flex: 1;
    text-align: center;
    background: rgba(245, 248, 246, 0.7);
    border-radius: 16px;
    padding: 16px 8px;
}

.wt-done-number {
    font-size: 2rem;
    font-weight: 700;
    line-height: 1.2;
}

.wt-done-number.is-total {
    color: #2d4a3a;
}

.wt-done-number.is-correct {
    color: #2f7b57;
}

.wt-done-number.is-wrong {
    color: #b65661;
}

.wt-done-label {
    font-size: 0.85rem;
    color: var(--gray);
    margin-top: 4px;
}

.wt-done-tip {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 14px 16px;
    border-radius: 14px;
    font-size: 0.95rem;
    margin-bottom: 20px;
}

.wt-done-tip.is-correct {
    background: rgba(205, 237, 221, 0.4);
    color: #2f7b57;
}

.wt-done-tip.is-wrong {
    background: rgba(255, 224, 224, 0.4);
    color: #b65661;
}

/* Toast（与全局一致） */
:global(.custom-toast) {
    position: fixed;
    top: 30px;
    left: 50%;
    transform: translateX(-50%) translateY(-20px);
    padding: 8px 25px;
    border-radius: 14px;
    background: rgba(255, 255, 255, 0.92);
    backdrop-filter: blur(12px);
    box-shadow: 0 12px 40px rgba(0, 0, 0, 0.18);
    font-size: 1.05rem;
    font-weight: 500;
    color: #2d4a3a;
    z-index: 9999;
    opacity: 0;
    transition: opacity 0.3s ease, transform 0.3s ease;
    border: 1px solid rgba(255, 255, 255, 0.6);
}

:global(.custom-toast.toast-visible) {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
}

:global(.custom-toast.toast-success) {
    border: 4px solid #4caf50;
    color: #1e4a2a;
}

:global(.custom-toast.toast-error) {
    border: 4px solid #ef5350;
    color: #7a2a2a;
}

:global(.custom-toast.toast-info) {
    border: 4px solid #42a5f5;
    color: #1a3a5a;
}

/* ========== 响应式 ========== */
@media (min-width: 1024px) {
    .wt-container {
        padding: 50px 60px 120px;
    }

    .wq-title {
        font-size: 2.6rem;
    }
}

@media (min-width: 641px) and (max-width: 1023px) {
    .wt-container {
        padding: 30px 28px 70px;
        min-width: auto;
    }
}

@media (max-width: 640px) {
    .wt-container {
        padding: 16px 14px 90px;
        min-width: auto;
    }

    .wq-title {
        font-size: 1.6rem;
    }

    .wt-setup-card,
    .wt-done-card {
        padding: 20px;
    }

    .wt-compare {
        flex-direction: column;
        gap: 10px;
    }

    .wt-question-card {
        padding: 18px 16px;
    }

    .wt-scope-btns {
        flex-direction: column;
        gap: 8px;
    }

    .wt-btn {
        flex: 1;
        justify-content: center;
    }
}
</style>
