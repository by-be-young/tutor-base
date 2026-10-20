<!--
  TasksView.vue - 今日任务仪表盘（任务中心 · Riso Campus 集点卡）
  ----------------------------------------------------------------------------
  业务行为（与后端契约）保持不变：
    1. 所有登录用户均可从导航栏进入任务中心
    2. 「每日签到」：签到记录、月度统计与积分发放由后端 /api/v1/checkins 承担；
       未签到时按钮显示「去签到」并可打开签到弹窗
    3. 卡池抽卡：卡组、卡片、当期 UP、抽卡价格与概率全部来自
       /api/v1/rewards/catalog；抽卡与 UP 判定由 POST /api/v1/rewards/draws 完成
    4. 收藏室：按服务端卡组陈列已获得卡片，统计使用 /api/v1/rewards 的 stats
    5. 等级只由 lifetimePoints（累计积分）决定，当前积分仅用于消费抽卡
  UI：奶油纸张 + 墨黑文字 + 平涂印刷色块 + 墨线硬阴影 + 站内 /cards 拍立得照片墙。
-->
<template>
    <div class="page page--wide tasks-view">
        <!-- ========== 页头 ========== -->
        <header class="page-head page-head--row tasks-head">
            <div class="head-copy">
                <span class="eyebrow">NEST / MISSION CONTROL</span>
                <h1 class="page-title">任务中心</h1>
                <p class="page-sub">
                    完成每日、每周与限时任务，解锁分级成就，提升成长等级。
                </p>
            </div>

            <!-- 标签页：任务 / 成就 / 收藏室 -->
            <div class="seg tasks-tabs" role="tablist" aria-label="任务中心视图">
                <button type="button" class="seg__btn" role="tab" :aria-selected="activeTab === 'tasks'"
                    :class="{ 'is-active': activeTab === 'tasks' }" @click="switchTab('tasks')">
                    <i class="fas fa-list-check"></i> 任务
                </button>
                <button type="button" class="seg__btn" role="tab" :aria-selected="activeTab === 'achievements'"
                    :class="{ 'is-active': activeTab === 'achievements' }" @click="switchTab('achievements')">
                    <i class="fas fa-medal"></i> 成就
                </button>
                <button type="button" class="seg__btn" role="tab" :aria-selected="activeTab === 'collection'"
                    :class="{ 'is-active': activeTab === 'collection' }" @click="switchTab('collection')">
                    <i class="fas fa-images"></i> 收藏室
                    <span class="seg__count">{{ uniqueCards }}</span>
                </button>
            </div>
        </header>

        <!-- ========== 加载骨架 ========== -->
        <div v-if="tasksStore.isLoading" class="tasks-skeletons" aria-busy="true" aria-label="正在加载任务数据">
            <div class="skeleton skeleton-hero"></div>
            <div class="grid grid--2">
                <div class="skeleton skeleton-card"></div>
                <div class="skeleton skeleton-card"></div>
            </div>
            <div class="skeleton skeleton-track"></div>
        </div>

        <template v-else>
            <!-- ========== 错误态 ========== -->
            <div v-if="tasksStore.loadError" class="empty tasks-error">
                <span class="empty__icon"><i class="fas fa-triangle-exclamation"></i></span>
                <p class="empty__title">任务数据加载失败</p>
                <p class="empty__text">{{ tasksStore.loadError }}</p>
                <div class="empty__actions">
                    <button type="button" class="btn btn--soft" @click="reload">
                        <i class="fas fa-rotate-right"></i> 重试
                    </button>
                </div>
            </div>

            <!-- ========== 成长等级横幅（不区分学科） ========== -->
            <section v-if="activeTab !== 'achievements'" class="level-banner panel" aria-label="成长等级">
                <LevelBadge :points="tasksStore.lifetimePoints" size="lg" />
                <div class="level-banner__side">
                    <div class="level-banner__stat">
                        <strong>{{ levelInfo.level }}</strong>
                        <span>当前等级</span>
                    </div>
                    <div class="level-banner__stat">
                        <strong>{{ levelInfo.toNext }}</strong>
                        <span>积分升级</span>
                    </div>
                    <button type="button" class="btn btn--soft btn--sm" @click="switchTab('achievements')">
                        <i class="fas fa-medal" aria-hidden="true"></i> 查看成就
                    </button>
                </div>
            </section>

            <!-- ========== 积分 Hero（集点卡票根） ========== -->
            <section v-if="activeTab !== 'achievements'" class="hero panel" aria-label="积分总览">
                <div class="hero-inner">
                    <div class="hero-main">
                        <span class="eyebrow">CURRENT POINTS</span>
                        <span class="hero-points-label">当前积分</span>
                        <div class="hero-number">
                            <CountUp :key="tasksStore.points" :value="tasksStore.points" :duration="900" />
                        </div>
                        <p class="hero-next">
                            单抽消耗 <strong>{{ drawCostDisplay }}</strong> 积分 · 当前积分可抽
                            <strong>{{ affordableDraws }}</strong> 次
                        </p>
                        <span class="progress progress--cyan hero-progress">
                            <span class="progress__bar" :style="{ width: levelInfo.progress + '%' }"></span>
                        </span>
                        <p class="hero-level-note">
                            累计 {{ tasksStore.lifetimePoints }} 积分 · 等级进度 {{ levelInfo.progress }}%
                        </p>
                        <div class="hero-actions">
                            <button type="button" class="btn btn--warm" @click="goToCardPool">
                                <i class="fas fa-wand-magic-sparkles"></i> 前往卡池
                            </button>
                            <button type="button" class="btn btn--ghost btn--sm" @click="switchTab('collection', true)">
                                <i class="fas fa-book-open"></i> 收藏室
                            </button>
                        </div>
                    </div>

                    <div class="hero-side">
                        <!-- 收藏进度环（票根虚线分隔） -->
                        <div class="ring-wrap" role="img"
                            :aria-label="`收藏进度 ${collectedPercent}%，已收集 ${uniqueCards} / ${totalCatalogCards} 张不同卡片`">
                            <svg class="ring-svg" viewBox="0 0 120 120" aria-hidden="true">
                                <circle class="ring-track" cx="60" cy="60" r="50" />
                                <circle class="ring-bar" cx="60" cy="60" r="50" :stroke-dasharray="RING_CIRCUMFERENCE"
                                    :stroke-dashoffset="ringOffset" transform="rotate(-90 60 60)" />
                            </svg>
                            <div class="ring-center">
                                <strong>{{ collectedPercent }}%</strong>
                                <span>{{ uniqueCards }} / {{ totalCatalogCards }}</span>
                            </div>
                        </div>

                        <ul class="hero-metrics">
                            <li>
                                <i class="fas fa-dice" aria-hidden="true"></i>
                                <div><strong>{{ totalDraws }}</strong><span>累计抽卡</span></div>
                            </li>
                            <li>
                                <i class="fas fa-star" aria-hidden="true"></i>
                                <div><strong>{{ totalObtained }}</strong><span>累计获得卡片</span></div>
                            </li>
                            <li>
                                <i class="fas fa-layer-group" aria-hidden="true"></i>
                                <div><strong>{{ completeSets }} / {{ totalSets }}</strong><span>集齐卡组</span></div>
                            </li>
                        </ul>
                    </div>
                </div>

                <!-- 卡图拍立得条 -->
                <div v-if="heroCollage.length" class="hero-collage" aria-hidden="true">
                    <img v-for="(card, i) in heroCollage" :key="`${card.cardKey}-${i}`" :src="card.img" alt=""
                        loading="lazy" @error="onImageError" />
                </div>
            </section>

            <!-- ========== 任务列表 ========== -->
            <template v-if="activeTab === 'tasks'">
                <TaskBoard />

                <!-- 签到卡 -->
                <section class="checkin-card panel" aria-label="每日签到状态">
                    <div class="panel-head">
                        <h2 class="panel-title">
                            <i class="fas fa-calendar-check checkin-title-icon" aria-hidden="true"></i> 每日签到
                        </h2>
                        <span class="chip" :class="checkinStore.todayCheckedIn ? 'chip--lime' : 'chip--amber'">
                            <i class="fas"
                                :class="checkinStore.todayCheckedIn ? 'fa-circle-check' : 'fa-hourglass-half'"
                                aria-hidden="true"></i>
                            {{ checkinStore.todayCheckedIn ? '今日已签到' : '今日未签到' }}
                        </span>
                    </div>

                    <div class="panel-body checkin-layout">
                        <div class="checkin-main">
                            <div class="streak">
                                <span class="streak-value">{{ streak }}</span>
                                <span class="streak-unit">天</span>
                                <span class="streak-label">连续签到</span>
                            </div>

                            <div class="checkin-facts">
                                <div class="fact">
                                    <strong>+{{ checkinStore.pointsPerCheckIn }}</strong>
                                    <span>每次签到</span>
                                </div>
                                <div class="fact">
                                    <strong>{{ checkinStore.monthCount }}</strong>
                                    <span>本月已签到</span>
                                </div>
                                <div class="fact">
                                    <strong>{{ nextBonus ? nextBonus - checkinStore.monthCount : 0 }}</strong>
                                    <span>{{ nextBonus ? `距第 ${nextBonus} 次奖励` : '月度奖励已拿满' }}</span>
                                </div>
                            </div>

                            <!-- 签到主按钮：E2E 通过可访问名定位 -->
                            <button v-if="!checkinStore.todayCheckedIn" type="button"
                                class="btn btn--warm checkin-cta" @click="checkinStore.openModal()">
                                <i class="fas fa-hand-point-up" aria-hidden="true"></i>
                                去签到
                                <span class="cta-bonus">+{{ checkinStore.pointsPerCheckIn }}</span>
                            </button>
                            <button v-else type="button" class="btn btn--soft checkin-cta"
                                @click="checkinStore.openModal()">
                                <i class="fas fa-calendar-days" aria-hidden="true"></i> 查看签到月历
                            </button>
                        </div>

                        <!-- 本月签到微缩日历 -->
                        <div class="mini-calendar" aria-label="本月签到日历">
                            <div class="mini-cal-head">
                                <span>{{ checkinStore.year || '—' }} 年 {{ checkinStore.month || '—' }} 月</span>
                                <span class="mini-cal-note">
                                    {{ checkinStore.loading ? '同步中…' : `${checkinStore.monthCount} 次签到` }}
                                </span>
                            </div>
                            <div v-if="miniCalendar.length" class="mini-cal-body">
                                <div class="mini-weekdays">
                                    <span v-for="w in WEEKDAYS" :key="w">{{ w }}</span>
                                </div>
                                <div class="mini-days">
                                    <span v-for="(cell, i) in miniCalendar" :key="i" class="mini-day"
                                        :class="{ 'is-blank': !cell.day, 'is-checked': cell.checked, 'is-today': cell.today }">
                                        {{ cell.day || '' }}
                                        <i v-if="cell.checked" class="fas fa-check mini-check" aria-hidden="true"></i>
                                    </span>
                                </div>
                            </div>
                            <div v-else class="loading-line">
                                <span class="spinner" aria-hidden="true"></span> 正在同步月历…
                            </div>
                        </div>
                    </div>
                </section>

                <!-- 卡池 -->
                <section ref="cardPoolSection" class="gacha-section panel" aria-label="收藏卡池">
                    <div class="panel-head">
                        <h2 class="panel-title">
                            <i class="fas fa-wand-magic-sparkles gacha-title-icon" aria-hidden="true"></i> 收藏卡池
                        </h2>
                        <div v-if="catalogStore.loaded" class="legend">
                            <span class="chip chip--amber"><i class="fas fa-star" aria-hidden="true"></i> 稀有
                                <strong>{{ catalogStore.rareRatePercent }}</strong></span>
                            <span class="chip chip--vermilion"><i class="fas fa-fire" aria-hidden="true"></i> 其中
                                <strong>{{ catalogStore.upRatePercent }}</strong> 为 UP</span>
                            <span class="chip"><i class="fas fa-coins" aria-hidden="true"></i>
                                {{ drawCostDisplay }} 积分/抽</span>
                        </div>
                    </div>

                    <div class="panel-body gacha-body">
                        <!-- 目录加载中 -->
                        <div v-if="catalogStore.loading && !catalogStore.loaded" class="gacha-state">
                            <span class="loading-line"><span class="spinner" aria-hidden="true"></span> 正在同步卡池…</span>
                        </div>

                        <!-- 目录加载失败：保持页面可用，可重试 -->
                        <div v-else-if="catalogStore.error && !catalogStore.loaded" class="gacha-state">
                            <div class="empty gacha-error">
                                <span class="empty__icon"><i class="fas fa-triangle-exclamation" aria-hidden="true"></i></span>
                                <p class="empty__title">卡池信息加载失败</p>
                                <p class="empty__text">{{ catalogStore.error }}</p>
                                <button type="button" class="btn btn--soft btn--sm" @click="reloadCatalog">
                                    <i class="fas fa-rotate-right" aria-hidden="true"></i> 重试
                                </button>
                            </div>
                        </div>

                        <template v-else>
                            <!-- 左：当期 UP 卡 -->
                            <div class="up-side">
                                <div class="up-label">
                                    <span class="up-tag"><i class="fas fa-fire" aria-hidden="true"></i> 当期 UP</span>
                                    <span class="up-cycle">每周轮换</span>
                                </div>
                                <div class="up-stage" :class="{ 'is-empty': !currentUp }">
                                    <span class="up-aura" aria-hidden="true"></span>
                                    <span v-if="currentUp" class="up-badge">UP</span>
                                    <img v-if="currentUp" :src="currentUp.img" :alt="currentUp.name" class="up-img"
                                        @error="onImageError" />
                                    <span v-else class="up-placeholder" aria-hidden="true">
                                        <i class="fas fa-hourglass-half"></i>
                                        卡池更新中
                                    </span>
                                </div>
                                <div class="up-meta">
                                    <strong>{{ currentUp?.name || '当期 UP 卡片' }}</strong>
                                    <span class="up-set">{{ currentUp?.setName || '待卡池同步' }}</span>
                                    <span v-if="currentUp?.rotatesAt" class="up-rotates">
                                        <i class="fas fa-clock-rotate-left" aria-hidden="true"></i>
                                        轮换至 {{ formatRotatesAt(currentUp.rotatesAt) }}
                                    </span>
                                </div>
                            </div>

                            <!-- 右：抽卡操作 -->
                            <div class="draw-side">
                                <p class="gacha-note">
                                    <i class="fas fa-circle-info" aria-hidden="true"></i>
                                    每抽消耗 <strong>{{ drawCostDisplay }}</strong> 积分 · 稀有卡概率
                                    <strong>{{ catalogStore.rareRatePercent }}</strong> · 若出稀有，有
                                    <strong>{{ catalogStore.upRatePercent }}</strong> 概率为当期 UP ·
                                    已拥有的卡会重复掉落并计入累计获得
                                </p>

                                <div class="draw-actions">
                                    <button type="button" class="btn btn--warm draw-btn" :disabled="!canDraw"
                                        @click="drawOnce">
                                        <i class="fas fa-dice" aria-hidden="true"></i>
                                        {{ drawing ? '抽取中…' : `抽一次 · ${drawCostDisplay} 积分` }}
                                    </button>
                                    <button type="button" class="btn btn--primary draw-btn" :disabled="!canDrawTen"
                                        @click="drawTen">
                                        <i class="fas fa-layer-group" aria-hidden="true"></i>
                                        {{ drawing ? '抽取中…' : `十连抽 · ${tenPullCostDisplay} 积分` }}
                                    </button>
                                    <span class="draw-balance">
                                        剩余积分 <strong>{{ tasksStore.points }}</strong>
                                        <span v-if="affordableDraws < 1" class="draw-poor">不足一次</span>
                                        <span v-else-if="affordableDraws < 10" class="draw-poor">不足十连</span>
                                    </span>
                                </div>

                                <!-- 近期抽卡结果小条 -->
                                <div class="recent-draws" aria-label="最近抽卡记录">
                                    <span class="recent-label">最近掉落 · 累计抽卡 {{ totalDraws }} 次</span>
                                    <div class="recent-strip">
                                        <template v-for="row in recentDraws" :key="row.id">
                                            <img v-if="recentCardImg(row)" :src="recentCardImg(row)"
                                                :alt="recentCard(row)?.name || ''" loading="lazy" class="recent-thumb"
                                                :class="{ 'is-rare': row.rarity === 'rare' }" @error="onImageError" />
                                        </template>
                                        <span v-if="recentDraws.length === 0" class="recent-empty">
                                            还没有抽过卡，来一发试试手气
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </template>
                    </div>
                </section>

                <!-- 收藏室入口 -->
                <RevealOnScroll as="section" class="collection-entry panel" aria-label="收藏室入口">
                    <div v-if="entryCollage.length" class="entry-collage" aria-hidden="true">
                        <img v-for="card in entryCollage" :key="card.cardKey" :src="card.img" alt="" loading="lazy"
                            @error="onImageError" />
                    </div>
                    <div class="entry-copy">
                        <span class="eyebrow">COLLECTION ROOM</span>
                        <h2 class="section-title entry-title">
                            <i class="fas fa-book-open" aria-hidden="true"></i> 收藏室
                        </h2>
                        <p class="entry-desc">
                            已收集 <strong>{{ uniqueCards }}</strong> / {{ totalCatalogCards }} 张不同卡片 ·
                            累计获得 <strong>{{ totalObtained }}</strong> 张 ·
                            集齐 <strong>{{ completeSets }}</strong> / {{ totalSets }} 套卡组
                        </p>
                        <span class="progress entry-progress">
                            <span class="progress__bar" :style="{ width: collectedPercent + '%' }"></span>
                        </span>
                        <div class="entry-actions">
                            <button type="button" class="btn btn--primary" @click="switchTab('collection', true)">
                                <i class="fas fa-arrow-right" aria-hidden="true"></i> 进入收藏室
                            </button>
                            <button type="button" class="btn btn--soft btn--sm" @click="goToCardPool">
                                <i class="fas fa-wand-magic-sparkles" aria-hidden="true"></i> 前往卡池
                            </button>
                            <span class="entry-note">
                                <i class="fas fa-star" aria-hidden="true"></i>
                                稀有 {{ catalogStore.rareRatePercent }} · 卡池周期性轮换
                            </span>
                        </div>
                    </div>
                </RevealOnScroll>
            </template>

            <!-- ========== 收藏室 / 成就 ========== -->
            <CollectionRoom v-else-if="activeTab === 'collection'" @go-draw="goToCardPool" />
            <AchievementBoard v-else />
        </template>

        <!-- ========== 抽卡结果弹窗（Teleport 到 body，避免被页面堆叠上下文裁切） ========== -->
        <Teleport to="body">
            <div v-if="claimVisible" class="backdrop" @click.self="closeClaim">
                <div class="modal draw-modal" :class="{ 'draw-modal--ten': isTenPull }" role="dialog" aria-modal="true"
                    :aria-label="isTenPull ? '十连抽结果' : '抽卡结果'">
                    <button type="button" class="modal__close draw-close" aria-label="关闭抽卡结果" @click="closeClaim">
                        <i class="fas fa-times" aria-hidden="true"></i>
                    </button>

                    <header class="draw-modal__head">
                        <span class="eyebrow">DRAW RESULT / 抽卡结果</span>
                        <h3 class="draw-modal__title">
                            <i class="fas fa-wand-magic-sparkles" aria-hidden="true"></i>
                            {{ isTenPull ? '十连抽结果' : '单抽结果' }}
                        </h3>
                        <div v-if="resultSummary" class="draw-summary" aria-label="抽卡结果统计">
                            <span class="draw-sum-chip is-rare">
                                <i class="fas fa-star" aria-hidden="true"></i> 稀有 {{ resultSummary.rare }}
                            </span>
                            <span class="draw-sum-chip is-up">
                                <i class="fas fa-fire" aria-hidden="true"></i> UP {{ resultSummary.up }}
                            </span>
                            <span class="draw-sum-chip is-new">
                                <i class="fas fa-sparkles" aria-hidden="true"></i> 新卡 {{ resultSummary.fresh }}
                            </span>
                            <span class="draw-sum-chip is-dup">
                                <i class="fas fa-repeat" aria-hidden="true"></i> 重复 {{ resultSummary.duplicate }}
                            </span>
                        </div>
                    </header>

                    <!-- 单抽：翻牌揭晓 -->
                    <div v-if="!isTenPull" class="reveal-stage reveal-stage--single">
                        <div class="reveal-flip" :class="{ 'is-flipped': flipped }">
                            <div class="reveal-flip__inner">
                                <div class="reveal-flip__face reveal-flip__back">
                                    <span class="back-glyph" aria-hidden="true"><i class="fas fa-gift"></i></span>
                                    <span class="back-text">揭晓卡片中…</span>
                                </div>
                                <div class="reveal-flip__face reveal-flip__front"
                                    :class="{ 'is-rare': currentReward?.rarity === 'rare', 'is-up': currentReward?.isUp }">
                                    <div class="reveal-photo">
                                        <img v-if="currentReward?.card?.img" :src="currentReward.card.img"
                                            :alt="currentReward.card.name" class="reveal-img" @error="onImageError" />
                                        <span v-else class="reveal-empty" aria-hidden="true"><i class="fas fa-gift"></i></span>
                                        <span v-if="currentReward?.isUp" class="reveal-flag is-up">UP</span>
                                        <span v-else-if="currentReward?.duplicate" class="reveal-flag is-dup">重复</span>
                                    </div>
                                    <div class="reveal-caption">
                                        <span class="reveal-rarity" :class="{ 'is-rare': currentReward?.rarity === 'rare' }">
                                            {{ rarityLabel(currentReward) }}
                                        </span>
                                        <strong class="reveal-name">{{ currentReward?.card?.name || '神秘卡片' }}</strong>
                                        <span class="reveal-set">{{ currentReward?.card?.setName }}</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <p v-if="flipped && currentReward?.duplicate" class="reveal-hint">
                            <i class="fas fa-repeat" aria-hidden="true"></i> 重复卡片，已计入累计获得
                        </p>
                    </div>

                    <!-- 十连：响应式结果网格 -->
                    <div v-else class="reveal-grid" :class="{ 'is-revealed': flipped }" aria-label="十连抽结果">
                        <div v-for="(result, index) in drawResults" :key="`${result.cardKey}-${index}`"
                            class="reveal-tile"
                            :class="{ 'is-rare': result.rarity === 'rare', 'is-up': result.isUp, 'is-dup': result.duplicate }"
                            :style="{ '--i': index }">
                            <div class="reveal-tile__photo">
                                <img v-if="result.card?.img" :src="result.card.img" :alt="result.card.name"
                                    loading="lazy" @error="onImageError" />
                                <span v-else class="reveal-empty" aria-hidden="true"><i class="fas fa-gift"></i></span>
                                <span v-if="result.isUp" class="reveal-flag is-up">UP</span>
                                <span v-else-if="result.duplicate" class="reveal-flag is-dup">重复</span>
                            </div>
                            <span class="reveal-tile__name">{{ result.card?.name || '神秘卡片' }}</span>
                        </div>
                    </div>

                    <div class="claim-footer">
                        <button v-if="!flipped" type="button" class="btn claim-btn" disabled>
                            <i class="fas fa-spinner fa-spin" aria-hidden="true"></i> 揭晓中…
                        </button>
                        <button v-else type="button" class="btn btn--warm claim-btn" @click="confirmClaim">
                            <i class="fas fa-hand-holding-heart" aria-hidden="true"></i>
                            收下卡片
                        </button>
                    </div>
                </div>
            </div>
        </Teleport>
    </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { useCheckinStore } from '@/stores/checkinStore'
