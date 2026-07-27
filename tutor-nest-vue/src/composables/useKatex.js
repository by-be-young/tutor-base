// src/composables/useKatex.js
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

const KATEX_CSS = 'https://cdn.bootcdn.net/ajax/libs/KaTeX/0.16.9/katex.min.css'
const KATEX_JS = 'https://cdn.bootcdn.net/ajax/libs/KaTeX/0.16.9/katex.min.js'
const AUTORENDER_JS = 'https://cdn.bootcdn.net/ajax/libs/KaTeX/0.16.9/contrib/auto-render.min.js'
const RENDER_TIMEOUT = 5000

let isLoaded = false
let loadPromise = null

// 预连接 CDN
function preconnect(url) {
    const link = document.createElement('link')
    link.rel = 'preconnect'
    link.href = url
    link.crossOrigin = 'anonymous'
    document.head.appendChild(link)
}

// 加载 CSS
function loadCSS(href) {
    if (document.querySelector(`link[href="${href}"]`)) return
    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = href
    document.head.appendChild(link)
}

// 加载 Script
function loadScript(src) {
    return new Promise((resolve, reject) => {
        const exist = document.querySelector(`script[src="${src}"]`)
        if (exist) {
            if (exist.dataset.loaded === 'true') return resolve()
            exist.addEventListener('load', resolve, { once: true })
            exist.addEventListener('error', reject, { once: true })
            return
        }
        const s = document.createElement('script')
        s.src = src
        s.async = true
        s.onload = () => {
            s.dataset.loaded = 'true'
            resolve()
        }
        s.onerror = reject
        document.head.appendChild(s)
    })
}

// 加载 KaTeX 库
function loadKatex() {
    if (isLoaded) return Promise.resolve(window.katex)
    if (loadPromise) return loadPromise

    loadCSS(KATEX_CSS)
    loadPromise = loadScript(KATEX_JS)
        .then(() => loadScript(AUTORENDER_JS))
        .then(() => {
            isLoaded = true
            return window.katex
        })
        .catch((e) => {
            loadPromise = null
            throw e
        })

    return loadPromise
}

// 预处理文本节点
function preprocessTextNodes(root) {
    const walker = document.createTreeWalker(
        root,
        NodeFilter.SHOW_TEXT,
        {
            acceptNode: function (node) {
                let parent = node.parentElement
                while (parent && parent !== root) {
                    const tag = parent.tagName.toLowerCase()
                    if (
                        ['code', 'pre', 'script', 'style', 'textarea'].includes(tag) ||
                        parent.classList.contains('katex')
                    ) {
                        return NodeFilter.FILTER_REJECT
                    }
                    parent = parent.parentElement
                }
                return NodeFilter.FILTER_ACCEPT
            }
        }
    )

    let node
    while ((node = walker.nextNode())) {
        node.textContent = node.textContent.replace(/\\cdotp/g, '\\cdot')
    }
}

// 强制换行处理
function forceBreakAllKatex(container) {
    if (!container) return

    // 所有 katex 相关元素
    container.querySelectorAll('[class*="katex"]').forEach((el) => {
        el.style.setProperty('white-space', 'normal', 'important')
        el.style.setProperty('word-break', 'break-all', 'important')
        el.style.setProperty('overflow-wrap', 'anywhere', 'important')
    })

    // 块级公式
    container.querySelectorAll('.katex-display').forEach((disp) => {
        disp.style.setProperty('display', 'block', 'important')
        disp.style.setProperty('max-width', '100%', 'important')
        disp.style.setProperty('overflow', 'visible', 'important')
    })

    // .base 必须变成块
    container.querySelectorAll('.katex-display .base').forEach((base) => {
        base.style.setProperty('display', 'block', 'important')
        base.style.setProperty('width', '100%', 'important')
        base.style.setProperty('white-space', 'normal', 'important')
        base.style.setProperty('word-break', 'break-all', 'important')
        base.style.setProperty('overflow-wrap', 'anywhere', 'important')
    })

    // 向上修正父容器 nowrap
    const parents = new Set()
    container.querySelectorAll('.katex-display, .katex').forEach((formula) => {
        let p = formula.parentElement
        while (p && p !== container && !p.classList.contains('detail-body')) {
            if (
                [
                    'P', 'LI', 'DIV', 'TD', 'TH', 'BLOCKQUOTE',
                    'H1', 'H2', 'H3', 'H4', 'H5', 'H6', 'SECTION'
                ].includes(p.tagName)
            ) {
                parents.add(p)
            }
            p = p.parentElement
        }
    })
    parents.forEach((p) => {
        p.style.setProperty('white-space', 'normal', 'important')
        p.style.setProperty('word-break', 'break-all', 'important')
        p.style.setProperty('overflow-wrap', 'anywhere', 'important')
        p.style.setProperty('max-width', '100%', 'important')
    })

    // 容器溢出
    const body = container.closest('.detail-body') || container
    body.style.setProperty('overflow', 'visible', 'important')
    body.style.setProperty('overflow-x', 'visible', 'important')
}

// 核心渲染函数
async function renderMathInContainer(container) {
    if (!container) return

    await loadKatex()
    if (!window.renderMathInElement) return

    // 预处理文本节点
    preprocessTextNodes(container)

    const originalHTML = container.innerHTML
    let timedOut = false

    const timer = setTimeout(() => {
        timedOut = true
        container.innerHTML = originalHTML.replace(/\$/g, '')
        container.querySelectorAll('.katex,.katex-display').forEach((e) => e.remove())
    }, RENDER_TIMEOUT)

    try {
        window.renderMathInElement(container, {
            delimiters: [
                { left: '$$', right: '$$', display: true },
                { left: '$', right: '$', display: false },
                { left: '\\(', right: '\\)', display: false },
                { left: '\\[', right: '\\]', display: true }
            ],
            throwOnError: false,
            ignoredClasses: ['question-slot'],
            strict: false
        })
        clearTimeout(timer)
        if (timedOut) throw new Error('Render timed out')
    } catch (e) {
        container.innerHTML = originalHTML.replace(/\$/g, '')
        return
    }

    // 强制换行
    forceBreakAllKatex(container)
}

// Vue Composable
export function useKatex() {
    const isReady = ref(false)
    let resizeTimer = null

    // 初始化 KaTeX
    async function initKatex() {
        try {
            // 预连接 CDN
            preconnect('https://cdn.bootcdn.net')

            await loadKatex()
            isReady.value = true
            console.log('KaTeX 加载成功')
        } catch (error) {
            console.error('KaTeX 加载失败:', error)
        }
    }

    // 渲染容器中的数学公式
    async function renderMath(container) {
        if (!container) return

        try {
            await renderMathInContainer(container)
        } catch (error) {
            console.error('KaTeX 渲染失败:', error)
            // 移除所有公式标记
            if (container.innerHTML) {
                container.innerHTML = container.innerHTML.replace(/\$/g, '')
            }
        }
    }

    // 监听窗口大小变化，重新应用换行样式
    function handleResize() {
        clearTimeout(resizeTimer)
        resizeTimer = setTimeout(() => {
            document.querySelectorAll('.detail-body').forEach(forceBreakAllKatex)
        }, 200)
    }

    // 组件挂载时初始化
    onMounted(() => {
        initKatex()
        window.addEventListener('resize', handleResize)
    })

    // 组件卸载时清理
    onUnmounted(() => {
        window.removeEventListener('resize', handleResize)
        clearTimeout(resizeTimer)
    })

    return {
        isReady,
        renderMath,
        forceBreakAllKatex
    }
}