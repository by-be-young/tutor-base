// src/composables/useImageEmbed.js
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

// ================================================================
// 一、配置（可响应式修改）
// ================================================================

/** 图片存储的基础路径 */
const IMAGE_BASE_PATH = ref('blogs/图片/')

/** 是否启用懒加载（默认关闭：图片数量有限，懒加载在 Edge 下会因
 *  opacity:0 被判定不可见而延迟 load 事件，导致图片永不显示） */
const ENABLE_LAZY_LOAD = ref(false)

/** 图片加载失败时的占位图（可选） */
const PLACEHOLDER_IMAGE = ref(null)

// ================================================================
// 二、核心解析函数
// ================================================================

/**
 * 解析图片嵌入语法 ![[filename|options]]
 * 
 * @param {string} text - 包含图片语法的文本
 * @returns {string} 替换为 HTML img 标签后的文本
 */
function parseImageEmbeds(text) {
    if (!text || typeof text !== 'string') {
        return text || ''
    }

    const regex = /!\[\[([^\]]+)\]\]/g

    return text.replace(regex, function (match, content) {
        const parsed = parseImageOptions(content)
        return generateImageHTML(parsed)
    })
}

/**
 * 解析图片选项字符串
 * 
 * @param {string} content - 方括号内的内容
 * @returns {Object} 解析后的选项对象
 */
function parseImageOptions(content) {
    const parts = content.split('|').map(s => s.trim()).filter(s => s !== '')

    const result = {
        filename: parts[0] || '',
        width: null,
        height: null,
        align: null,
        title: null,
        caption: null,
        lazy: ENABLE_LAZY_LOAD.value,
        responsive: true,
        rounded: false,
        shadow: false
    }

    if (parts.length === 0) {
        return result
    }

    result.filename = parts[0]

    for (let i = 1; i < parts.length; i++) {
        const option = parts[i]

        // 检查是否为尺寸：数字x数字
        const sizeMatch = option.match(/^(\d*)(?:x(\d+))?$/i)
        if (sizeMatch && (sizeMatch[1] || sizeMatch[2])) {
            result.width = sizeMatch[1] || null
            result.height = sizeMatch[2] || null
            if (result.width && !result.height) {
                result.height = result.width
            }
            if (!result.width && result.height) {
                result.width = null
            }
            continue
        }

        // 检查是否为对齐方式
        const alignMatch = option.match(/^(left|center|right|inline)$/i)
        if (alignMatch) {
            result.align = alignMatch[1].toLowerCase()
            continue
        }

        // 检查是否为布尔标志
        const boolMatch = option.match(/^(lazy|responsive|rounded|shadow)$/i)
        if (boolMatch) {
            const key = boolMatch[1].toLowerCase()
            result[key] = true
            continue
        }

        // 其他内容视为标题/图注
        if (!result.title) {
            result.title = option
        } else {
            result.caption = option
        }
    }

    return result
}

/**
 * 生成图片 HTML
 */
function generateImageHTML(options) {
    const {
        filename = '',
        width = null,
        height = null,
        align = null,
        title = '',
        caption = '',
        lazy = true,
        responsive = true,
        rounded = false,
        shadow = false
    } = options

    if (!filename) {
        return ''
    }

    const imagePath = IMAGE_BASE_PATH.value + filename

    // 构建属性数组
    let attributes = []

    attributes.push(`src="${escapeHtml(imagePath)}"`)

    const altText = title || filename.replace(/\.[^.]+$/, '')
    attributes.push(`alt="${escapeHtml(altText)}"`)

    if (title) {
        attributes.push(`title="${escapeHtml(title)}"`)
    }

    if (width && width !== 'auto') {
        attributes.push(`width="${escapeHtml(width)}"`)
    }

    if (height && height !== 'auto') {
        attributes.push(`height="${escapeHtml(height)}"`)
    }

    if (lazy) {
        attributes.push('loading="lazy"')
    }

    // 错误处理
    attributes.push(`onerror="this.style.display='none'"`)

    // 收集样式
    const styles = []

    if (responsive) {
        styles.push('max-width: 100%')
        styles.push('height: auto')
    }

    if (rounded) {
        styles.push('border-radius: 8px')
    }

    if (shadow) {
        styles.push('box-shadow: 0 2px 8px rgba(0,0,0,0.15)')
    }

    if (styles.length > 0) {
        attributes.push(`style="${styles.join('; ')}"`)
    }

    // 对齐类名
    let alignClass = ''
    if (align && align !== 'inline') {
        alignClass = `image-align-${align}`
    }

    // 构建 HTML
    let html = ''

    if (caption) {
        html += `<figure class="image-figure ${alignClass}">`
        html += `<img ${attributes.join(' ')} />`
        html += `<figcaption class="image-caption">${escapeHtml(caption)}</figcaption>`
        html += `</figure>`
    } else {
        html += `<span class="image-wrapper ${alignClass}">`
        html += `<img ${attributes.join(' ')} />`
        html += `</span>`
    }

    return html
}