import { useTasksStore } from '@/stores/tasksStore'
import { useCardCatalogStore } from '@/stores/cardCatalogStore'
import { showToast } from '@/utils/toast'
import { levelFromPoints } from '@/utils/level'
import CountUp from '@/components/ui/CountUp.vue'
import LevelBadge from '@/components/ui/LevelBadge.vue'
import RevealOnScroll from '@/components/ui/RevealOnScroll.vue'
import CollectionRoom from '@/components/tasks/CollectionRoom.vue'
import TaskBoard from '@/components/tasks/TaskBoard.vue'
import AchievementBoard from '@/components/tasks/AchievementBoard.vue'

const authStore = useAuthStore()
const checkinStore = useCheckinStore()
const tasksStore = useTasksStore()
const catalogStore = useCardCatalogStore()
const route = useRoute()
const router = useRouter()

// ========== 常量 ==========
const WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']
const RING_RADIUS = 50
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS

// ========== 标签页 ==========
const activeTab = ref('tasks')
const levelInfo = computed(() => levelFromPoints(tasksStore.lifetimePoints))
const VALID_TABS = ['tasks', 'achievements', 'collection']

function applyTabFromQuery() {
    const tab = String(route.query.tab || '')
    if (VALID_TABS.includes(tab)) activeTab.value = tab
}

