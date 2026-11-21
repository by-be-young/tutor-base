/**
 * 课件 PDF 导出（客户端生成，不依赖 window.print）。
 *
 * 桌面端仍走浏览器打印；手机浏览器（QQ 浏览器、微信、钉钉等 WebView）
 * 普遍没有可用的打印能力，这里用 html2canvas 把正文栅格化后交给 jsPDF
 * 生成真正的 PDF 文件，再由用户点击保存。
 */

const A4_WIDTH_MM = 210
const A4_HEIGHT_MM = 297
const PAGE_MARGIN_MM = 14
const CONTENT_WIDTH_MM = A4_WIDTH_MM - PAGE_MARGIN_MM * 2
const PAGE_CONTENT_HEIGHT_MM = A4_HEIGHT_MM - PAGE_MARGIN_MM * 2

/** 导出版面固定宽度；682px 映射到 A4 的 182mm 内容宽度 */
export const PDF_RENDER_WIDTH = 682

const MAX_CANVAS_EDGE = 30000
const MAX_CANVAS_PIXELS = 50_000_000
const MAX_CONTENT_HEIGHT = 26000

/** 手机 / 平板浏览器：优先直接生成 PDF 文件 */
export function prefersClientPdf() {
    if (typeof window === 'undefined') return false
    const ua = navigator.userAgent || ''
    const isIOS = /iPad|iPhone|iPod/i.test(ua)
        || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)
    const isTouchTablet = (navigator.maxTouchPoints || 0) > 1
        && window.matchMedia?.('(pointer: coarse)')?.matches
    return isIOS || isTouchTablet
        || /Android|HarmonyOS|Mobile|MicroMessenger|QQBrowser|DingTalk/i.test(ua)
}

/** 浏览器是否提供可用的打印能力 */
export function canUseBrowserPrint() {
    return typeof window !== 'undefined' && typeof window.print === 'function'
}

