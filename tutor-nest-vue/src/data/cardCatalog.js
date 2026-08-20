// src/data/cardCatalog.js
// 收藏室卡片目录：卡组定义与里程碑 → 卡片的确定性映射
// ----------------------------------------------------------------------------
// 每个卡组包含 1 张稀有卡片 + 6 张普通卡片；卡片没有图片，用「渐变色 + 图标」呈现。
// 里程碑映射规则：
//   - 普通节点（每 200 积分，且不是整千）：按固定顺序循环取卡组内的普通卡
//   - 稀有节点（每 1000 积分）：按固定顺序循环取卡组稀有卡
// 同一里程碑对所有人返回同一张卡片，保证收藏可复现。

export const CARD_SETS = [
    {
        key: 'flame',
        name: '烈焰炼金',
        theme: '化学',
        emblem: 'fas fa-fire',
        colors: ['#f08a7a', '#c0483f'],
        rare: { name: '绯红神话·炼金之焰', icon: 'fas fa-fire', colors: ['#f08a7a', '#c0483f'] },
        commons: [
            { name: '赤焰试纸', icon: 'fas fa-vial', colors: ['#e58f8f', '#b35656'] },
            { name: '沸腾坩埚', icon: 'fas fa-flask', colors: ['#d9825a', '#a05a2f'] },
            { name: '闪电催化', icon: 'fas fa-bolt', colors: ['#e8c35c', '#b3923a'] },
            { name: '流星火花', icon: 'fas fa-meteor', colors: ['#f0a58a', '#c06050'] },
            { name: '王冠结晶', icon: 'fas fa-crown', colors: ['#e8c85a', '#b09030'] },
            { name: '深红矿藏', icon: 'fas fa-gem', colors: ['#e07a7a', '#a84040'] }
        ]
    },
    {
        key: 'aurora',
        name: '极光之翼',
        theme: '英语',
        emblem: 'fas fa-feather',
        colors: ['#8fd4f0', '#3f8fb8'],
        rare: { name: '冰蓝神话·极光之翼', icon: 'fas fa-feather', colors: ['#8fd4f0', '#3f8fb8'] },
        commons: [
            { name: '晨曦书页', icon: 'fas fa-book-open', colors: ['#c9e8f4', '#7eb3cf'] },
            { name: '锚定单词', icon: 'fas fa-anchor', colors: ['#9fc4e0', '#5f8fb0'] },
            { name: '朗读星辉', icon: 'fas fa-microphone', colors: ['#b5d4f0', '#6a8fc0'] },
            { name: '月圆语法', icon: 'fas fa-moon', colors: ['#d4c9f0', '#8a7fc0'] },
            { name: '晴空拼写', icon: 'fas fa-sun', colors: ['#f0e8a0', '#c0b060'] },
            { name: '夜航日记', icon: 'fas fa-book', colors: ['#a8c8d8', '#5f90a8'] }
        ]
    },
    {
        key: 'galaxy',
        name: '星河守望',
        theme: '数理',
        emblem: 'fas fa-compass',
        colors: ['#f2d66b', '#c9a227'],
        rare: { name: '金色传说·时光罗盘', icon: 'fas fa-compass', colors: ['#f2d66b', '#c9a227'] },
        commons: [
            { name: '坐标星辰', icon: 'fas fa-star', colors: ['#f2e08a', '#c0a855'] },
            { name: '分秒时辰', icon: 'fas fa-clock', colors: ['#d9c98a', '#a89450'] },
            { name: '星轨测算', icon: 'fas fa-ruler', colors: ['#c9d9e8', '#8aa0b8'] },
            { name: '三角灯塔', icon: 'fas fa-mountain', colors: ['#b8d4c0', '#7aa88a'] },
            { name: '天秤均衡', icon: 'fas fa-balance-scale', colors: ['#d8c8b0', '#a89070'] },
            { name: '圆周度量', icon: 'fas fa-circle', colors: ['#e8c8d8', '#b080a0'] }
        ]
    },
    {
        key: 'forest',
        name: '青岚秘境',
        theme: '自然',
        emblem: 'fas fa-seedling',
        colors: ['#7fe0b0', '#2f9e76'],
        rare: { name: '翠玉传奇·守护之灵', icon: 'fas fa-shield-alt', colors: ['#7fe0b0', '#2f9e76'] },
        commons: [
            { name: '苔原印记', icon: 'fas fa-leaf', colors: ['#9cc48a', '#5f8f4e'] },
            { name: '林间露珠', icon: 'fas fa-droplet', colors: ['#8fd4e0', '#3f90a8'] },
            { name: '山风回声', icon: 'fas fa-mountain', colors: ['#a8c8b0', '#6a9078'] },
            { name: '岩层脉络', icon: 'fas fa-layer-group', colors: ['#c0b8a0', '#908060'] },
            { name: '藤蔓缠绕', icon: 'fas fa-seedling', colors: ['#a0d4a0', '#60a060'] },
            { name: '萤火微光', icon: 'fas fa-lightbulb', colors: ['#e0e0a0', '#a0a060'] }
        ]
    }
]

/**
 * 里程碑积分 → 卡片（确定性映射）
 * @param {number} milestonePoints 里程碑积分（200 的整数倍）
 * @returns {{ setKey: string, set: object, rarity: 'common'|'rare', cardKey: string, card: object }}
 */
export function milestoneCard(milestonePoints) {
    const isRare = milestonePoints % 1000 === 0
    if (isRare) {
        const set = CARD_SETS[((milestonePoints / 1000) - 1 + CARD_SETS.length * 100) % CARD_SETS.length]
        return { setKey: set.key, set, rarity: 'rare', cardKey: `${set.key}-r`, card: set.rare }
    }
    const n = (milestonePoints / 200) - 1
    const set = CARD_SETS[Math.floor(n / 6) % CARD_SETS.length]
    const slot = n % 6
    return { setKey: set.key, set, rarity: 'common', cardKey: `${set.key}-c${slot}`, card: set.commons[slot] }
}

/**
 * 生成里程碑列表（200 ~ maxPoints，每 200 一个；整千为稀有节点）
 */
export function buildMilestones(maxPoints = 6000) {
    const list = []
    for (let pts = 200; pts <= maxPoints; pts += 200) {
        const isRare = pts % 1000 === 0
        const { card, rarity, setKey, cardKey } = milestoneCard(pts)
        list.push({ pts, isRare, rarity, setKey, cardKey, card })
    }
    return list
}