watch(() => route.query.tab, applyTabFromQuery)

// ========== 收藏统计（优先使用服务端 stats） ==========
const collection = computed(() => tasksStore.collection)
const uniqueCards = computed(() => {
    const serverUnique = Number(tasksStore.stats.uniqueCards)
    if (Number.isFinite(serverUnique) && serverUnique > 0) return serverUnique
    return new Set(collection.value.map(row => row.cardKey)).size
})
const totalCatalogCards = computed(() =>
    Number(catalogStore.totalCards) || Number(tasksStore.stats.totalCatalogCards) || 0)
const totalSets = computed(() =>
    Number(catalogStore.totalSets) || Number(tasksStore.stats.totalSets) || catalogStore.sets.length)
const collectedPercent = computed(() =>
    totalCatalogCards.value > 0 ? Math.round((uniqueCards.value / totalCatalogCards.value) * 100) : 0)
const totalDraws = computed(() => Number(tasksStore.stats.totalDraws) || 0)
const totalObtained = computed(() =>
    Number(tasksStore.stats.totalCards) || collection.value.length)
const completeSets = computed(() => {
    const owned = new Set(collection.value.map(row => row.cardKey))
    return catalogStore.sets.filter(set =>
        catalogStore.cardsInSet(set).every(card => owned.has(card.cardKey))).length
})
const ringOffset = computed(() =>
    RING_CIRCUMFERENCE * (1 - Math.min(100, collectedPercent.value) / 100))

