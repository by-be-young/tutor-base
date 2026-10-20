// src/data/cardCatalog.js
// 收藏室卡片目录：卡组定义与卡片检索
// ----------------------------------------------------------------------------
// 每个卡组包含 1 张稀有卡片 + 6 张普通卡片，卡片以图片呈现（3:4 竖版）。
// 各卡组主题与画风互不相同（写实油画 / 水彩 / 科幻概念 / 美食摄影 / 动感漫画）。
// 抽卡由后端依据当前会话、积分与已有收藏完成，浏览器只负责展示服务端返回的卡片。

export const CARD_SETS = [
    {
        key: 'puppy',
        name: '狗狗图鉴',
        theme: '宠物',
        style: '写实油画',
        emblem: 'fas fa-dog',
        colors: ['#f0b27a', '#c05a2f'],
        rare: { name: '汪星人欢乐时光', img: `${import.meta.env.BASE_URL}cards/puppy-r.png` },
        commons: [
            { name: '柴犬', img: `${import.meta.env.BASE_URL}cards/puppy-c0.png` },
            { name: '拉布拉多', img: `${import.meta.env.BASE_URL}cards/puppy-c1.png` },
            { name: '柯基', img: `${import.meta.env.BASE_URL}cards/puppy-c2.png` },
            { name: '萨摩耶', img: `${import.meta.env.BASE_URL}cards/puppy-c3.png` },
            { name: '金毛', img: `${import.meta.env.BASE_URL}cards/puppy-c4.png` },
            { name: '哈士奇', img: `${import.meta.env.BASE_URL}cards/puppy-c5.png` }
        ]
    },
    {
        key: 'ocean',
        name: '深海探秘',
        theme: '海洋',
        style: '淡雅水彩',
        emblem: 'fas fa-fish',
        colors: ['#6bc8e8', '#1f7fb8'],
        rare: { name: '深海盛会', img: `${import.meta.env.BASE_URL}cards/ocean-r.png` },
        commons: [
            { name: '海豚', img: `${import.meta.env.BASE_URL}cards/ocean-c0.png` },
            { name: '座头鲸', img: `${import.meta.env.BASE_URL}cards/ocean-c1.png` },
            { name: '章鱼', img: `${import.meta.env.BASE_URL}cards/ocean-c2.png` },
            { name: '绿海龟', img: `${import.meta.env.BASE_URL}cards/ocean-c3.png` },
            { name: '水母', img: `${import.meta.env.BASE_URL}cards/ocean-c4.png` },
            { name: '小丑鱼', img: `${import.meta.env.BASE_URL}cards/ocean-c5.png` }
        ]
    },
    {
        key: 'cosmos',
        name: '太空漫游',
        theme: '航天',
        style: '科幻概念',
        emblem: 'fas fa-rocket',
        colors: ['#8f7be0', '#3f2f9e'],
        rare: { name: '星际漫游', img: `${import.meta.env.BASE_URL}cards/cosmos-r.png` },
        commons: [
            { name: '火箭', img: `${import.meta.env.BASE_URL}cards/cosmos-c0.png` },
            { name: '宇航员', img: `${import.meta.env.BASE_URL}cards/cosmos-c1.png` },
            { name: '土星', img: `${import.meta.env.BASE_URL}cards/cosmos-c2.png` },
            { name: '流星', img: `${import.meta.env.BASE_URL}cards/cosmos-c3.png` },
            { name: '空间站', img: `${import.meta.env.BASE_URL}cards/cosmos-c4.png` },
            { name: '探测车', img: `${import.meta.env.BASE_URL}cards/cosmos-c5.png` }
        ]
    },
    {
        key: 'dessert',
        name: '甜品工坊',
        theme: '美食',
        style: '美食摄影',
        emblem: 'fas fa-cake-candles',
        colors: ['#f0a8c8', '#d0608f'],
        rare: { name: '甜品盛宴', img: `${import.meta.env.BASE_URL}cards/dessert-r.png` },
        commons: [
            { name: '草莓蛋糕', img: `${import.meta.env.BASE_URL}cards/dessert-c0.png` },
            { name: '马卡龙', img: `${import.meta.env.BASE_URL}cards/dessert-c1.png` },
            { name: '甜甜圈', img: `${import.meta.env.BASE_URL}cards/dessert-c2.png` },
            { name: '冰淇淋', img: `${import.meta.env.BASE_URL}cards/dessert-c3.png` },
            { name: '奶油泡芙', img: `${import.meta.env.BASE_URL}cards/dessert-c4.png` },
            { name: '焦糖布丁', img: `${import.meta.env.BASE_URL}cards/dessert-c5.png` }
        ]
    },
    {
        key: 'sport',
        name: '热血运动',
        theme: '体育',
        style: '动感漫画',
        emblem: 'fas fa-futbol',
        colors: ['#8fe06b', '#2f9e4e'],
        rare: { name: '赛场狂欢', img: `${import.meta.env.BASE_URL}cards/sport-r.png` },
        commons: [
            { name: '篮球', img: `${import.meta.env.BASE_URL}cards/sport-c0.png` },
            { name: '足球', img: `${import.meta.env.BASE_URL}cards/sport-c1.png` },
            { name: '网球', img: `${import.meta.env.BASE_URL}cards/sport-c2.png` },
            { name: '游泳', img: `${import.meta.env.BASE_URL}cards/sport-c3.png` },
            { name: '滑板', img: `${import.meta.env.BASE_URL}cards/sport-c4.png` },
            { name: '羽毛球', img: `${import.meta.env.BASE_URL}cards/sport-c5.png` }
        ]
    },
    {
        key: 'milkfrog',
        name: '奶蛙狂笑录',
        theme: '迷因',
        style: '3D黏土丑萌',
        emblem: 'fas fa-laugh-squint',
        colors: ['#f5c542', '#d98a1f'],
        rare: { name: '齁齁齁之王', img: `${import.meta.env.BASE_URL}cards/milkfrog-r.png` },
        commons: [
            { name: '经典大笑', img: `${import.meta.env.BASE_URL}cards/milkfrog-c0.png` },
            { name: '托腮思考', img: `${import.meta.env.BASE_URL}cards/milkfrog-c1.png` },
            { name: '工位奶蛙', img: `${import.meta.env.BASE_URL}cards/milkfrog-c2.png` },
            { name: '旅行奶蛙', img: `${import.meta.env.BASE_URL}cards/milkfrog-c3.png` },
            { name: '街头奶蛙', img: `${import.meta.env.BASE_URL}cards/milkfrog-c4.png` },
            { name: '招财奶蛙', img: `${import.meta.env.BASE_URL}cards/milkfrog-c5.png` }
        ]
    },
    {
        key: 'delta',
        name: '三角洲鼠鼠连',
        theme: '游戏二创',
        style: '3D动物干员',
        emblem: 'fas fa-shield-halved',
        colors: ['#e08a3c', '#3c4a6b'],
        rare: { name: '千万不能掉队', img: `${import.meta.env.BASE_URL}cards/delta-r.png` },
        commons: [
            { name: '哈基蜂', img: `${import.meta.env.BASE_URL}cards/delta-c0.png` },
            { name: '威虫', img: `${import.meta.env.BASE_URL}cards/delta-c1.png` },
            { name: '刀盾狗', img: `${import.meta.env.BASE_URL}cards/delta-c2.png` },
            { name: '红狼', img: `${import.meta.env.BASE_URL}cards/delta-c3.png` },
            { name: '比特猫', img: `${import.meta.env.BASE_URL}cards/delta-c4.png` },
            { name: '鸟兽兽', img: `${import.meta.env.BASE_URL}cards/delta-c5.png` }
        ]
    },
    {
        key: 'opossum',
        name: '背手负鼠日记',
        theme: '社畜',
        style: '写实摄影',
        emblem: 'fas fa-user-tie',
        colors: ['#8a9bb0', '#3a4a6b'],
        rare: { name: '事情有了新退展', img: `${import.meta.env.BASE_URL}cards/opossum-r.png` },
        commons: [
            { name: '窗边发呆', img: `${import.meta.env.BASE_URL}cards/opossum-c0.png` },
            { name: '敬自己一杯', img: `${import.meta.env.BASE_URL}cards/opossum-c1.png` },
            { name: '深夜加班', img: `${import.meta.env.BASE_URL}cards/opossum-c2.png` },
            { name: '晨间咖啡', img: `${import.meta.env.BASE_URL}cards/opossum-c3.png` },
            { name: '周末瘫平', img: `${import.meta.env.BASE_URL}cards/opossum-c4.png` },
            { name: '夕阳释然', img: `${import.meta.env.BASE_URL}cards/opossum-c5.png` }
        ]
    },
    {
        key: 'verify',
        name: '我要验牌',
        theme: '港片',
        style: '复古电影感',
        emblem: 'fas fa-spa',
        colors: ['#b03a3a', '#d4a84b'],
        rare: { name: '牌没有问题', img: `${import.meta.env.BASE_URL}cards/verify-r.png` },
        commons: [
            { name: '黑桃A', img: `${import.meta.env.BASE_URL}cards/verify-c0.png` },
            { name: '筹码堆', img: `${import.meta.env.BASE_URL}cards/verify-c1.png` },
            { name: '骰盅', img: `${import.meta.env.BASE_URL}cards/verify-c2.png` },
            { name: '轮盘', img: `${import.meta.env.BASE_URL}cards/verify-c3.png` },
            { name: '霓虹雨夜', img: `${import.meta.env.BASE_URL}cards/verify-c4.png` },
            { name: '风衣背影', img: `${import.meta.env.BASE_URL}cards/verify-c5.png` }
        ]
    },
    {
        key: 'softgun',
        name: '心软枪手',
        theme: '末日治愈',
        style: '吉卜力动画',
        emblem: 'fas fa-heart',
        colors: ['#e8923c', '#6b4a8a'],
        rare: { name: '拥抱小僵尸', img: `${import.meta.env.BASE_URL}cards/softgun-r.png` },
        commons: [
            { name: '含泪举枪', img: `${import.meta.env.BASE_URL}cards/softgun-c0.png` },
            { name: '可爱小僵尸', img: `${import.meta.env.BASE_URL}cards/softgun-c1.png` },
            { name: '废墟街道', img: `${import.meta.env.BASE_URL}cards/softgun-c2.png` },
            { name: '夜下篝火', img: `${import.meta.env.BASE_URL}cards/softgun-c3.png` },
            { name: '防毒面具猫', img: `${import.meta.env.BASE_URL}cards/softgun-c4.png` },
            { name: '樱花废墟', img: `${import.meta.env.BASE_URL}cards/softgun-c5.png` }
        ]
    }
]

