<!--
  WrongQuestionsView.vue - 错题作战室
  ----------------------------------------------------------------------------
  功能说明：
    1. 展示当前学生的全部错题（手动添加 + 自动收集）
    2. 后端返回当前账户可见的错题和正确答案；页面从静态文章补充学科与题干
       （subject ← articles.json series；题干 ← 文章 markdown 的【题干N】标记）
    3. 统计、筛选（学科/状态/搜索）、排序、掌握标记、编辑、删除
    4. 手动添加：从文章选题（学科 → 文章 → 题号，实时预览题干）
  视觉：Riso Campus「批改作业本」—— 奶油纸底、墨线边框、错位硬阴影、学科色条。
-->
<template>
    <div class="page page--wide wq">
        <!-- 未登录 -->
        <section v-if="!authStore.isLoggedIn" class="panel panel--pad wq-guest">
            <span class="empty__icon"><i class="fas fa-lock"></i></span>
            <h1 class="wq-guest__title">登录后进入错题作战室</h1>
            <p class="empty__text">错题、错因、笔记与掌握进度都会保存在你的账号里。</p>
            <router-link to="/" class="btn btn--primary">
                <i class="fas fa-right-to-bracket"></i> 去登录
            </router-link>
        </section>

        <template v-else>
            <!-- ============ 头部 ============ -->
            <header class="page-head page-head--row">
                <div class="wq-head">
                    <span class="eyebrow">NEST / 错题作战室</span>
                    <h1 class="page-title">错题<span class="text-gradient">作战室</span></h1>
                    <p class="page-sub">把每一道错题拆开、弄懂、逐个击破 —— 直到掌握率逼近 100%。</p>
                </div>
                <div class="wq-head__actions">
                    <button type="button" class="btn wq-only-toggle"
                        :class="{ 'is-on': statusFilter === 'unmastered' }" @click="toggleOnlyUnmastered">
                        <i class="fas fa-crosshairs"></i> 只看未掌握
                    </button>
                    <router-link to="/wrong-training" class="btn btn--primary btn--lg">
                        <i class="fas fa-dumbbell"></i> 开始训练
                    </router-link>
                </div>
            </header>

            <!-- ============ 作战概览 ============ -->
            <section class="wq-hero panel panel--glow" aria-label="错题概览">
                <span class="wq-hero__tape" aria-hidden="true"></span>

                <div class="stat-grid wq-hero__stats">
                    <div class="stat-card stat-card--amber">
                        <div class="stat-value">
                            <CountUp :key="`total-${stats.total}`" :value="stats.total" />
                        </div>
                        <div class="stat-label">总题数</div>
                        <div class="stat-sub">手动 + 自动收集</div>
                    </div>
                    <div class="stat-card stat-card--cyan">
                        <div class="stat-value">
                            <CountUp :key="`pending-${stats.unmastered}`" :value="stats.unmastered" />
                        </div>
                        <div class="stat-label">未掌握</div>
                        <div class="stat-sub">优先攻克</div>
                    </div>
                    <div class="stat-card stat-card--lime">
                        <div class="stat-value">
                            <CountUp :key="`mastered-${stats.mastered}`" :value="stats.mastered" />
                        </div>
                        <div class="stat-label">已掌握</div>
                        <div class="stat-sub">继续保持</div>
                    </div>
                </div>

                <div class="wq-ring">
                    <div class="wq-ring__dial" role="img" :aria-label="`当前错题掌握率 ${masteryPercent}%`">
                        <div class="wq-ring__inner">
                            <span class="wq-ring__num">
                                <CountUp :key="`mastery-${masteryPercent}`" :value="masteryPercent" suffix="%" />
                            </span>
                            <span class="wq-ring__cap">掌握率</span>
                        </div>
                    </div>
                    <div class="wq-ring__side">
                        <span class="chip chip--violet">
                            <i class="fas fa-bullseye"></i>
                            {{ stats.mastered }} / {{ stats.total }} 已掌握
                        </span>
                        <div class="progress" aria-hidden="true">
                            <span class="progress__bar" :style="{ width: masteryPercent + '%' }"></span>
                        </div>
                        <p class="wq-ring__hint">{{ masteryHint }}</p>
                    </div>
                </div>
            </section>

            <!-- ============ 筛选 ============ -->
            <section class="wq-filters panel panel--tight" aria-label="筛选与搜索">
                <div class="wq-filters__row">
                    <div class="seg" role="group" aria-label="掌握状态筛选">
                        <button v-for="tab in statusTabs" :key="tab.value" type="button" class="seg__btn"
                            :class="{ 'is-active': statusFilter === tab.value }" :aria-pressed="statusFilter === tab.value"
                            @click="statusFilter = tab.value">
                            {{ tab.label }}
                            <span class="seg__count">{{ tab.count }}</span>
                        </button>
                    </div>

                    <div class="wq-filters__controls">
                        <select v-model="subjectFilter" class="select wq-select" aria-label="按学科筛选">
                            <option value="">全部学科</option>
                            <option v-for="s in subjectOptions" :key="s" :value="s">{{ s }}</option>
                        </select>

                        <select v-model="sortMode" class="select wq-select" aria-label="排序方式">
                            <option value="recent">最近更新</option>
                            <option value="wrong">错误次数</option>
                        </select>

                        <div class="input-wrap wq-search">
                            <i class="fas fa-magnifying-glass"></i>
                            <input v-model="searchKeyword" type="text" class="input"
                                placeholder="搜索题目、答案、错因、标签..." aria-label="搜索错题" />
                            <button v-if="searchKeyword" type="button" class="input-wrap__action" aria-label="清空搜索"
                                @click="searchKeyword = ''">
                                <i class="fas fa-xmark"></i>
                            </button>
                        </div>
                    </div>
                </div>

                <div class="wq-filters__meta">
                    <span class="wq-result-count">
                        <i class="fas fa-list-check"></i>
                        命中 <strong>{{ filteredQuestions.length }}</strong> / {{ stats.total }} 道
                    </span>
                    <button v-if="hasActiveFilter" type="button" class="btn btn--ghost btn--sm" @click="clearFilters">
                        <i class="fas fa-filter-circle-xmark"></i> 清除筛选
                    </button>
                </div>
            </section>

            <!-- ============ 加载骨架 ============ -->
            <div v-if="!booted" class="wq-list" aria-hidden="true">
                <div v-for="i in 3" :key="`wq-sk-${i}`" class="panel wq-sk-card">
                    <div class="wq-sk-card__head">
                        <span class="skeleton wq-sk wq-sk--chip"></span>
                        <span class="skeleton wq-sk wq-sk--tag"></span>
                        <span class="skeleton wq-sk wq-sk--tag"></span>
                    </div>
                    <span class="skeleton wq-sk wq-sk--line"></span>
                    <span class="skeleton wq-sk wq-sk--line wq-sk--short"></span>
                </div>
            </div>

            <!-- ============ 空状态 ============ -->
            <section v-else-if="filteredQuestions.length === 0" class="wq-empty-wrap">
                <!-- 一道错题都没有 -->
                <div v-if="wrongQuestionsStore.questions.length === 0" class="panel wq-empty">
                    <div class="wq-empty__art">
                        <span class="wq-empty__tape" aria-hidden="true"></span>
                        <img v-if="!emptyArtBroken" class="wq-empty__img" :src="emptyArt" alt="空错题本插画"
                            loading="lazy" @error="emptyArtBroken = true" />
                        <span v-else class="wq-empty__fallback" aria-hidden="true">
                            <i class="fas fa-award"></i>
                        </span>
                    </div>
                    <div class="wq-empty__body">
                        <span class="eyebrow">零错题 · 待解锁</span>
                        <h2 class="wq-empty__title">作战室还空着</h2>
                        <p class="wq-empty__text">
                            在文章答题时点击「加入错题本」，或被批阅为「错误」的题目会自动收集到这里。
                            收录第一道错题，掌握率就从这里开始。
                        </p>
                        <div class="empty__actions">
                            <router-link to="/" class="btn btn--primary">
                                <i class="fas fa-book-open"></i> 去文章练习
                            </router-link>
                            <router-link to="/category" class="btn btn--ghost">
                                <i class="fas fa-layer-group"></i> 浏览资料
                            </router-link>
                        </div>
                    </div>
                </div>

                <!-- 有错题，但筛选为空 -->
                <div v-else class="empty wq-empty-filtered">
                    <span class="empty__icon"><i class="fas fa-filter-circle-xmark"></i></span>
                    <p class="empty__title">当前筛选没有命中错题</p>
                    <p class="empty__text">换个学科、状态或关键词试试，也可以直接清除全部筛选条件。</p>
                    <div class="empty__actions">
                        <button type="button" class="btn btn--soft" @click="clearFilters">
                            <i class="fas fa-rotate-left"></i> 清除筛选
                        </button>
                    </div>
                </div>
            </section>

            <!-- ============ 错题卡片列表 ============ -->
            <div v-else ref="listContainer" class="wq-list">
                <article v-for="q in filteredQuestions" :key="q.id" class="wq-card panel"
                    :class="{ 'is-expanded': expandedId === q.id, 'is-mastered': q.mastered }">
                    <span class="wq-rail" :class="subjectToneClass(resolvedOf(q).subject)" aria-hidden="true"></span>

                    <div class="wq-card__body">
                        <!-- 卡片头部 -->
                        <button type="button" class="wq-card__head" :aria-expanded="expandedId === q.id"
                            :aria-controls="`wq-detail-${q.id}`" @click="toggleExpand(q.id)">
                            <span class="chip wq-subject" :class="subjectChipClass(resolvedOf(q).subject)">
                                <i class="fas fa-bookmark"></i>
                                {{ resolvedOf(q).subject || '未分类' }}
                            </span>
                            <span class="tag" :class="q.is_manual ? 'is-manual' : 'is-auto'">
                                {{ q.is_manual ? '手动' : '自动' }}
                            </span>
                            <span class="wq-state" :class="q.mastered ? 'is-mastered' : 'is-pending'">
                                <i :class="q.mastered ? 'fas fa-circle-check' : 'fas fa-hourglass-half'"></i>
                                {{ q.mastered ? '已掌握' : '未掌握' }}
                            </span>
                            <span class="wq-wrong-times" :title="`共错了 ${q.wrong_count} 次`">
                                <i class="fas fa-rotate-left"></i> 错 {{ q.wrong_count }} 次
                            </span>
                            <span class="wq-chev" aria-hidden="true">
                                <i :class="expandedId === q.id ? 'fas fa-chevron-up' : 'fas fa-chevron-down'"></i>
                            </span>
                        </button>

                        <!-- 题目内容（动态解析，支持图片与数学公式） -->
                        <div class="wq-question" @click="toggleExpand(q.id)" v-html="resolvedOf(q).questionText
                            ? renderQuestionText(resolvedOf(q).questionText)
                            : (resolvedReady ? '（无题目内容）' : '加载中…')"></div>

                        <!-- 标签 -->
                        <div v-if="q.tags && q.tags.length" class="wq-tags">
                            <span v-for="tag in q.tags" :key="tag" class="tag"># {{ tag }}</span>
                        </div>

                        <!-- 来源文章链接 -->
                        <div v-if="q.source_blog_id" class="wq-source">
                            <i class="fas fa-link"></i>
                            <span class="wq-source__text">
                                来自《{{ blogTitleOf(q.source_blog_id) }}》第
                                {{ resolvedOf(q).order ?? q.source_question_id }} 题
                            </span>
                            <router-link :to="`/blog/${q.source_blog_id}`" class="wq-source__jump">
                                跳转原文 <i class="fas fa-arrow-right"></i>
                            </router-link>
                        </div>

                        <!-- 展开详情（支持数学公式） -->
                        <div v-if="expandedId === q.id" :id="`wq-detail-${q.id}`" class="wq-detail">
                            <div class="wq-compare">
                                <div class="wq-compare__col is-my">
                                    <span class="wq-compare__label">
                                        <i class="fas fa-pen"></i> 我的答案（错）
                                    </span>
                                    <div class="wq-compare__text"
                                        v-html="q.my_answer ? safeText(q.my_answer) : '（未填写）'"></div>
                                </div>
                                <div class="wq-compare__col is-correct">
                                    <span class="wq-compare__label">
                                        <i class="fas fa-check"></i> 正确答案（对）
                                    </span>
                                    <div class="wq-compare__text"
                                        v-html="resolvedOf(q).correctAnswer ? safeText(resolvedOf(q).correctAnswer) : '（未填写）'">
                                    </div>
                                </div>
                            </div>

                            <div v-if="q.wrong_reason || q.note" class="wq-notes">
                                <div v-if="q.wrong_reason" class="wq-note is-reason">
                                    <span class="wq-note__label"><i class="fas fa-lightbulb"></i> 错因分析</span>
                                    <div class="wq-note__text" v-html="safeText(q.wrong_reason)"></div>
                                </div>
                                <div v-if="q.note" class="wq-note is-note">
                                    <span class="wq-note__label"><i class="fas fa-note-sticky"></i> 笔记</span>
                                    <div class="wq-note__text" v-html="safeText(q.note)"></div>
                                </div>
                            </div>
                        </div>

                        <!-- 卡片操作栏 -->
                        <div class="wq-card__actions">
                            <span class="wq-date"><i class="fas fa-clock"></i> {{ formatDate(q.updated_at) }}</span>
                            <div class="wq-action-btns">
                                <button type="button" class="btn btn--cyan btn--sm" @click="handleRedo(q)">
                                    <i class="fas fa-rotate-right"></i> 重做该题
                                </button>
                                <button type="button" class="btn btn--sm"
                                    :class="q.mastered ? 'btn--ghost' : 'btn--soft'"
                                    :disabled="busyIds.has(q.id)" @click="handleToggleMastered(q)">
                                    <i :class="q.mastered ? 'fas fa-undo' : 'fas fa-check-circle'"></i>
                                    {{ q.mastered ? '取消掌握' : '标记掌握' }}
                                </button>
                                <button type="button" class="btn btn--ghost btn--sm" @click="openEditModal(q)">
                                    <i class="fas fa-pen-to-square"></i> 编辑
                                </button>
                                <button type="button" class="btn btn--danger btn--sm" @click="requestDelete(q)">
                                    <i class="fas fa-trash-can"></i> 删除
                                </button>
                            </div>
                        </div>
                    </div>
                </article>
            </div>
        </template>

        <!-- ============ 编辑错题模态框 ============ -->
        <div v-if="modalVisible" class="backdrop" @click.self="closeModal">
            <div class="modal wq-modal" role="dialog" aria-modal="true" aria-labelledby="wq-edit-title">
                <div class="modal__head">
                    <h2 id="wq-edit-title" class="modal__title">
                        <i class="fas fa-pen-to-square"></i> 编辑错题
                    </h2>
                    <button type="button" class="modal__close" aria-label="关闭编辑弹窗" @click="closeModal">
                        <i class="fas fa-xmark"></i>
                    </button>
                </div>

                <div class="modal__body" ref="modalBody">
                    <!-- 题干只读预览 -->
                    <div class="field">
                        <label class="field-label">题干（来自文章，不可修改）</label>
                        <div class="wq-preview" v-html="renderQuestionText(editPreviewText)"></div>
                    </div>

                    <div class="field">
                        <label class="field-label">我的答案</label>
                        <textarea v-model="form.my_answer" class="textarea" rows="2"
                            placeholder="你当时的作答"></textarea>
                    </div>

                    <div class="field">
                        <label class="field-label">错因分析</label>
                        <textarea v-model="form.wrong_reason" class="textarea" rows="2"
                            placeholder="为什么会做错？知识点没掌握、审题不清、粗心…"></textarea>
                    </div>

                    <div class="field">
                        <label class="field-label">
                            知识点标签
                            <span class="field-hint">用逗号分隔</span>
                        </label>
                        <input v-model="form.tagsInput" type="text" class="input"
                            placeholder="如：氧化还原, 配平" />
                    </div>

                    <div class="field">
                        <label class="field-label">笔记</label>
                        <textarea v-model="form.note" class="textarea" rows="2"
                            placeholder="易错点提醒、总结、学习心得…"></textarea>
                    </div>
                </div>

                <div class="modal__foot">
                    <button type="button" class="btn btn--ghost" @click="closeModal">取消</button>
                    <button type="button" class="btn btn--primary" :disabled="saving" @click="submitForm">
                        <i class="fas" :class="saving ? 'fa-spinner fa-spin' : 'fa-floppy-disk'"></i>
                        {{ saving ? '保存中...' : '保存' }}
                    </button>
                </div>
            </div>
        </div>

        <!-- ============ 删除确认模态框 ============ -->
        <div v-if="confirmVisible" class="backdrop" @click.self="cancelDelete">
            <div class="modal wq-modal wq-modal--danger" role="dialog" aria-modal="true"
                aria-labelledby="wq-delete-title">
                <div class="modal__head">
                    <h2 id="wq-delete-title" class="modal__title">
                        <i class="fas fa-triangle-exclamation"></i> 删除错题
                    </h2>
                    <button type="button" class="modal__close" aria-label="关闭删除弹窗" @click="cancelDelete">
                        <i class="fas fa-xmark"></i>
                    </button>
                </div>
                <div class="modal__body">
                    <p class="wq-confirm-text">
                        {{ deletingItem?.is_manual
                            ? '确定要删除这道错题吗？删除后不可恢复。'
                            : '这道错题来自自动收集，删除后若再次做错会重新收集。确定删除吗？' }}
                    </p>
                </div>
                <div class="modal__foot">
                    <button type="button" class="btn btn--ghost" @click="cancelDelete">取消</button>
                    <button type="button" class="btn btn--danger" :disabled="deleting" @click="confirmDelete">
                        <i class="fas" :class="deleting ? 'fa-spinner fa-spin' : 'fa-trash-can'"></i>
                        {{ deleting ? '删除中...' : '确认删除' }}
                    </button>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useArticleStore } from '@/stores/blogStore'