// ========== 卡池 ==========
const cardPoolSection = ref(null)
const drawCostDisplay = computed(() => catalogStore.drawCost > 0 ? catalogStore.drawCost : '—')
const tenPullCostDisplay = computed(() => catalogStore.drawCost > 0 ? catalogStore.drawCost * 10 : '—')
const affordableDraws = computed(() => {
    if (catalogStore.drawCost <= 0) return 0
    return Math.floor(tasksStore.points / catalogStore.drawCost)
})
const currentUp = computed(() => catalogStore.currentUp)
const canDraw = computed(() =>
    catalogStore.loaded && catalogStore.drawCost > 0 &&
    tasksStore.points >= catalogStore.drawCost && !drawing.value)
const canDrawTen = computed(() =>
    catalogStore.loaded && catalogStore.drawCost > 0 &&
    tasksStore.points >= catalogStore.drawCost * 10 && !drawing.value)
const recentDraws = computed(() => collection.value.slice(0, 8))

function recentCard(row) {
    return catalogStore.findCard(row.setKey, row.cardKey)
}

function recentCardImg(row) {
    const card = recentCard(row)
    return card ? card.img : ''
}

function formatRotatesAt(value) {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return ''
    return new Intl.DateTimeFormat('zh-CN', {
        month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit'
    }).format(date)
}

// ========== 签到推导信息 ==========
const nextBonus = computed(() =>
    checkinStore.bonusMilestones.find(m => m > checkinStore.monthCount) ?? null)

/** 本月连续签到天数（已加载月份内、从服务端今日/昨日向前连续计数） */
const streak = computed(() => {
    if (!checkinStore.today || !checkinStore.dates.length) return 0
    const daySet = new Set(checkinStore.dates)
    const cursor = new Date(`${checkinStore.today}T00:00:00`)
    if (!daySet.has(checkinStore.today)) cursor.setDate(cursor.getDate() - 1)
    let count = 0
    while (count < 31) {
        if (!checkinStore.year || !checkinStore.month) break
        if (cursor.getFullYear() !== checkinStore.year || cursor.getMonth() + 1 !== checkinStore.month) break
        const iso = formatDate(cursor)
        if (!daySet.has(iso)) break
        count += 1
        cursor.setDate(cursor.getDate() - 1)
    }
    return count
})

function formatDate(date) {
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
}