/**
 * 里程碑积分 → 稀有度（确定性）
 * @param {number} milestonePoints 里程碑积分（200 的整数倍）
 * @returns {'rare'|'common'} 整千积分为稀有，其余为普通
 */
export function milestoneRarity(milestonePoints) {
    return milestonePoints % 1000 === 0 ? 'rare' : 'common'
}

/**
 * 根据收藏记录（set_key + card_key）还原卡片对象
 * @param {string} setKey 卡组 key
 * @param {string} cardKey 卡片 key（如 'puppy-r'、'puppy-c0'）
 * @returns {object|null} 卡片对象
 */
export function findCard(setKey, cardKey) {
    const set = CARD_SETS.find(s => s.key === setKey)
    if (!set) return null
    if (cardKey === `${setKey}-r`) return set.rare
    const match = /-c(\d+)$/.exec(cardKey)
    if (!match) return null
    return set.commons[parseInt(match[1], 10)] || null
}

/**
 * 生成里程碑列表（200 ~ maxPoints，每 200 一个；整千为稀有节点）
 * 里程碑只标记稀有度，具体卡片在领取时随机抽取。
 */
export function buildMilestones(maxPoints = 6000) {
    const list = []
    for (let pts = 200; pts <= maxPoints; pts += 200) {
        list.push({ pts, isRare: milestoneRarity(pts) === 'rare' })
    }
    return list
}