import { useWrongQuestionsStore } from '@/stores/wrongQuestionsStore'
import { useWrongQuestionsResolve } from '@/composables/useWrongQuestionsResolve'
import { useKatex } from '@/composables/useKatex'
import { useImageEmbed } from '@/composables/useImageEmbed'
import { renderMarkdownTable } from '@/utils/questionText'
import { showToast } from '@/utils/toast'
import CountUp from '@/components/ui/CountUp.vue'

const router = useRouter()
const authStore = useAuthStore()
const blogStore = useArticleStore()
const wrongQuestionsStore = useWrongQuestionsStore()
const { resolvedMap, resolvedReady, resolvedOf, resolveQuestions } = useWrongQuestionsResolve()
const { renderMath } = useKatex()
const { processMarkdown, setBasePath } = useImageEmbed()
setBasePath('articles/图片/')

// ========== 空状态插画（站内收藏卡，失败时色块兜底） ==========
const emptyArt = `${import.meta.env.BASE_URL}cards/cosmos-r.png`
const emptyArtBroken = ref(false)

// ========== 展示解析（题干/学科/正确答案） ==========
// 正确答案由后端返回；静态文章只用于补充题干和学科。
const listContainer = ref(null)
const modalBody = ref(null)

// ========== 渲染工具 ==========
/** 题干内容渲染：markdown 表格 → <table>，图片嵌入（![[图片]] → <img>），换行转 <br>，$...$ 公式由 KaTeX 渲染 */
function renderQuestionText(text) {
    return processMarkdown(renderMarkdownTable(text)).replace(/\n/g, '<br>')
}