export function buildPdfFilename(title) {
    const base = String(title || '课件')
        .replace(/[\\/:*?"<>|\n\r]/g, '-')
        .replace(/\s+/g, ' ')
        .replace(/^[\s.-]+|[\s.-]+$/g, '')
        .slice(0, 80)
    return `${base || '课件'}.pdf`
}

export function formatFileSize(bytes) {
    if (!bytes || bytes < 0) return '0 KB'
    if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
    return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function delay(ms) {
    return new Promise(resolve => window.setTimeout(resolve, ms))
}

async function waitForAssets(element) {
    const fontTask = document.fonts?.ready || Promise.resolve()
    const imageTasks = Array.from(element.querySelectorAll('img')).map(img => {
        if (img.complete) return Promise.resolve()
        return new Promise(resolve => {
            img.addEventListener('load', resolve, { once: true })
            img.addEventListener('error', resolve, { once: true })
            window.setTimeout(resolve, 15000)
        })
    })
    await Promise.race([Promise.all([fontTask, ...imageTasks]), delay(3000)])
    await delay(30)
}

function scanWhiteRows(canvas) {
    const sampleWidth = 320
    const sampleHeight = Math.max(1, Math.ceil(canvas.height / 4))
    const sample = document.createElement('canvas')
    sample.width = sampleWidth
    sample.height = sampleHeight
    const ctx = sample.getContext('2d')
    ctx.drawImage(canvas, 0, 0, canvas.width, canvas.height, 0, 0, sampleWidth, sampleHeight)
    const pixels = ctx.getImageData(0, 0, sampleWidth, sampleHeight).data
    const rows = new Uint8Array(sampleHeight)
    for (let y = 0; y < sampleHeight; y++) {
        let white = 1
        for (let x = 0; x < sampleWidth; x++) {
            const offset = (y * sampleWidth + x) * 4
            if (pixels[offset + 3] > 8 && (pixels[offset] < 246 || pixels[offset + 1] < 246 || pixels[offset + 2] < 246)) {
                white = 0
                break
            }
        }
        rows[y] = white
    }
    sample.width = 0
    sample.height = 0
    return { rows, ratio: canvas.height / sampleHeight }
}

function collectProtectedRanges(element, scale) {
    const rootRect = element.getBoundingClientRect()
    const ranges = []
    element.querySelectorAll('img, table, pre, blockquote, .katex-display, h1, h2, h3, h4').forEach(node => {
        const rect = node.getBoundingClientRect()
        if (rect.height < 12 || rect.width < 12) return
        const isHeading = /^H[1-4]$/.test(node.tagName)
        ranges.push({
            top: (rect.top - rootRect.top) * scale,
            bottom: (rect.bottom - rootRect.top) * scale + (isHeading ? 32 * scale : 0)
        })
    })
    return ranges.sort((a, b) => a.top - b.top)
}

function buildPageSlices(canvasHeight, pageHeight, whitespace, protectedRanges = []) {
    const { rows, ratio } = whitespace
    const slices = []
    let start = 0
    while (start < canvasHeight - 2) {
        const target = start + pageHeight
        let end = Math.min(target, canvasHeight)
        if (target < canvasHeight) {
            const blocked = protectedRanges.find(range => target > range.top && target < range.bottom)
            if (blocked && blocked.top > start + 120) {
                end = Math.floor(blocked.top) - 6
            } else {
                const fromRow = Math.max(0, Math.floor((start + pageHeight * 0.5) / ratio))
                const toRow = Math.min(rows.length - 1, Math.floor(target / ratio))
                for (let row = toRow; row >= fromRow; row--) {
                    if (!rows[row]) continue
                    const y = Math.floor((row + 1) * ratio)
                    const inProtected = protectedRanges.some(range => y > range.top - 6 && y < range.bottom + 6)
                    if (!inProtected) {
                        end = y
                        break
                    }
                }
            }
        }
        if (end - start < 24) end = Math.min(start + pageHeight, canvasHeight)
        slices.push({ y: start, height: end - start })
        start = end
    }
    return slices
}

function cropCanvas(source, slice) {
    const canvas = document.createElement('canvas')
    canvas.width = source.width
    canvas.height = Math.ceil(slice.height)
    const ctx = canvas.getContext('2d')
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(
        source,
        0, slice.y, source.width, slice.height,
        0, 0, source.width, canvas.height
    )
    return canvas
}

function createError(code, message) {
    const error = new Error(message)
    error.code = code
    return error
}

/**
 * 把已经准备好的 DOM 节点渲染成 PDF。
 * @param {{ element: HTMLElement, title?: string, onProgress?: (info: { phase: string, page?: number, total?: number }) => void }} options
 * @returns {Promise<{ blob: Blob, filename: string, pages: number, bytes: number }>}
 */
export async function createCoursewarePdf({ element, title, onProgress } = {}) {
    if (!element) throw createError('missing_element', '没有可导出的正文')

    const [{ default: html2canvas }, { jsPDF }] = await Promise.all([
        import('html2canvas'),
        import('jspdf')
    ])

    onProgress?.({ phase: 'assets' })
    await waitForAssets(element)

    const cssWidth = Math.ceil(element.getBoundingClientRect().width) || PDF_RENDER_WIDTH
    const cssHeight = Math.ceil(element.scrollHeight)
    if (cssHeight < 10) throw createError('empty_content', '正文为空，无法导出')
    if (cssHeight > MAX_CONTENT_HEIGHT) {
        throw createError('content_too_long', '这篇内容过长，手机端无法一次生成 PDF')
    }

    let scale = 2
    const captureHeight = Math.ceil(cssHeight * 1.12 + 120)
    scale = Math.min(scale, MAX_CANVAS_EDGE / captureHeight, Math.sqrt(MAX_CANVAS_PIXELS / (cssWidth * captureHeight)))
    if (scale < 0.9) {
        throw createError('content_too_long', '这篇内容过长，手机端无法一次生成 PDF')
    }

    onProgress?.({ phase: 'rendering' })
    const canvas = await html2canvas(element, {
        scale,
        width: cssWidth,
        height: captureHeight,
        backgroundColor: '#ffffff',
        useCORS: true,
        allowTaint: false,
        logging: false,
        imageTimeout: 15000,
        windowWidth: Math.max(1200, cssWidth),
        windowHeight: 900,
        scrollX: 0,
        scrollY: 0
    })
    if (!canvas.width || !canvas.height) {
        throw createError('render_failed', '正文渲染失败，请稍后重试')
    }

    const whitespace = scanWhiteRows(canvas)
    const contentBottom = (() => {
        let last = whitespace.rows.length - 1
        while (last > 0 && whitespace.rows[last]) last--
        return Math.min(canvas.height, Math.round((last + 1) * whitespace.ratio) + 10)
    })()
    if (contentBottom < 40) throw createError('render_failed', '正文渲染失败，请稍后重试')

    const pxPerMm = canvas.width / CONTENT_WIDTH_MM
    const pageHeightPx = Math.floor(PAGE_CONTENT_HEIGHT_MM * pxPerMm)
    const protectedRanges = collectProtectedRanges(element, canvas.width / cssWidth)
    const slices = buildPageSlices(contentBottom, pageHeightPx, whitespace, protectedRanges)
    if (!slices.length) throw createError('render_failed', '正文渲染失败，请稍后重试')

    const pdf = new jsPDF({
        orientation: 'portrait',
        unit: 'mm',
        format: 'a4',
        compress: true
    })
    pdf.setProperties({
        title: title || '课件',
        subject: 'NEST 学巢课件',
        creator: 'NEST 学巢'
    })

    slices.forEach((slice, index) => {
        if (index > 0) pdf.addPage('a4', 'portrait')
        const sliceCanvas = cropCanvas(canvas, slice)
        const imageData = sliceCanvas.toDataURL('image/jpeg', 0.94)
        const imageHeightMm = (slice.height / pxPerMm)
        pdf.addImage(
            imageData,
            'JPEG',
            PAGE_MARGIN_MM,
            PAGE_MARGIN_MM,
            CONTENT_WIDTH_MM,
            Math.min(imageHeightMm, PAGE_CONTENT_HEIGHT_MM),
            `page-${index}`,
            'FAST'
        )
        sliceCanvas.width = 0
        sliceCanvas.height = 0
        onProgress?.({ phase: 'packing', page: index + 1, total: slices.length })
    })

    canvas.width = 0
    canvas.height = 0

    const blob = pdf.output('blob')
    return {
        blob,
        filename: buildPdfFilename(title),
        pages: slices.length,
        bytes: blob.size
    }
}
