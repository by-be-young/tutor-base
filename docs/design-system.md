# NEST · 学巢 — 前端设计契约

> 本文件是全站视觉重做的唯一协作契约。先读 `tutor-nest-vue/src/assets/theme.css`，再动任何页面。

## 1. 设计方向（2026-10 修订）

**Riso Campus · 印刷学院风**：奶油纸张 + 墨黑文字 + 高饱和印刷色块 + 硬边框与错位阴影。
关键词：纸感、印刷、拼贴、留白、手作、学院杂志。

**已明确否决**：黑紫底、霓虹发光、毛玻璃、AI 感渐变光晕、深色赛博界面。

- **画布**：`--paper-0` 奶油纸 `#F3ECDD`；卡片 `--paper-1` 纸白 `#FFFDF6`；次级面 `--paper-2`。
- **文字/线条**：`--ink` 墨黑 `#1C1A14`；正文 `--text-2`；弱化 `--text-3`。
- **印刷色**：朱红 `--vermilion #ED4B2A`、钴蓝 `--cobalt #1D4ED8`、黄绿 `--lime #C9EF4A`、奶油黄 `--butter #FFC53D`、天蓝 `--sky #7CC5F0`、粉 `--pink #FF9EBB`、墨绿 `--teal #0F8F7C`。
- **卡片**：纸白底 + `1.5px` 墨线边框 + `3~5px` 错位硬阴影（`--shadow-card`），圆角 12~22px。
- **按钮**：平涂色块 + 墨线 + 硬阴影；hover 位移 `-1px`，active 下沉。
- **图片**：站内资源。用「拍立得」处理：纸白相框、墨线、轻微旋转、硬阴影；禁止外链。
- **反白区**：每页最多一个 `--ink` 底色块（如首页收藏卡池、页脚）。
- **动效**：入场、数字滚动、hover 位移、旋转贴纸；不要发光/呼吸光晕。
- **文本**：短、准、有情绪。不要解释性长段落。

**禁止清单**：`backdrop-filter` / 毛玻璃、`box-shadow: 0 0 Npx 彩色` 发光、紫/青霓虹渐变、`#0b1022` 类深色底、`rgba(255,255,255,...)` 半透明白（反白区除外）、外链图片。

## 2. 样式来源

**只使用 `theme.css` 里的 token 与组件类。** 页面可以写 `<style scoped>`，但只能写布局/页面特有形状；颜色、边框、圆角、字体、阴影、动效曲线必须引用 token。

| 用途 | 类 |
| --- | --- |
| 页面容器 / 页头 | `.page` `.page--wide` `.page--narrow` `.page-head` `.page-head--row` `.eyebrow` `.page-title` `.page-sub` |
| 面板 | `.panel` `.panel--pad` `.panel--tight` `.panel--glow`（右上角朱红点） `.panel--flat` `.panel-head` `.panel-title` `.panel-body` `.tile` `.icon-tile` |
| 按钮 | `.btn` + `.btn--primary`（朱红）`.btn--warm`（奶油黄）`.btn--soft`（黄绿）`.btn--cyan`（天蓝）`.btn--ghost` `.btn--danger` `.btn--sm` `.btn--lg` `.btn--icon` `.btn--block` |
| 标签 | `.chip` + `.chip--violet/cyan/lime/amber/coral/pink`、`.badge`、`.tag`、`.kbd`、`.status-dot` |
| 统计 | `.stat-grid` `.stat-card` `.stat-card--cyan/lime/coral/amber`（整块色卡）`.stat-value` `.stat-label` `.stat-sub` |
| 表单 | `.field` `.field-label` `.input` `.textarea` `.select` `.input-wrap` `.field-hint` `.field-error` `.form-grid` |
| 分段控件 | `.seg` `.seg__btn` `.seg__btn.is-active`（墨黑底）`.seg__count` |
| 进度 | `.progress`（墨线框）`.progress__bar` `.progress--cyan/warm/pink` |
| 空/加载 | `.empty` `.empty__icon` `.empty__title` `.empty__text` `.empty__actions` `.spinner` `.loading-line` `.skeleton` |
| 弹窗 | `.backdrop` `.modal` `.modal__head` `.modal__title` `.modal__body` `.modal__foot` `.modal__close` |
| 文本 | `.text-gradient`（黄绿荧光笔）`.text-gradient--warm` `.text-gradient--pink` `.text-muted` `.text-dim` `.text-sm` `.mono` `.eyebrow` |
| 装饰 | `.paper-decor`（全局已挂载）、`.marquee` `.marquee__track`、`.tilt`、`.avatar`、`.grid--2/3/4` `.stack` `.row` |