/** 详情文本安全渲染：转义 HTML 防注入，换行转 <br>，$...$ 公式由 KaTeX 渲染 */
function safeText(text) {
    return String(text ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
        .replace(/\n/g, '<br>')
}

// 列表/模态框内容变化后渲染数学公式
function scheduleFormulaRender() {
    nextTick(() => {
        if (listContainer.value) renderMath(listContainer.value)
        if (modalBody.value) renderMath(modalBody.value)
    })
}

// ========== 筛选状态 ==========
const statusFilter = ref('all')
const subjectFilter = ref('')
const sortMode = ref('recent')
const searchKeyword = ref('')
const expandedId = ref(null)
const busyIds = ref(new Set())

const statusTabs = computed(() => [
    { value: 'all', label: '全部', count: stats.value.total },
    { value: 'unmastered', label: '未掌握', count: stats.value.unmastered },
    { value: 'mastered', label: '已掌握', count: stats.value.mastered }
])

const hasActiveFilter = computed(() =>
    statusFilter.value !== 'all' || Boolean(subjectFilter.value) || Boolean(searchKeyword.value.trim()))

// ========== 统计 ==========
const stats = computed(() => {
    const all = wrongQuestionsStore.questions
    const mastered = all.filter(q => q.mastered).length
    return {
        total: all.length,
        unmastered: all.length - mastered,
        mastered
    }
})

const masteryPercent = computed(() => {
    if (!stats.value.total) return 0
    return Math.round((stats.value.mastered / stats.value.total) * 100)
})

const masteryHint = computed(() => {
    if (!stats.value.total) return '收录第一道错题，掌握率就从这里开始。'
    if (!stats.value.unmastered) return '全部掌握，保持手感，去训练舱巩固一遍吧。'
    return `还有 ${stats.value.unmastered} 道错题等待攻克，优先处理未掌握的题。`
})

// ========== 学科选项 ==========
const subjectOptions = computed(() => {
    const set = new Set()
    resolvedMap.value.forEach(rd => {
        if (rd.subject) set.add(rd.subject)
    })
    blogStore.blogData.forEach(b => {
        if (b.series) set.add(b.series)
    })
    return Array.from(set).sort((a, b) => a.localeCompare(b))
})

// ========== 筛选与排序 ==========
const filteredQuestions = computed(() => {
    const keyword = searchKeyword.value.trim().toLowerCase()

    let list = wrongQuestionsStore.questions.filter(q => {
        // 状态筛选
        if (statusFilter.value === 'unmastered' && q.mastered) return false
        if (statusFilter.value === 'mastered' && !q.mastered) return false
        // 学科筛选
        const subject = resolvedOf(q).subject
        if (subjectFilter.value && subject !== subjectFilter.value) return false
        // 关键词搜索
        if (keyword) {
            const haystack = [
                resolvedOf(q).questionText,
                resolvedOf(q).correctAnswer,
                q.my_answer,
                q.wrong_reason,
                q.note,
                (q.tags || []).join(' ')
            ].join(' ').toLowerCase()
            if (!haystack.includes(keyword)) return false
        }
        return true
    })

    // 排序
    if (sortMode.value === 'wrong') {
        list = [...list].sort((a, b) => (b.wrong_count || 0) - (a.wrong_count || 0))
    } else {
        list = [...list].sort((a, b) =>
            new Date(b.updated_at || 0) - new Date(a.updated_at || 0))
    }
    return list
})

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

// ========== 卡片交互 ==========
function toggleExpand(id) {
    expandedId.value = expandedId.value === id ? null : id
}

// 重做该题：跳转到错题训练页，仅训练这一题
function handleRedo(q) {
    router.push(`/wrong-training?question=${q.id}`)
}

function toggleOnlyUnmastered() {
    statusFilter.value = statusFilter.value === 'unmastered' ? 'all' : 'unmastered'
}

function clearFilters() {
    statusFilter.value = 'all'
    subjectFilter.value = ''
    searchKeyword.value = ''
}

// 学科 → 卡片左侧色条（Riso 印刷色）
function subjectToneClass(subject) {
    if (subject === '高中化学') return 'is-chem'
    if (subject === '高中英语') return 'is-english'
    if (subject === '初中英语') return 'is-junior'
    return 'is-other'
}

// 学科 → 徽章配色
function subjectChipClass(subject) {
    if (subject === '高中化学') return 'chip--cyan'
    if (subject === '高中英语') return 'chip--pink'
    if (subject === '初中英语') return 'chip--amber'
    return 'chip--violet'
}

function blogTitleOf(blogId) {
    const blog = blogStore.blogData.find(b => Number(b.id) === Number(blogId))
    return blog?.title || '未知文章'
}

function formatDate(dateStr) {
    if (!dateStr) return ''
    const d = new Date(dateStr)
    if (isNaN(d)) return ''
    const pad = n => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

// ========== 掌握切换 ==========
async function handleToggleMastered(q) {
    if (busyIds.value.has(q.id)) return
    busyIds.value = new Set(busyIds.value).add(q.id)
    try {
        await wrongQuestionsStore.toggleMastered(q.id, !q.mastered)
        showToast(q.mastered ? '已取消掌握' : '已标记掌握，继续保持！', 'success')
    } catch (e) {
        showToast(e.message || '操作失败', 'error')
    } finally {
        const next = new Set(busyIds.value)
        next.delete(q.id)
        busyIds.value = next
    }
}

// ========== 删除 ==========
const confirmVisible = ref(false)
const deletingItem = ref(null)
const deleting = ref(false)

function requestDelete(q) {
    deletingItem.value = q
    confirmVisible.value = true
}

function cancelDelete() {
    if (deleting.value) return
    confirmVisible.value = false
    deletingItem.value = null
}

async function confirmDelete() {
    if (!deletingItem.value || deleting.value) return
    deleting.value = true
    try {
        await wrongQuestionsStore.deleteQuestion(deletingItem.value.id, deletingItem.value.is_manual)
        showToast('已删除', 'success')
        confirmVisible.value = false
        deletingItem.value = null
    } catch (e) {
        showToast(e.message || '删除失败', 'error')
    } finally {
        deleting.value = false
    }
}

// ========== 编辑模态框 ==========
const modalVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)

const form = ref(createEmptyForm())

function createEmptyForm() {
    return {
        my_answer: '',
        wrong_reason: '',
        tagsInput: '',
        note: ''
    }
}

// 当前错题的题干预览（来自文章，只读）
const editPreviewText = computed(() => {
    if (!editingId.value) return ''
    return resolvedOf({ id: editingId.value }).questionText || ''
})

function openEditModal(q) {
    editingId.value = q.id
    form.value = {
        my_answer: q.my_answer || '',
        wrong_reason: q.wrong_reason || '',
        tagsInput: (q.tags || []).join(', '),
        note: q.note || ''
    }
    modalVisible.value = true
}

function closeModal() {
    if (saving.value) return
    modalVisible.value = false
}

async function submitForm() {
    if (saving.value) return

    const payload = {
        my_answer: form.value.my_answer.trim(),
        wrong_reason: form.value.wrong_reason.trim(),
        note: form.value.note.trim(),
        tags: form.value.tagsInput
            .split(/[,，]/)
            .map(t => t.trim())
            .filter(Boolean)
    }

    saving.value = true
    try {
        await wrongQuestionsStore.updateQuestion(editingId.value, payload)
        showToast('修改成功', 'success')
        modalVisible.value = false
        await resolveQuestions(wrongQuestionsStore.questions)
    } catch (e) {
        showToast(e.message || '保存失败，请稍后重试', 'error')
    } finally {
        saving.value = false
    }
}

// ========== 键盘：Esc 关闭弹层 ==========
function handleKeydown(event) {
    if (event.key !== 'Escape') return
    if (confirmVisible.value) cancelDelete()
    else if (modalVisible.value) closeModal()
}

// ========== 生命周期 ==========
onMounted(async () => {
    window.addEventListener('keydown', handleKeydown)
    if (authStore.isLoggedIn) {
        await loadData()
        scheduleFormulaRender()
    }
})

onBeforeUnmount(() => {
    window.removeEventListener('keydown', handleKeydown)
})

// 登录状态变化时重新加载
watch(() => authStore.isLoggedIn, async (loggedIn) => {
    if (loggedIn) {
        await loadData()
        scheduleFormulaRender()
    }
})

// 列表数据、展开状态、模态框内容变化后重新渲染数学公式
watch([filteredQuestions, expandedId, modalVisible], () => {
    scheduleFormulaRender()
})
</script>

<style scoped>
/* ==========================================================================
   Riso Campus · 批改作业本
   规则：奶油纸底 / 纸白卡片 / 1.5px 墨线 / 3~5px 错位硬阴影 / 平涂印刷色。
   不使用毛玻璃、发光、深色底与紫青渐变。
   ========================================================================== */

/* ========== 页面骨架 ========== */
.wq {
    display: flex;
    flex-direction: column;
    gap: 24px;
}

.wq-head {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.wq-head .text-gradient {
    padding: 0 0.06em;
}

.wq-head__actions {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
}

.wq-only-toggle.is-on {
    color: var(--ink);
    background: var(--lime);
    border: 1.5px solid var(--ink);
    box-shadow: 3px 3px 0 var(--ink);
}

.wq-only-toggle.is-on:hover {
    text-decoration: none;
    background: #d7f76b;
    transform: translate(-1px, -1px);
    box-shadow: 4px 4px 0 var(--ink);
}

/* ========== 未登录 ========== */
.wq-guest {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
    padding: 64px 24px;
    text-align: center;
    margin-top: 6vh;
}

.wq-guest__title {
    font-size: 1.4rem;
}

/* ========== 作战概览（成绩单） ========== */
.wq-hero {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(290px, 350px);
    gap: 26px;
    align-items: center;
    padding: 28px 24px 26px;
}

/* 顶部纸胶带 */
.wq-hero__tape {
    position: absolute;
    top: -14px;
    left: 38px;
    width: 108px;
    height: 26px;
    background: var(--butter);
    border: 1.5px solid var(--ink);
    transform: rotate(-2.5deg);
    pointer-events: none;
}

.wq-hero__stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 14px;
}

.wq-hero .stat-card {
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.wq-hero .stat-card:hover {
    transform: translate(-2px, -2px);
    box-shadow: 5px 5px 0 var(--ink);
}

.wq-hero .stat-value {
    color: var(--ink);
}

.wq-hero .stat-label {
    color: var(--ink);
}

.wq-hero .stat-sub {
    color: var(--ink-soft);
    opacity: 0.8;
}

/* 掌握率印章 */
.wq-ring {
    display: flex;
    align-items: center;
    gap: 18px;
    min-width: 0;
}

.wq-ring__dial {
    position: relative;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 128px;
    height: 128px;
    border-radius: 50%;
    border: 2.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 4px 4px 0 var(--ink);
    transform: rotate(-3deg);
}

.wq-ring__dial::before {
    content: '';
    position: absolute;
    inset: 7px;
    border-radius: 50%;
    border: 1.5px dashed var(--line-strong);
    pointer-events: none;
}

.wq-ring__inner {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    text-align: center;
}

.wq-ring__num {
    font-family: var(--font-display);
    font-size: 1.6rem;
    font-weight: 800;
    line-height: 1;
    letter-spacing: -0.04em;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.wq-ring__cap {
    font-family: var(--font-mono);
    font-size: 0.64rem;
    font-weight: 600;
    letter-spacing: 0.16em;
    color: var(--text-3);
}

.wq-ring__side {
    display: flex;
    flex-direction: column;
    gap: 12px;
    min-width: 0;
    flex: 1;
}

.wq-ring__hint {
    color: var(--text-3);
    font-size: 0.8rem;
    line-height: 1.6;
}

/* ========== 筛选栏 ========== */
.wq-filters {
    display: flex;
    flex-direction: column;
    gap: 14px;
}

.wq-filters__row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
    flex-wrap: wrap;
}

.wq-filters__controls {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    flex: 1;
    justify-content: flex-end;
}

.wq-select {
    width: auto;
    min-width: 132px;
    height: 42px;
    padding: 0 38px 0 14px;
    font-size: 0.84rem;
    border-radius: var(--r-full);
    background-position: calc(100% - 19px) 18px, calc(100% - 14px) 18px;
}

.wq-search {
    flex: 1;
    min-width: 200px;
    max-width: 320px;
}

.wq-search .input {
    height: 42px;
    font-size: 0.84rem;
    border-radius: var(--r-full);
    padding-right: 44px;
}

.wq-filters__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    flex-wrap: wrap;
    padding-top: 12px;
    border-top: 1.5px dashed var(--line-strong);
}

.wq-result-count {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--text-3);
    font-size: 0.8rem;
    font-family: var(--font-mono);
    letter-spacing: 0.04em;
}

