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
        rare: { name: '汪星人欢乐时光', img: '/cards/puppy-r.png' },
        commons: [
            { name: '柴犬', img: '/cards/puppy-c0.png' },
            { name: '拉布拉多', img: '/cards/puppy-c1.png' },
            { name: '柯基', img: '/cards/puppy-c2.png' },
            { name: '萨摩耶', img: '/cards/puppy-c3.png' },
            { name: '金毛', img: '/cards/puppy-c4.png' },
            { name: '哈士奇', img: '/cards/puppy-c5.png' }
        ]
    },
    {
        key: 'ocean',
        name: '深海探秘',
        theme: '海洋',
        style: '淡雅水彩',
        emblem: 'fas fa-fish',
        colors: ['#6bc8e8', '#1f7fb8'],
        rare: { name: '深海盛会', img: '/cards/ocean-r.png' },
        commons: [
            { name: '海豚', img: '/cards/ocean-c0.png' },
            { name: '座头鲸', img: '/cards/ocean-c1.png' },
            { name: '章鱼', img: '/cards/ocean-c2.png' },
            { name: '绿海龟', img: '/cards/ocean-c3.png' },
            { name: '水母', img: '/cards/ocean-c4.png' },
            { name: '小丑鱼', img: '/cards/ocean-c5.png' }
        ]
    },
    {
        key: 'cosmos',
        name: '太空漫游',
        theme: '航天',
        style: '科幻概念',
        emblem: 'fas fa-rocket',
        colors: ['#8f7be0', '#3f2f9e'],
        rare: { name: '星际漫游', img: '/cards/cosmos-r.png' },
        commons: [
            { name: '火箭', img: '/cards/cosmos-c0.png' },
            { name: '宇航员', img: '/cards/cosmos-c1.png' },
            { name: '土星', img: '/cards/cosmos-c2.png' },
            { name: '流星', img: '/cards/cosmos-c3.png' },
            { name: '空间站', img: '/cards/cosmos-c4.png' },
            { name: '探测车', img: '/cards/cosmos-c5.png' }
        ]
    },
    {
        key: 'dessert',
        name: '甜品工坊',
        theme: '美食',
        style: '美食摄影',
        emblem: 'fas fa-cake-candles',
        colors: ['#f0a8c8', '#d0608f'],
        rare: { name: '甜品盛宴', img: '/cards/dessert-r.png' },
        commons: [
            { name: '草莓蛋糕', img: '/cards/dessert-c0.png' },
            { name: '马卡龙', img: '/cards/dessert-c1.png' },
            { name: '甜甜圈', img: '/cards/dessert-c2.png' },
            { name: '冰淇淋', img: '/cards/dessert-c3.png' },
            { name: '奶油泡芙', img: '/cards/dessert-c4.png' },
            { name: '焦糖布丁', img: '/cards/dessert-c5.png' }
        ]
    },
    {
        key: 'sport',
        name: '热血运动',
        theme: '体育',
        style: '动感漫画',
        emblem: 'fas fa-futbol',
        colors: ['#8fe06b', '#2f9e4e'],
        rare: { name: '赛场狂欢', img: '/cards/sport-r.png' },
        commons: [
            { name: '篮球', img: '/cards/sport-c0.png' },
            { name: '足球', img: '/cards/sport-c1.png' },
            { name: '网球', img: '/cards/sport-c2.png' },
            { name: '游泳', img: '/cards/sport-c3.png' },
            { name: '滑板', img: '/cards/sport-c4.png' },
            { name: '羽毛球', img: '/cards/sport-c5.png' }
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