/** 本月签到微缩日历：周一开头，已签到 / 今日高亮 */
const miniCalendar = computed(() => {
    const { year, month } = checkinStore
    if (!year || !month) return []
    const checked = new Set(checkinStore.dates.map(d => Number(d.slice(8, 10))))
    const today = checkinStore.today
    const firstWeekday = (new Date(year, month - 1, 1).getDay() + 6) % 7
    const daysInMonth = new Date(year, month, 0).getDate()
    const cells = [
        ...Array(firstWeekday).fill(null),
        ...Array.from({ length: daysInMonth }, (_, i) => i + 1)
    ]
    while (cells.length % 7 !== 0) cells.push(null)
    return cells.map(day => ({
        day,
        checked: day ? checked.has(day) : false,
        today: day && today
            ? today === `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
            : false
    }))
})

// ========== 卡组拼贴（站内 /cards 图片，来自服务端目录） ==========
const heroCollage = computed(() => {
    const cards = []
    for (const set of catalogStore.sets) {
        if (set.rare) cards.push(set.rare)
        cards.push(...set.commons.slice(0, 2))
        if (cards.length >= 9) break
    }
    return cards.slice(0, 9)
})
const entryCollage = computed(() => {
    const cards = catalogStore.sets.map(set => set.rare).filter(Boolean)
    const firstCommon = catalogStore.sets[0]?.commons?.[0]
    if (firstCommon) cards.push(firstCommon)
    return cards.slice(0, 6)
})

/** 图片加载失败时隐藏 img，露出纸色兜底 */
function onImageError(event) {
    const img = event?.target
    if (img) img.style.opacity = '0'
}

// ========== 页面跳转 / 卡池定位 ==========
async function goToCardPool() {
    if (activeTab.value !== 'tasks') {
        await switchTab('tasks')
    }
    await nextTick()
    // 等路由 replace 的滚动恢复执行完再定位卡池，避免被滚回顶部覆盖
    window.setTimeout(() => {
        cardPoolSection.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }, 80)
}

function switchTab(tab, scrollTop = false) {
    activeTab.value = tab
    const navigation = router.replace({ path: '/tasks', query: tab === 'tasks' ? {} : { tab } })
    if (scrollTop) window.scrollTo({ top: 0, behavior: 'smooth' })
    return navigation
}

async function reloadCatalog() {
    await catalogStore.load(true)
    if (catalogStore.error) showToast(catalogStore.error, 'error')
}

// ========== 抽卡结果 ==========
const claimVisible = ref(false)
const flipped = ref(false)
const currentReward = ref(null)
const drawResults = ref([])
const drawing = ref(false)
let flipTimer = null

const isTenPull = computed(() => drawResults.value.length > 1)
const resultSummary = computed(() => {
    const results = drawResults.value
    if (!results.length) return null
    return {
        rare: results.filter(result => result.rarity === 'rare').length,
        up: results.filter(result => result.isUp).length,
        fresh: results.filter(result => !result.duplicate).length,
        duplicate: results.filter(result => result.duplicate).length
    }
})

function rarityLabel(result) {
    if (!result) return '揭晓中'
    return result.rarity === 'rare' ? '✦ 稀有卡片' : '普通卡片'
}

function scheduleReveal(delay = 650) {
    if (flipTimer) clearTimeout(flipTimer)
    flipTimer = setTimeout(() => { flipped.value = true }, delay)
}

/** 卡池单抽：服务端抽卡 → 翻卡弹窗揭晓 */
async function drawOnce() {
    if (!canDraw.value) return
    drawing.value = true
    flipped.value = false
    drawResults.value = []
    currentReward.value = null
    claimVisible.value = true
    try {
        const result = await tasksStore.drawCard()
        currentReward.value = result
        drawResults.value = result ? [result] : []
        scheduleReveal()
    } catch (e) {
        claimVisible.value = false
        drawResults.value = []
        showToast(e.message || '抽卡失败，请稍后再试', 'error')
    } finally {
        drawing.value = false
    }
}

/** 卡池十连：服务端一次性抽 10 张 */
async function drawTen() {
    if (!canDrawTen.value) return
    drawing.value = true
    flipped.value = false
    drawResults.value = []
    currentReward.value = null
    claimVisible.value = true
    try {
        const results = await tasksStore.drawCards(10)
        drawResults.value = results
        currentReward.value = results[0] || null
        if (!results.length) {
            claimVisible.value = false
            showToast('本次抽卡没有获得卡片，请稍后再试', 'error')
            return
        }
        scheduleReveal(750)
    } catch (e) {
        claimVisible.value = false
        drawResults.value = []
        showToast(e.message || '抽卡失败，请稍后再试', 'error')
    } finally {
        drawing.value = false
    }
}

function confirmClaim() {
    if (!flipped.value || !resultSummary.value) return
    const summary = resultSummary.value
    claimVisible.value = false
    if (isTenPull.value) {
        showToast(
            `十连抽完成：稀有 ${summary.rare} · UP ${summary.up} · 新卡 ${summary.fresh} · 重复 ${summary.duplicate}`,
            summary.rare || summary.up ? 'success' : 'info'
        )
        return
    }
    const result = drawResults.value[0]
    if (!result) return
    const tag = result.isUp ? '【UP】' : ''
    const name = result.card?.name || '卡片'
    showToast(
        result.duplicate
            ? `${tag}「${name}」重复获得，已计入累计获得`
            : `${tag}「${name}」已收入收藏室`,
        result.duplicate ? 'info' : 'success'
    )
}

function closeClaim() {
    // 揭晓动画进行中不允许关闭，避免状态错乱
    if (!flipped.value) return
    claimVisible.value = false
    drawResults.value = []
}

// ========== 数据加载 ==========
async function reload() {
    await tasksStore.load(authStore.currentUser)
    if (tasksStore.loadError) showToast(tasksStore.loadError, 'error')
}

onMounted(() => {
    applyTabFromQuery()
    checkinStore.loadMonth() // 首屏即显示今日签到状态（去签到 / 今日已签到）
    catalogStore.load()
    tasksStore.load(authStore.currentUser).then(() => {
        if (tasksStore.loadError) showToast(tasksStore.loadError, 'error')
    })
})

onBeforeUnmount(() => {
    if (flipTimer) clearTimeout(flipTimer)
})
</script>

<style scoped>
/* ========== 成长等级横幅 ========== */
.level-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 24px;
    flex-wrap: wrap;
    padding: 22px 26px;
    margin-bottom: 18px;
}

.level-banner__side {
    display: flex;
    align-items: center;
    gap: 20px;
    flex-wrap: wrap;
}

.level-banner__stat {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding-left: 18px;
    border-left: 1.5px solid var(--line-strong);
}

.level-banner__stat strong {
    font-family: var(--font-display);
    font-size: 1.5rem;
    font-weight: 800;
    line-height: 1;
    color: var(--ink);
}

.level-banner__stat span {
    font-size: 0.72rem;
    color: var(--text-3);
}

@media (max-width: 768px) {
    .level-banner {
        padding: 18px;
    }

    .level-banner__side {
        width: 100%;
        justify-content: space-between;
    }
}
.tasks-view {
    position: relative;
}

/* ========== 页头 / 标签 ========== */
.tasks-head {
    align-items: flex-end;
}

.head-copy {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.tasks-tabs {
    flex-shrink: 0;
}

.tasks-tabs .seg__btn {
    height: 38px;
}

/* ========== 加载骨架 ========== */
.tasks-skeletons {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.skeleton-hero {
    height: 300px;
    border-radius: var(--r-lg);
}

.skeleton-card {
    height: 172px;
    border-radius: var(--r-lg);
}

.skeleton-track {
    height: 196px;
    border-radius: var(--r-lg);
}

.tasks-error {
    margin-bottom: 24px;
}

/* ========== Hero（集点卡票根） ========== */
.hero {
    position: relative;
    margin-bottom: 26px;
    padding: clamp(22px, 3.2vw, 34px);
    overflow: hidden;
}

.hero-inner {
    position: relative;
    z-index: 2;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 32px;
    align-items: center;
}

.hero-main {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
}

.hero-points-label {
    margin-top: 10px;
    font-family: var(--font-mono);
    font-size: 0.78rem;
    font-weight: 600;
    letter-spacing: 0.16em;
    color: var(--text-3);
}

.hero-number {
    margin-top: 2px;
    font-family: var(--font-display);
    font-size: clamp(3.6rem, 8vw, 5.8rem);
    font-weight: 800;
    line-height: 0.9;
    letter-spacing: -0.05em;
    font-variant-numeric: tabular-nums;
    color: var(--ink);
    text-shadow: 6px 6px 0 var(--butter);
}

.hero-next {
    margin-top: 18px;
    color: var(--text-2);
    font-size: 0.9rem;
}

.hero-next strong {
    color: var(--vermilion);
    font-family: var(--font-display);
    font-size: 1.08em;
    font-variant-numeric: tabular-nums;
}

.hero-progress {
    width: min(440px, 100%);
    height: 12px;
    margin-top: 16px;
}

.hero-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    margin-top: 20px;
}

.hero-side {
    display: flex;
    align-items: center;
    gap: 26px;
    padding-left: 28px;
    border-left: 2px dashed var(--line-strong);
}

.ring-wrap {
    position: relative;
    width: 150px;
    height: 150px;
    flex-shrink: 0;
}

.ring-svg {
    width: 100%;
    height: 100%;
}

.ring-track {
    fill: none;
    stroke: var(--paper-2);
    stroke-width: 12;
}

.ring-bar {
    fill: none;
    stroke: var(--vermilion);
    stroke-width: 12;
    stroke-linecap: butt;
    transition: stroke-dashoffset 1s var(--ease-out);
}

.ring-center {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
}

.ring-center strong {
    font-family: var(--font-display);
    font-size: 1.9rem;
    font-weight: 800;
    line-height: 1;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.ring-center span {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    color: var(--text-3);
}

.hero-metrics {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.hero-metrics li {
    display: flex;
    align-items: center;
    gap: 10px;
}

.hero-metrics i {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    flex-shrink: 0;
    border: 1.5px solid var(--ink);
    border-radius: 9px;
    background: var(--butter);
    color: var(--ink);
    font-size: 0.84rem;
    box-shadow: 2px 2px 0 var(--ink);
}

.hero-metrics li:nth-child(2) i {
    background: var(--lime);
}

.hero-metrics li:nth-child(3) i {
    background: var(--sky);
}

.hero-metrics strong {
    display: block;
    font-family: var(--font-display);
    font-size: 1rem;
    font-weight: 800;
    line-height: 1.15;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.hero-metrics span {
    display: block;
    font-size: 0.72rem;
    color: var(--text-3);
}

/* 卡图拍立得条 */
.hero-collage {
    position: relative;
    z-index: 2;
    display: flex;
    gap: 14px;
    margin-top: 26px;
    padding-top: 18px;
    border-top: 2px dashed var(--line-strong);
    overflow: hidden;
}

.hero-collage img {
    flex: 0 0 auto;
    width: 84px;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 5px solid var(--paper-1);
    outline: 1.5px solid var(--ink);
    background: var(--paper-2);
    box-shadow: 4px 4px 0 rgba(28, 26, 20, 0.85);
    transform: rotate(var(--tilt, 0deg));
    transition: transform var(--dur) var(--ease-out);
}

.hero-collage img:nth-child(1) { --tilt: -4deg; }
.hero-collage img:nth-child(2) { --tilt: 3deg; }
.hero-collage img:nth-child(3) { --tilt: -2deg; }
.hero-collage img:nth-child(4) { --tilt: 2.4deg; }
.hero-collage img:nth-child(5) { --tilt: -3.2deg; }
.hero-collage img:nth-child(6) { --tilt: 1.8deg; }
.hero-collage img:nth-child(7) { --tilt: -1.6deg; }
.hero-collage img:nth-child(8) { --tilt: 3.4deg; }
.hero-collage img:nth-child(9) { --tilt: -2.6deg; }

.hero-collage img:hover {
    transform: rotate(0deg) translateY(-4px);
}

/* ========== 签到卡 ========== */
.checkin-card {
    margin-bottom: 26px;
}

.checkin-title-icon {
    color: var(--vermilion);
}

.checkin-layout {
    display: grid;
    grid-template-columns: minmax(0, 1.2fr) minmax(280px, 0.8fr);
    gap: 28px;
    align-items: center;
}

.checkin-main {
    display: flex;
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
}

.streak {
    display: flex;
    align-items: baseline;
    gap: 8px;
}

.streak-value {
    font-family: var(--font-display);
    font-size: clamp(2.6rem, 5vw, 3.6rem);
    font-weight: 800;
    line-height: 1;
    letter-spacing: -0.03em;
    color: var(--vermilion);
    font-variant-numeric: tabular-nums;
}

.streak-unit {
    font-family: var(--font-display);
    font-size: 1rem;
    font-weight: 800;
    color: var(--ink);
}

.streak-label {
    margin-left: 4px;
    font-size: 0.82rem;
    color: var(--text-3);
}

.checkin-facts {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 12px;
    width: 100%;
}

.fact {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 12px 14px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-sm);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 var(--ink);
}

.fact:nth-child(1) {
    background: var(--butter);
}

.fact:nth-child(2) {
    background: var(--sky);
}

.fact:nth-child(3) {
    background: var(--lime);
}

.fact strong {
    font-family: var(--font-display);
    font-size: 1.18rem;
    font-weight: 800;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.fact span {
    font-size: 0.72rem;
    font-weight: 600;
    color: var(--ink-soft);
}

.checkin-cta {
    height: 50px;
    padding: 0 26px;
    font-size: 0.96rem;
    font-weight: 800;
}

.cta-bonus {
    padding: 2px 10px;
    border-radius: var(--r-full);
    background: var(--ink);
    color: var(--paper-1);
    font-family: var(--font-mono);
    font-size: 0.82rem;
}

.mini-calendar {
    padding: 16px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    box-shadow: var(--shadow-card);
}

.mini-cal-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;
    font-family: var(--font-display);
    font-size: 0.84rem;
    font-weight: 800;
    color: var(--ink);
}

.mini-cal-note {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    font-weight: 400;
    color: var(--text-3);
}

.mini-weekdays {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    margin-bottom: 5px;
}

.mini-weekdays span {
    text-align: center;
    font-family: var(--font-mono);
    font-size: 0.6rem;
    color: var(--text-3);
}

.mini-days {
    display: grid;
    grid-template-columns: repeat(7, 1fr);
    gap: 3px;
}

.mini-day {
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
    aspect-ratio: 1;
    border: 1px solid var(--line);
    border-radius: 5px;
    background: var(--paper-1);
    color: var(--text-2);
    font-size: 0.72rem;
    font-weight: 600;
}

.mini-day.is-blank {
    visibility: hidden;
}

.mini-day.is-checked {
    border-color: var(--ink);
    background: var(--paper-2);
    color: var(--ink);
    font-weight: 800;
}

.mini-check {
    position: absolute;
    right: 2px;
    bottom: 0;
    color: var(--vermilion);
    font-size: 0.42rem;
}

.mini-day.is-today {
    border: 2px solid var(--ink);
    background: var(--butter);
    color: var(--ink);
    font-weight: 800;
}

/* ========== 卡池 ========== */
.gacha-section {
    margin-bottom: 26px;
    scroll-margin-top: 84px;
}

.gacha-title-icon {
    color: var(--vermilion);
}

.legend {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
}

.gacha-body {
    display: grid;
    grid-template-columns: minmax(220px, 300px) minmax(0, 1fr);
    gap: 32px;
    align-items: start;
    padding: 24px;
    background:
        radial-gradient(circle at 82% 12%, rgba(240, 178, 122, 0.22), transparent 30%),
        var(--paper-1);
}

/* 左侧 UP 卡 */
.up-side {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.up-label {
    display: flex;
    align-items: center;
    gap: 10px;
}

.up-tag {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 4px 12px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--vermilion);
    color: var(--paper-1);
    font-size: 0.72rem;
    font-weight: 800;
    transform: rotate(-3deg);
    box-shadow: 2px 2px 0 var(--ink);
}

.up-cycle {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    color: var(--text-3);
}

.up-stage {
    position: relative;
    padding: 16px;
    background: var(--paper-2);
    border: 1.5px solid var(--ink);
    border-radius: var(--r-md);
    box-shadow: var(--shadow-card);
    transform: rotate(-1.4deg);
}

.up-aura {
    position: absolute;
    inset: 8px;
    border: 2px dashed rgba(212, 58, 58, 0.55);
    border-radius: var(--r-sm);
    pointer-events: none;
}

.up-img {
    position: relative;
    z-index: 1;
    width: 100%;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 1.5px solid var(--ink);
}

.up-meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.up-meta strong {
    font-family: var(--font-display);
    font-size: 1rem;
    font-weight: 800;
    color: var(--ink);
}

.up-set {
    font-family: var(--font-mono);
    font-size: 0.74rem;
    color: var(--text-3);
}

/* 右侧抽卡操作 */
.draw-side {
    display: flex;
    flex-direction: column;
    gap: 18px;
    min-width: 0;
}

.gacha-note {
    margin: 0;
    padding: 12px 14px;
    border: 1.5px dashed var(--ink-soft);
    border-radius: var(--r-md);
    background: var(--paper-2);
    color: var(--text-2);
    font-size: 0.82rem;
    line-height: 1.65;
}

.gacha-note i {
    color: var(--cobalt);
    margin-right: 4px;
}

.gacha-note strong {
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.draw-actions {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;
    padding: 16px;
    border: 2px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-2);
    box-shadow: 4px 4px 0 var(--ink);
}

.draw-btn {
    font-size: 1rem;
    padding: 14px 28px;
    font-weight: 800;
}

.draw-balance {
    font-size: 0.88rem;
    color: var(--text-2);
}

.draw-balance strong {
    font-family: var(--font-display);
    font-size: 1.2rem;
    color: var(--ink);
    font-variant-numeric: tabular-nums;
}

.draw-poor {
    margin-left: 8px;
    padding: 2px 8px;
    border: 1px solid var(--vermilion);
    border-radius: var(--r-full);
    color: var(--vermilion);
    font-size: 0.7rem;
    font-weight: 700;
}

.recent-draws {
    display: flex;
    flex-direction: column;
    gap: 8px;
}

.recent-label {
    font-family: var(--font-mono);
    font-size: 0.72rem;
    color: var(--text-3);
    letter-spacing: 0.04em;
}

.recent-strip {
    display: flex;
    gap: 10px;
    align-items: center;
    min-height: 88px;
    flex-wrap: wrap;
}

.recent-thumb {
    width: 56px;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 3px solid var(--paper-1);
    outline: 1.5px solid var(--ink);
    box-shadow: 2px 2px 0 rgba(28, 26, 20, 0.85);
    transform: rotate(-2deg);
}

.recent-thumb:nth-child(even) { transform: rotate(2deg); }
.recent-thumb.is-rare { outline: 2px solid var(--vermilion); }

.recent-empty {
    font-size: 0.8rem;
    color: var(--text-3);
    padding: 12px;
    border: 1.5px dashed var(--ink-soft);
    border-radius: var(--r-md);
    flex: 1;
    text-align: center;
}

.hero-level-note {
    margin-top: 8px;
    font-family: var(--font-mono);
    font-size: 0.74rem;
    color: var(--text-3);
}

.gacha-state {
    grid-column: 1 / -1;
    padding: 6px 0 14px;
}

.gacha-error {
    margin: 0 auto;
    max-width: 440px;
}

.up-stage.is-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: 220px;
}

.up-placeholder {
    position: relative;
    z-index: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 34px 12px;
    color: var(--text-3);
    font-size: 0.82rem;
    text-align: center;
}

.up-placeholder i {
    font-size: 1.5rem;
    color: var(--vermilion);
}

.up-badge {
    position: absolute;
    z-index: 2;
    top: 10px;
    left: 10px;
    padding: 3px 10px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--vermilion);
    color: var(--paper-1);
    font-family: var(--font-mono);
    font-size: 0.7rem;
    font-weight: 800;
    letter-spacing: 0.08em;
    transform: rotate(-5deg);
    box-shadow: 2px 2px 0 var(--ink);
}

.up-rotates {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-family: var(--font-mono);
    font-size: 0.68rem;
    color: var(--text-3);
}

/* ========== 收藏室入口（拍立得照片墙） ========== */
.collection-entry {
    display: grid;
    grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
    min-height: 224px;
    overflow: hidden;
}

.entry-collage {
    position: relative;
    display: flex;
    flex-wrap: wrap;
    align-content: center;
    justify-content: center;
    gap: 10px;
    padding: 22px;
    background: var(--paper-2);
    border-right: 1.5px solid var(--ink);
}

.entry-collage img {
    width: 76px;
    aspect-ratio: 3 / 4;
    object-fit: cover;
    border: 4px solid var(--paper-1);
    outline: 1.5px solid var(--ink);
    background: var(--paper-1);
    box-shadow: 3px 3px 0 rgba(28, 26, 20, 0.85);
    transform: rotate(var(--tilt, 0deg));
}

.entry-collage img:nth-child(1) { --tilt: -4deg; }
.entry-collage img:nth-child(2) { --tilt: 3deg; }
.entry-collage img:nth-child(3) { --tilt: -2deg; }
.entry-collage img:nth-child(4) { --tilt: 2.4deg; }
.entry-collage img:nth-child(5) { --tilt: -3deg; }
.entry-collage img:nth-child(6) { --tilt: 1.8deg; }

.entry-copy {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 12px;
    padding: 30px;
    background: var(--paper-1);
}

.entry-title {
    font-size: 1.36rem;
}

.entry-title i {
    color: var(--cobalt);
}

.entry-desc {
    color: var(--text-2);
    font-size: 0.85rem;
}

.entry-desc strong {
    color: var(--ink);
    font-family: var(--font-mono);
}

.entry-progress {
    height: 12px;
    max-width: 420px;
}

.entry-actions {
    display: flex;
    align-items: center;
    gap: 14px;
    flex-wrap: wrap;
    margin-top: 4px;
}

.entry-note {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.74rem;
    color: var(--text-3);
}

.entry-note i {
    color: var(--vermilion);
}

/* ========== 抽卡结果弹窗 ========== */
.draw-modal {
    width: min(440px, 100%);
    padding: 24px 22px 22px;
    border-radius: var(--r-lg);
    background: var(--paper-1);
}

.draw-modal--ten {
    width: min(760px, 100%);
    background:
        radial-gradient(circle at 15% 10%, rgba(240, 178, 122, 0.24), transparent 26%),
        radial-gradient(circle at 88% 82%, rgba(143, 123, 224, 0.2), transparent 28%),
        var(--paper-1);
}

.draw-close {
    position: absolute;
    top: 14px;
    right: 14px;
    z-index: 6;
}

.draw-modal__head {
    position: relative;
    z-index: 2;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-right: 34px;
    margin-bottom: 18px;
}

.draw-modal__title {
    display: flex;
    align-items: center;
    gap: 9px;
    font-family: var(--font-display);
    font-size: 1.3rem;
    font-weight: 800;
    color: var(--ink);
}

.draw-modal__title i {
    color: var(--vermilion);
}

.draw-summary {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
}

.draw-sum-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 3px 9px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-2);
    color: var(--ink);
    font-family: var(--font-mono);
    font-size: 0.68rem;
    font-weight: 700;
}

.draw-sum-chip.is-rare { background: var(--butter); }
.draw-sum-chip.is-up { background: var(--vermilion); color: var(--paper-1); }
.draw-sum-chip.is-new { background: var(--lime); }
.draw-sum-chip.is-dup { border-style: dashed; background: transparent; color: var(--text-3); }

/* ---- 单抽：翻牌 ---- */
.reveal-stage--single {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
}

.reveal-flip {
    width: 240px;
    height: 330px;
    perspective: 1100px;
}

.reveal-flip__inner {
    position: relative;
    width: 100%;
    height: 100%;
    transform-style: preserve-3d;
    transition: transform 0.8s cubic-bezier(0.4, 1.1, 0.5, 1);
}

.reveal-flip.is-flipped .reveal-flip__inner {
    transform: rotateY(180deg);
}

.reveal-flip__face {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    border: 2px solid var(--ink);
    border-radius: var(--r-md);
    background: var(--paper-1);
    box-shadow: var(--shadow-card);
    overflow: hidden;
    backface-visibility: hidden;
    -webkit-backface-visibility: hidden;
}

.reveal-flip__back {
    align-items: center;
    justify-content: center;
    gap: 12px;
    background-color: var(--paper-2);
    background-image: radial-gradient(rgba(28, 26, 20, 0.16) 1.2px, transparent 1.4px);
    background-size: 14px 14px;
}

.reveal-flip__front {
    transform: rotateY(180deg);
}

.reveal-flip__front.is-rare {
    background: var(--butter);
    border-color: var(--vermilion);
}

.reveal-flip__front.is-up {
    box-shadow: 6px 6px 0 var(--vermilion);
}

.back-glyph {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border: 2px solid var(--ink);
    border-radius: 50%;
    background: var(--butter);
    color: var(--ink);
    font-size: 1.5rem;
    box-shadow: 3px 3px 0 var(--ink);
}

.back-text {
    font-family: var(--font-mono);
    font-size: 0.74rem;
    letter-spacing: 0.22em;
    color: var(--text-3);
}

.reveal-photo {
    position: relative;
    flex: 1;
    min-height: 0;
    margin: 10px 10px 0;
    border: 1.5px solid var(--ink);
    border-radius: 4px;
    overflow: hidden;
    background: var(--paper-2);
}

.reveal-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.reveal-empty {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-4);
    font-size: 2rem;
}

.reveal-flag {
    position: absolute;
    top: 8px;
    left: 8px;
    z-index: 2;
    padding: 2px 8px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    font-family: var(--font-mono);
    font-size: 0.64rem;
    font-weight: 800;
    box-shadow: 2px 2px 0 var(--ink);
}

.reveal-flag.is-up {
    background: var(--vermilion);
    color: var(--paper-1);
    transform: rotate(-4deg);
}

.reveal-flag.is-dup {
    background: var(--paper-1);
    color: var(--text-3);
    border-style: dashed;
    box-shadow: none;
}

.reveal-caption {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 5px;
    padding: 12px 14px 16px;
    text-align: center;
}

.reveal-rarity {
    padding: 2px 10px;
    border: 1.5px solid var(--ink);
    border-radius: var(--r-full);
    background: var(--paper-1);
    color: var(--ink);
    font-size: 0.66rem;
    font-weight: 800;
}

.reveal-rarity.is-rare {
    background: var(--vermilion);
    color: var(--paper-1);
}

.reveal-name {
    font-family: var(--font-display);
    font-size: 1.1rem;
    font-weight: 800;
    line-height: 1.3;
    color: var(--ink);
}

.reveal-set {
    font-family: var(--font-mono);
    font-size: 0.68rem;
    color: var(--text-3);
}

.reveal-hint {
    margin: 0;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 0.7rem;
    color: var(--text-3);
}

/* ---- 十连：响应式结果网格 ---- */
.reveal-grid {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    gap: 10px;
}

.reveal-tile {
    min-width: 0;
    padding: 5px;
    border: 1.5px solid var(--line-strong);
    border-radius: var(--r-sm);
    background: var(--paper-1);
    opacity: 0;
    transform: translateY(10px) scale(0.92);
    transition: opacity 0.3s var(--ease-out), transform 0.3s var(--ease-out),
        border-color 0.2s var(--ease-out);
    transition-delay: calc(var(--i) * 45ms);
}

.reveal-grid.is-revealed .reveal-tile {
    opacity: 1;
    transform: none;
}

.reveal-tile.is-rare {
    border: 2px solid var(--vermilion);
    background: #fff6ea;
    box-shadow: 3px 3px 0 var(--vermilion);
}

.reveal-tile.is-up {
    background: #ffe9e0;
}

.reveal-tile__photo {
    position: relative;
    border: 1px solid var(--ink-soft);
    border-radius: 3px;
    overflow: hidden;
    background: var(--paper-2);
}

.reveal-tile__photo img {
    display: block;
    width: 100%;
    aspect-ratio: 3 / 4;
    object-fit: cover;
}

.reveal-tile__name {
    display: block;
    margin-top: 4px;
    font-family: var(--font-display);
    font-size: 0.64rem;
    font-weight: 700;
    color: var(--ink);
    text-align: center;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.reveal-tile.is-rare .reveal-tile__name {
    color: var(--vermilion);
}

.claim-footer {
    position: relative;
    z-index: 2;
    display: flex;
    justify-content: center;
    margin-top: 20px;
}

.claim-btn {
    min-width: 210px;
    height: 50px;
    font-size: 0.96rem;
    font-weight: 800;
}

@keyframes results-pop {
    from { opacity: 0; transform: translateY(16px) scale(0.96); }
    to { opacity: 1; transform: translateY(0) scale(1); }
}

.reveal-grid.is-revealed {
    animation: results-pop 0.45s cubic-bezier(0.2, 1.4, 0.4, 1);
}

@media (max-width: 760px) {
    .reveal-grid {
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .reveal-flip {
        width: 210px;
        height: 300px;
    }
}

@media (max-width: 420px) {
    .draw-modal,
    .draw-modal--ten {
        padding: 20px 14px 18px;
    }

    .reveal-grid {
        gap: 7px;
    }
}

/* ========== 响应式 ========== */
@media (max-width: 1080px) {
    .hero-inner {
        grid-template-columns: minmax(0, 1fr);
        gap: 26px;
    }

    .hero-side {
        border-left: 0;
        padding-left: 0;
        gap: 22px;
    }

    .checkin-layout {
        grid-template-columns: minmax(0, 1fr);
        gap: 22px;
    }

    .mini-calendar {
        max-width: 420px;
    }
}

@media (max-width: 760px) {
    .gacha-body {
        grid-template-columns: minmax(0, 1fr);
        gap: 22px;
    }
    .up-side {
        align-items: center;
        text-align: center;
    }
    .up-stage {
        width: 60%;
        max-width: 230px;
    }
}

@media (max-width: 760px) {
    .tasks-head {
        align-items: flex-start;
    }

    .tasks-tabs {
        width: 100%;
    }

    .tasks-tabs .seg__btn {
        flex: 1;
        justify-content: center;
    }

    .hero {
        padding: 22px 18px;
    }

    .hero-number {
        font-size: clamp(3rem, 16vw, 4.2rem);
        text-shadow: 4px 4px 0 var(--butter);
    }

    .hero-side {
        flex-direction: column;
        align-items: flex-start;
        gap: 18px;
    }

    .ring-wrap {
        width: 126px;
        height: 126px;
    }

    .hero-metrics {
        display: grid;
        grid-template-columns: repeat(3, minmax(0, 1fr));
        gap: 10px;
        width: 100%;
    }

    .hero-metrics li {
        flex-direction: column;
        align-items: flex-start;
        gap: 6px;
    }

    .hero-collage {
        gap: 10px;
        margin-top: 20px;
        padding-top: 14px;
    }

    .hero-collage img {
        width: 64px;
    }

    .checkin-facts {
        grid-template-columns: repeat(3, minmax(0, 1fr));
    }

    .fact {
        padding: 10px 12px;
    }

    .checkin-cta {
        width: 100%;
    }

    .track-bar {
        align-items: flex-start;
        flex-direction: column;
    }

    .collection-entry {
        grid-template-columns: minmax(0, 1fr);
    }

    .entry-collage {
        border-right: 0;
        border-bottom: 1.5px solid var(--ink);
        padding: 18px;
        gap: 8px;
    }

    .entry-collage img {
        width: 64px;
    }

    .entry-copy {
        padding: 22px 18px;
    }

    .claim-scene {
        width: 220px;
        height: 312px;
    }
}

@media (max-width: 400px) {
    .checkin-facts {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }

    .hero-metrics {
        grid-template-columns: repeat(2, minmax(0, 1fr));
    }
}
</style>