.wq-result-count strong {
    color: var(--ink);
    font-size: 0.95rem;
    padding: 0 2px;
}

.wq-result-count i {
    color: var(--vermilion);
}

/* ========== 空状态 ========== */
.wq-empty-wrap {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.wq-empty {
    display: grid;
    grid-template-columns: minmax(0, 250px) minmax(0, 1fr);
    align-items: center;
    gap: 34px;
    padding: 34px;
}

.wq-empty__art {
    position: relative;
    padding: 11px;
    background: var(--paper-1);
    border: 1.5px solid var(--ink);
    box-shadow: var(--shadow-card);
    --float-rotate: -2.5deg;
    animation: float-y 7s ease-in-out infinite;
}

.wq-empty__tape {
    position: absolute;
    top: -13px;
    left: 50%;
    width: 92px;
    height: 25px;
    transform: translateX(-50%) rotate(-4deg);
    background: var(--sky);
    border: 1.5px solid var(--ink);
    z-index: 2;
}

.wq-empty__img {
    display: block;
    width: 100%;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 1.5px solid var(--ink);
    background: var(--paper-2);
}

.wq-empty__fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    aspect-ratio: 3 / 4;
    border: 1.5px solid var(--ink);
    background: var(--butter);
    color: var(--ink);
    font-size: 2.6rem;
}