// ================================================================
// 三、工具函数
// ================================================================

/**
 * HTML 转义，防止 XSS 攻击
 */
function escapeHtml(text) {
    if (!text) return ''
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    }
    return String(text).replace(/[&<>"']/g, function (m) {
        return map[m]
    })
}

// ================================================================
// 四、DOM 操作函数
// ================================================================

/**
 * 在 DOM 元素中解析图片嵌入
 */
function parseImageEmbedsInElement(element) {
    if (!element) {
        console.warn('parseImageEmbedsInElement: 元素不存在')
        return
    }

    function walkTextNodes(node) {
        if (node.nodeType === Node.TEXT_NODE) {
            if (node.textContent.includes('![[')) {
                const parent = node.parentNode
                const processed = parseImageEmbeds(node.textContent)

                if (processed !== node.textContent) {
                    const temp = document.createElement('div')
                    temp.innerHTML = processed

                    while (temp.firstChild) {
                        parent.insertBefore(temp.firstChild, node)
                    }
                    parent.removeChild(node)
                }
            }
            return
        }

        if (node.nodeType === Node.ELEMENT_NODE) {
            const skipTags = ['code', 'pre', 'script', 'style', 'img', 'figure', 'textarea']
            const tagName = node.tagName.toLowerCase()
            if (skipTags.includes(tagName)) {
                return
            }

            const children = Array.from(node.childNodes)
            children.forEach(child => walkTextNodes(child))
        }
    }

    walkTextNodes(element)
}

// ================================================================
// 五、样式注入
// ================================================================

let stylesInjected = false

function injectImageStyles() {
    if (stylesInjected) return

    const styleId = 'image-embed-styles'
    if (document.getElementById(styleId)) {
        stylesInjected = true
        return
    }

    const styles = `
    .image-wrapper {
      display: inline-block;
      margin: 0.5em 0;
      max-width: 100%;
    }
    
    .image-align-left {
      float: left;
      margin-right: 1em;
      margin-bottom: 0.5em;
      max-width: 50%;
    }
    
    .image-align-right {
      float: right;
      margin-left: 1em;
      margin-bottom: 0.5em;
      max-width: 50%;
    }
    
    .image-align-center {
      display: block;
      text-align: center;
      margin-left: auto;
      margin-right: auto;
      max-width: 80%;
    }
    
    .image-figure {
      display: block;
      margin: 1em 0;
      text-align: center;
    }
    
    .image-figure.image-align-left {
      float: left;
      margin-right: 1em;
      max-width: 50%;
    }
    
    .image-figure.image-align-right {
      float: right;
      margin-left: 1em;
      max-width: 50%;
    }
    
    .image-figure img {
      display: block;
      margin: 0 auto;
      max-width: 100%;
      height: auto;
    }
    
    .image-caption {
      display: block;
      margin-top: 0.5em;
      font-size: 0.9em;
      color: #666;
      text-align: center;
    }
    
    .image-wrapper img,
    .image-figure img {
      max-width: 100%;
      height: auto;
      border-radius: 4px;
      transition: opacity 0.3s ease;
    }
    
    .image-wrapper img[loading="lazy"],
    .image-figure img[loading="lazy"] {
      opacity: 0;
    }
    
    .image-wrapper img[loading="lazy"].loaded,
    .image-figure img[loading="lazy"].loaded {
      opacity: 1;
    }
    
    @media (max-width: 640px) {
      .image-align-left,
      .image-align-right,
      .image-figure.image-align-left,
      .image-figure.image-align-right {
        float: none;
        display: block;
        margin-left: auto;
        margin-right: auto;
        max-width: 80%;
      }
    }
  `

    const styleEl = document.createElement('style')
    styleEl.id = styleId
    styleEl.textContent = styles
    document.head.appendChild(styleEl)
    stylesInjected = true
}