// ----------------------------------------------------------------------------
// 卡池系统：每周轮换 UP 卡 + 概率抽卡
// ----------------------------------------------------------------------------

/** 单抽消耗积分 */
export const DRAW_COST = 80
/** 单抽稀有卡概率；80 积分/抽时，稀有卡期望约 1600 积分出现一次 */
export const RARE_RATE = 0.05
/** 若抽到稀有，其中为当期 UP 的概率 */
export const UP_RATE_WHEN_RARE = 0.5

/** 全部稀有卡（带 set_key / card_key 元信息） */
export const ALL_RARE_CARDS = CARD_SETS.map(set => ({
    set_key: set.key,
    card_key: `${set.key}-r`,
    rarity: 'rare',
    name: set.rare.name,
    img: set.rare.img,
    setName: set.name,
    setColors: set.colors
}))

/** 当前 ISO 周数（1-53） */
function isoWeek(date = new Date()) {
    const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
    const dayNum = d.getUTCDay() || 7
    d.setUTCDate(d.getUTCDate() + 4 - dayNum)
    const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
    return Math.ceil((((d - yearStart) / 86400000) + 1) / 7)
}

/** 当期 UP 稀有卡（按周轮换） */
export function getWeeklyUpCard(now = new Date()) {
    const week = isoWeek(now)
    return ALL_RARE_CARDS[(week - 1) % ALL_RARE_CARDS.length]
}

/**
 * 单抽一次卡池
 * @returns {{setKey:string, cardKey:string, rarity:'rare'|'common', card:object, isUp:boolean}}
 */
export function drawOneCard() {
    const roll = Math.random()
    if (roll < RARE_RATE) {
        // 稀有：50% 概率为当期 UP
        if (Math.random() < UP_RATE_WHEN_RARE) {
            const up = getWeeklyUpCard()
            return { setKey: up.set_key, cardKey: up.card_key, rarity: 'rare', card: findCard(up.set_key, up.card_key), isUp: true }
        }
        // 否则随机一张其他稀有
        const pool = ALL_RARE_CARDS.filter(c => c.card_key !== getWeeklyUpCard().card_key)
        const picked = pool[Math.floor(Math.random() * pool.length)]
        return { setKey: picked.set_key, cardKey: picked.card_key, rarity: 'rare', card: findCard(picked.set_key, picked.card_key), isUp: false }
    }
    // 普通：随机一个卡组，从其 6 张普通里随机
    const set = CARD_SETS[Math.floor(Math.random() * CARD_SETS.length)]
    const idx = Math.floor(Math.random() * set.commons.length)
    return { setKey: set.key, cardKey: `${set.key}-c${idx}`, rarity: 'common', card: set.commons[idx], isUp: false }
}