.wq-empty__body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    align-items: flex-start;
}

.wq-empty__title {
    font-size: 1.5rem;
}

.wq-empty__text {
    color: var(--text-2);
    font-size: 0.92rem;
    max-width: 52ch;
}

.wq-empty .empty__actions {
    margin-top: 6px;
}

.wq-empty-filtered {
    padding: 48px 24px;
    border: 2px dashed var(--line-strong);
    background: var(--paper-1);
}

/* ========== 错题卡片 ========== */
.wq-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.wq-card {
    position: relative;
    display: flex;
    overflow: hidden;
    transition: transform var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out);
}

.wq-card:hover {
    transform: translate(-2px, -2px);
    box-shadow: 6px 6px 0 var(--ink);
}

.wq-card.is-expanded {
    box-shadow: 6px 6px 0 var(--ink);
}

.wq-card.is-mastered {
    background: var(--paper-1);
}

/* 左侧学科色条 */
.wq-rail {
    flex-shrink: 0;
    width: 10px;
    border-right: 1.5px solid var(--ink);
    background: var(--cobalt);
}

.wq-rail.is-chem { background: var(--sky); }
.wq-rail.is-english { background: var(--pink); }
.wq-rail.is-junior { background: var(--butter); }
.wq-rail.is-other { background: var(--cobalt); }