// ================================================================
// 六、Vue Composable
// ================================================================

export function useImageEmbed() {
    const isReady = ref(false)
    let observer = null
    let observerTarget = null

    /**
     * 设置图片基础路径
     */
    function setBasePath(path) {
        IMAGE_BASE_PATH.value = path
    }

    /**
     * 设置是否启用懒加载
     */
    function setLazyLoad(enabled) {
        ENABLE_LAZY_LOAD.value = enabled
    }

    /**
     * 处理 Markdown 文本中的图片嵌入
     */
    function processMarkdown(text) {
        return parseImageEmbeds(text)
    }

    /**
     * 处理 DOM 元素中的图片嵌入
     */
    function processElement(element) {
        if (!element) return
        parseImageEmbedsInElement(element)
    }

    /**
     * 开始观察目标元素
     */
    function observe(target) {
        // 停止之前的观察
        disconnect()

        if (!target) {
            target = document.querySelector('.detail-body')
            if (!target) {
                console.warn('未找到 .detail-body 元素')
                return
            }
        }

        observerTarget = target

        // 如果已有内容，立即解析
        if (target.innerHTML.trim() !== '') {
            parseImageEmbedsInElement(target)
            // 添加图片加载事件监听
            addImageLoadListeners(target)
        }

        // 创建观察器
        observer = new MutationObserver(() => {
            if (observer._processing) return
            observer._processing = true

            requestAnimationFrame(() => {
                parseImageEmbedsInElement(target)
                addImageLoadListeners(target)
                observer._processing = false
            })
        })

        observer.observe(target, {
            childList: true,
            subtree: true,
            characterData: true
        })
    }

    /**
     * 停止观察
     */
    function disconnect() {
        if (observer) {
            observer.disconnect()
            observer = null
        }
        observerTarget = null
    }

    /**
     * 为图片添加加载事件
     */
    function addImageLoadListeners(container) {
        if (!container) return

        container.querySelectorAll('img[loading="lazy"]').forEach(img => {
            if (img.dataset.listenerAdded === 'true') return
            img.dataset.listenerAdded = 'true'

            img.addEventListener('load', () => {
                img.classList.add('loaded')
            })

            // 若图片已加载完成（缓存/已触发过 load），立即显示，避免永久透明占位
            if (img.complete && img.naturalWidth > 0) {
                img.classList.add('loaded')
            } else if (!img.complete) {
                // lazy 图片尚未加载：延迟轮询几次，加载完成即显示（避免监听错过 load 事件）
                let tries = 0
                const poll = setInterval(() => {
                    if (img.complete) {
                        clearInterval(poll)
                        if (img.naturalWidth > 0) img.classList.add('loaded')
                    } else if (++tries > 20) {
                        clearInterval(poll)
                    }
                }, 300)
            }

            img.addEventListener('error', () => {
                img.style.display = 'none'
                const placeholder = PLACEHOLDER_IMAGE.value
                if (placeholder) {
                    const parent = img.parentElement
                    if (parent) {
                        const placeholderEl = document.createElement('span')
                        placeholderEl.className = 'image-placeholder'
                        placeholderEl.textContent = '图片加载失败'
                        placeholderEl.style.cssText = `
              display: inline-block;
              padding: 20px;
              background: #f0f0f0;
              color: #999;
              border-radius: 4px;
              font-size: 0.9em;
            `
                        parent.appendChild(placeholderEl)
                    }
                }
            })
        })
    }

    // 组件挂载时初始化
    onMounted(() => {
        injectImageStyles()
        isReady.value = true
    })

    // 组件卸载时清理
    onUnmounted(() => {
        disconnect()
    })

    return {
        isReady,
        IMAGE_BASE_PATH,
        setBasePath,
        setLazyLoad,
        processMarkdown,
        processElement,
        observe,
        disconnect
    }
}