需要新 token 时：先在汇报里说明，不要私自改 `theme.css`（由 Lead 维护）。

## 3. 公共组件（可直接使用）

- `@/components/ui/RevealOnScroll.vue`：`<RevealOnScroll :delay="0.1" direction="up">…</RevealOnScroll>`。
- `@/components/ui/CountUp.vue`：`:value="123"`，视口内数字滚动。
- `@/components/ui/EmptyState.vue`：`icon` `title` `text` + 默认插槽（操作按钮）。
- `@/components/common/BrandMark.vue`：品牌标记，可 `size`。
- `@/stores/uiStore.js`：`openPalette()`、`requestAuth('login'|'register')`、`mobileMenuOpen`。
- `@/utils/toast.js`：`showToast(message, 'success'|'error'|'info'|'warn')`。**页面内不要再自建 toast DOM 与样式。**

## 4. 硬约束（不可破坏）

1. **业务逻辑不动**：gateway、store、router、鉴权、提交/批阅/积分/权限行为保持不变。只做展示层重构与不改变契约的 UX 增强。
2. **E2E 契约**（`tutor-nest-vue/e2e/administrator-learner-journey.spec.js`）：
   - 登录表单保留 `placeholder="用户名"` / `placeholder="密码"`，提交按钮可访问名必须精确为 `登录`。
   - 管理页保留：文本 `管理员`、按钮 `/新增/`、`option` 文本为学员名、按钮 `/学生账户/`、`listitem` 行内按钮 `/设置\/重置密码/`、`#student-new-password`、`#student-confirm-password`、按钮 `确认设置`、`role="status"` 文本含学员名、按钮 `/进入账号/`。
   - 任务页保留：`heading` 含 `任务中心`、文本 `当前积分`、按钮 `/去签到/`、签到弹层类 `.checkin-panel`、`heading` 含 `每日签到`、按钮 `/签到 \+100 积分/`、签到后按钮 `/今日已签到/` 置灰、页面出现精确文本 `100`。
3. **不要修改**：`theme.css`、`App.vue`、`components/common/*`、`components/ui/*`、`stores/uiStore.js`、`utils/toast.js`、`index.html`、`main.js`、`HomeView.vue`、`router/*`、`gateways/*`、`stores/*`。这些由 Lead 或其他队友负责。
4. **构建验证**：不要运行 `npm run build`（会触发 prebuild 同步）。用：
   ```powershell
   node scripts/check-sfc.mjs            # 全量 SFC/模板编译检查
   node scripts/check-sfc.mjs --file tutor-nest-vue/src/views/X.vue
   ```
5. **移动端**：375 / 768 / 1440 三档都要成立；触控目标 ≥ 40px；底部给 `MobileDock` 留出至少 88px（`.page` 已含 120px 底部内边距）。
6. **图片**：只用站内资源；`<img>` 必须有 `alt`、`loading="lazy"`（首屏除外）、失败时用渐变兜底。图片路径使用 `import.meta.env.BASE_URL` 或站内相对路径，禁止写死 `/cards/...`。

## 5. 页面结构建议

```vue
<template>
  <div class="page">
    <header class="page-head">
      <span class="eyebrow">NEST / 模块名</span>
      <h1 class="page-title">标题</h1>
      <p class="page-sub">一句短说明</p>
    </header>

    <div class="stat-grid">…</div>

    <section class="section">…</section>
  </div>
</template>
```

数据未就绪时给 `.skeleton` / `.loading-line`；无数据时给 `.empty`（配站内图片或 `.empty__icon`），不要只留空白。
错误提示用 `showToast`，表单内联错误用 `.field-error`。

## 6. 交付标准

- 视觉：与首页一致的纸感印刷语言；每个页面至少一个"眼前一亮"的视觉锚点（色块海报、拍立得图片墙、印章式时间线、翻页卡片等）。
- 交互：hover / focus / active / disabled 四态齐全；异步操作有 loading 与成功/失败反馈；危险操作有确认。
- 代码：无新增全局 CSS；无重复 toast 实现；无 console 报错；SFC 检查通过。
- 汇报：改了哪些文件、保留/新增了哪些功能、风险点、SFC 检查结果。