.wq-card__body {
    flex: 1;
    min-width: 0;
    padding: 18px 20px 16px 20px;
}

/* 卡片头部（整行可点击展开） */
.wq-card__head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    width: 100%;
    text-align: left;
    padding: 0;
    color: inherit;
    border-radius: 6px;
}

.wq-subject {
    height: 27px;
    font-size: 0.74rem;
}

.wq-card__head .tag {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    font-weight: 700;
    color: var(--text-2);
}

.wq-card__head .tag.is-auto {
    background: var(--butter);
    color: var(--ink);
}

.wq-card__head .tag.is-manual {
    background: var(--paper-2);
    color: var(--text-1);
}

.wq-state {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 25px;
    padding: 0 11px;
    border-radius: var(--r-full);
    border: 1.5px solid var(--ink);
    font-size: 0.72rem;
    font-weight: 800;
    white-space: nowrap;
}

.wq-state.is-pending {
    background: var(--vermilion);
    color: var(--paper-1);
}

.wq-state.is-mastered {
    background: var(--lime);
    color: var(--ink);
}

.wq-wrong-times {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    margin-left: auto;
    padding: 2px 9px;
    border: 1.5px solid var(--vermilion);
    border-radius: 6px;
    color: var(--vermilion);
    background: var(--paper-1);
    font-family: var(--font-mono);
    font-size: 0.72rem;
    font-weight: 600;
    white-space: nowrap;
    transform: rotate(-1.5deg);
}

.wq-chev {
    color: var(--text-3);
    font-size: 0.78rem;
    transition: color var(--dur-fast);
}

.wq-card__head:hover .wq-chev {
    color: var(--ink);
}

/* 题干（作业本答题区） */
.wq-question {
    margin-top: 14px;
    padding: 11px 14px 11px 16px;
    border-left: 3px solid var(--vermilion);
    background: var(--paper-0);
    border-radius: 0 var(--r-xs) var(--r-xs) 0;
    color: var(--ink);
    font-size: 1rem;
    line-height: 1.8;
    word-break: break-word;
    cursor: pointer;
}

/* 题干内容为 v-html 注入，须用 :deep() 才能命中 */
.wq-question :deep(img) {
    max-width: 100%;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-xs);
    margin: 8px 0;
    background: var(--paper-1);
}

.wq-question :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 10px 0;
    font-size: 0.92em;
}

.wq-question :deep(th),
.wq-question :deep(td) {
    border: 1px solid var(--ink);
    padding: 6px 11px;
    text-align: left;
    vertical-align: middle;
}

.wq-question :deep(th) {
    background: var(--butter);
    font-weight: 800;
}

/* 标签 */
.wq-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-top: 12px;
}

.wq-tags .tag {
    border: 1.5px solid var(--ink);
    background: var(--paper-1);
    color: var(--text-2);
    font-weight: 600;
    font-size: 0.72rem;
}

/* 来源 */
.wq-source {
    display: flex;
    align-items: center;
    gap: 7px;
    flex-wrap: wrap;
    margin-top: 12px;
    color: var(--text-3);
    font-size: 0.8rem;
}

.wq-source__jump {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--cobalt);
    font-weight: 700;
    border-bottom: 1.5px solid var(--cobalt);
    transition: gap var(--dur-fast) var(--ease-out), color var(--dur-fast);
}

.wq-source__jump:hover {
    gap: 9px;
    color: var(--vermilion);
    border-bottom-color: var(--vermilion);
}

/* 展开详情 */
.wq-detail {
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1.5px dashed var(--line-strong);
    animation: fade-up 0.3s var(--ease-out) both;
}

.wq-compare {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
}

.wq-compare__col {
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

/* 我的答案 = 错（朱红浅块）；正确答案 = 对（黄绿浅块） */
.wq-compare__col.is-my {
    border-top: 9px solid var(--vermilion);
    background: color-mix(in srgb, var(--vermilion) 9%, var(--paper-1));
}

.wq-compare__col.is-correct {
    border-top: 9px solid var(--lime);
    background: color-mix(in srgb, var(--lime) 26%, var(--paper-1));
}

.wq-compare__label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.76rem;
    font-weight: 800;
    letter-spacing: 0.02em;
    color: var(--ink);
}

.wq-compare__col.is-my .wq-compare__label i {
    color: var(--vermilion);
}

.wq-compare__col.is-correct .wq-compare__label i {
    color: var(--green);
}

.wq-compare__text {
    color: var(--ink);
    font-size: 0.92rem;
    line-height: 1.72;
    word-break: break-word;
}

.wq-notes {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
    margin-top: 14px;
}

.wq-note {
    padding: 12px 14px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    box-shadow: 3px 3px 0 var(--ink);
    background: var(--paper-1);
    min-width: 0;
}

.wq-note.is-reason {
    border-top: 9px solid var(--butter);
    background: color-mix(in srgb, var(--butter) 16%, var(--paper-1));
}

.wq-note.is-note {
    border-top: 9px solid var(--sky);
    background: color-mix(in srgb, var(--sky) 18%, var(--paper-1));
}

.wq-note__label {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-bottom: 6px;
    font-size: 0.76rem;
    font-weight: 800;
    color: var(--ink);
}

.wq-note.is-reason .wq-note__label i {
    color: #b07d00;
}

.wq-note.is-note .wq-note__label i {
    color: var(--cobalt);
}

.wq-note__text {
    color: var(--ink-soft);
    font-size: 0.9rem;
    line-height: 1.7;
    word-break: break-word;
}

/* 操作栏 */
.wq-card__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    flex-wrap: wrap;
    margin-top: 16px;
    padding-top: 14px;
    border-top: 1.5px dashed var(--line-strong);
}

.wq-date {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--text-3);
    font-size: 0.72rem;
    font-family: var(--font-mono);
    letter-spacing: 0.04em;
}

.wq-action-btns {
    display: flex;
    gap: 9px;
    flex-wrap: wrap;
}

/* ========== 加载骨架 ========== */
.wq-sk-card {
    display: flex;
    flex-direction: column;
    gap: 14px;
    padding: 20px 22px;
}

.wq-sk-card__head {
    display: flex;
    gap: 10px;
}

.wq-sk {
    display: block;
    height: 14px;
    border-radius: 4px;
}

.wq-sk--chip {
    width: 84px;
    height: 26px;
}

.wq-sk--tag {
    width: 58px;
    height: 26px;
}

.wq-sk--line {
    width: 100%;
    height: 16px;
}

.wq-sk--short {
    width: 62%;
}

/* ========== 模态框 ========== */
.wq-modal {
    width: min(640px, 100%);
}

.wq-modal--danger {
    width: min(460px, 100%);
}

.wq-confirm-text {
    color: var(--text-2);
    line-height: 1.75;
    font-size: 0.95rem;
}

.wq-modal .field + .field {
    margin-top: 16px;
}

/* 题干预览 */
.wq-preview {
    padding: 12px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    background: var(--paper-0);
    color: var(--ink);
    font-size: 0.92rem;
    line-height: 1.7;
    word-break: break-word;
    max-height: 220px;
    overflow-y: auto;
}

.wq-preview :deep(img) {
    max-width: 100%;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-xs);
    margin: 6px 0;
    background: var(--paper-1);
}

.wq-preview :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 8px 0;
    font-size: 0.9em;
}

.wq-preview :deep(th),
.wq-preview :deep(td) {
    border: 1px solid var(--ink);
    padding: 5px 10px;
    text-align: left;
    vertical-align: middle;
}

.wq-preview :deep(th) {
    background: var(--butter);
    font-weight: 800;
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
    .wq-hero {
        grid-template-columns: minmax(0, 1fr);
        gap: 24px;
    }

    .wq-ring {
        justify-content: flex-start;
    }

    .wq-ring__side {
        max-width: 440px;
    }
}

@media (max-width: 760px) {
    .wq {
        gap: 18px;
    }

    .wq-hero {
        padding: 22px 16px 20px;
    }

    .wq-hero__tape {
        left: 50%;
        transform: translateX(-50%) rotate(-2.5deg);
    }

    .wq-hero__stats {
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 10px;
    }

    .wq-hero .stat-card {
        padding: 14px 12px;
    }

    .wq-hero .stat-card .stat-sub {
        display: none;
    }

    .wq-ring {
        gap: 16px;
    }

    .wq-ring__dial {
        width: 112px;
        height: 112px;
    }

    .wq-ring__num {
        font-size: 1.35rem;
    }

    .wq-filters__row {
        flex-direction: column;
        align-items: stretch;
    }

    .wq-filters__controls {
        justify-content: stretch;
    }

    .wq-select {
        flex: 1;
        min-width: 0;
    }

    .wq-search {
        max-width: none;
        flex-basis: 100%;
    }

    .wq-empty {
        grid-template-columns: minmax(0, 1fr);
        gap: 24px;
        padding: 22px;
    }

    .wq-empty__art {
        max-width: 220px;
        margin: 0 auto;
    }

    .wq-compare,
    .wq-notes {
        grid-template-columns: minmax(0, 1fr);
    }

    .wq-card__actions {
        flex-direction: column;
        align-items: stretch;
    }

    .wq-action-btns {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        width: 100%;
    }

    .wq-wrong-times {
        margin-left: 0;
    }

    .wq-chev {
        margin-left: auto;
    }
}

@media (max-width: 420px) {
    .wq-head__actions .btn {
        flex: 1;
    }

    .wq-ring {
        flex-direction: column;
        align-items: flex-start;
    }

    .wq-hero .stat-card {
        padding: 12px 10px;
    }

    .wq-hero .stat-value {
        font-size: 1.6rem;
    }

    .wq-hero .stat-label {
        font-size: 0.72rem;
    }
}
</style>
