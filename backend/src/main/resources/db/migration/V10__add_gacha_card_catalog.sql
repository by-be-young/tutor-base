-- 数据驱动的卡池目录：卡组（card_sets）与卡片定义（card_definitions）。
-- 抽卡/目录接口在每次请求时读取 active 行，新增或停用卡组无需修改 Java 代码。
-- 同时新增 user_points.draw_count，记录累计抽卡次数（不影响 lifetime_points）。

SET lock_timeout = '5s';
SET statement_timeout = '30s';

CREATE TABLE IF NOT EXISTS public.card_sets (
    set_key text PRIMARY KEY,
    name text NOT NULL,
    theme text NOT NULL,
    style text NOT NULL,
    emblem text NOT NULL,
    color_a text NOT NULL,
    color_b text NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.card_definitions (
    card_key text PRIMARY KEY,
    set_key text NOT NULL REFERENCES public.card_sets (set_key) ON DELETE CASCADE,
    name text NOT NULL,
    image_path text NOT NULL,
    rarity text NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    CONSTRAINT card_definitions_rarity_check CHECK (rarity IN ('common', 'rare'))
);

CREATE INDEX IF NOT EXISTS card_sets_active_order_idx
    ON public.card_sets (active, sort_order, set_key);

CREATE INDEX IF NOT EXISTS card_definitions_set_order_idx
    ON public.card_definitions (set_key, active, sort_order, card_key);

-- 每个卡组最多一张启用中的稀有卡：UP 轮换与稀有抽取都依赖这一不变量。
CREATE UNIQUE INDEX IF NOT EXISTS card_definitions_active_rare_unique
    ON public.card_definitions (set_key)
    WHERE rarity = 'rare' AND active;

INSERT INTO public.card_sets (set_key, name, theme, style, emblem, color_a, color_b, sort_order)
VALUES
    ('puppy', '狗狗图鉴', '宠物', '写实油画', 'fas fa-dog', '#f0b27a', '#c05a2f', 1),
    ('ocean', '深海探秘', '海洋', '淡雅水彩', 'fas fa-fish', '#6bc8e8', '#1f7fb8', 2),
    ('cosmos', '太空漫游', '航天', '科幻概念', 'fas fa-rocket', '#8f7be0', '#3f2f9e', 3),
    ('dessert', '甜品工坊', '美食', '美食摄影', 'fas fa-cake-candles', '#f0a8c8', '#d0608f', 4),
    ('sport', '热血运动', '体育', '动感漫画', 'fas fa-futbol', '#8fe06b', '#2f9e4e', 5),
    ('milkfrog', '奶蛙狂笑录', '迷因', '3D黏土丑萌', 'fas fa-laugh-squint', '#f5c542', '#d98a1f', 6),
    ('delta', '三角洲鼠鼠连', '游戏二创', '3D动物干员', 'fas fa-shield-halved', '#e08a3c', '#3c4a6b', 7),
    ('opossum', '背手负鼠日记', '社畜', '写实摄影', 'fas fa-user-tie', '#8a9bb0', '#3a4a6b', 8),
    ('verify', '我要验牌', '港片', '复古电影感', 'fas fa-spa', '#b03a3a', '#d4a84b', 9),
    ('softgun', '心软枪手', '末日治愈', '吉卜力动画', 'fas fa-heart', '#e8923c', '#6b4a8a', 10)
ON CONFLICT (set_key) DO NOTHING;

INSERT INTO public.card_definitions (card_key, set_key, name, image_path, rarity, sort_order)
VALUES
    ('puppy-r', 'puppy', '汪星人欢乐时光', 'cards/puppy-r.png', 'rare', 0),
    ('puppy-c0', 'puppy', '柴犬', 'cards/puppy-c0.png', 'common', 1),
    ('puppy-c1', 'puppy', '拉布拉多', 'cards/puppy-c1.png', 'common', 2),
    ('puppy-c2', 'puppy', '柯基', 'cards/puppy-c2.png', 'common', 3),
    ('puppy-c3', 'puppy', '萨摩耶', 'cards/puppy-c3.png', 'common', 4),
    ('puppy-c4', 'puppy', '金毛', 'cards/puppy-c4.png', 'common', 5),
    ('puppy-c5', 'puppy', '哈士奇', 'cards/puppy-c5.png', 'common', 6),
    ('ocean-r', 'ocean', '深海盛会', 'cards/ocean-r.png', 'rare', 0),
    ('ocean-c0', 'ocean', '海豚', 'cards/ocean-c0.png', 'common', 1),
    ('ocean-c1', 'ocean', '座头鲸', 'cards/ocean-c1.png', 'common', 2),
    ('ocean-c2', 'ocean', '章鱼', 'cards/ocean-c2.png', 'common', 3),
    ('ocean-c3', 'ocean', '绿海龟', 'cards/ocean-c3.png', 'common', 4),
    ('ocean-c4', 'ocean', '水母', 'cards/ocean-c4.png', 'common', 5),
    ('ocean-c5', 'ocean', '小丑鱼', 'cards/ocean-c5.png', 'common', 6),
    ('cosmos-r', 'cosmos', '星际漫游', 'cards/cosmos-r.png', 'rare', 0),
    ('cosmos-c0', 'cosmos', '火箭', 'cards/cosmos-c0.png', 'common', 1),
    ('cosmos-c1', 'cosmos', '宇航员', 'cards/cosmos-c1.png', 'common', 2),
    ('cosmos-c2', 'cosmos', '土星', 'cards/cosmos-c2.png', 'common', 3),
    ('cosmos-c3', 'cosmos', '流星', 'cards/cosmos-c3.png', 'common', 4),
    ('cosmos-c4', 'cosmos', '空间站', 'cards/cosmos-c4.png', 'common', 5),
    ('cosmos-c5', 'cosmos', '探测车', 'cards/cosmos-c5.png', 'common', 6),
    ('dessert-r', 'dessert', '甜品盛宴', 'cards/dessert-r.png', 'rare', 0),
    ('dessert-c0', 'dessert', '草莓蛋糕', 'cards/dessert-c0.png', 'common', 1),
    ('dessert-c1', 'dessert', '马卡龙', 'cards/dessert-c1.png', 'common', 2),
    ('dessert-c2', 'dessert', '甜甜圈', 'cards/dessert-c2.png', 'common', 3),
    ('dessert-c3', 'dessert', '冰淇淋', 'cards/dessert-c3.png', 'common', 4),
    ('dessert-c4', 'dessert', '奶油泡芙', 'cards/dessert-c4.png', 'common', 5),
    ('dessert-c5', 'dessert', '焦糖布丁', 'cards/dessert-c5.png', 'common', 6),
    ('sport-r', 'sport', '赛场狂欢', 'cards/sport-r.png', 'rare', 0),
    ('sport-c0', 'sport', '篮球', 'cards/sport-c0.png', 'common', 1),
    ('sport-c1', 'sport', '足球', 'cards/sport-c1.png', 'common', 2),
    ('sport-c2', 'sport', '网球', 'cards/sport-c2.png', 'common', 3),
    ('sport-c3', 'sport', '游泳', 'cards/sport-c3.png', 'common', 4),
    ('sport-c4', 'sport', '滑板', 'cards/sport-c4.png', 'common', 5),
    ('sport-c5', 'sport', '羽毛球', 'cards/sport-c5.png', 'common', 6),
    ('milkfrog-r', 'milkfrog', '齁齁齁之王', 'cards/milkfrog-r.png', 'rare', 0),
    ('milkfrog-c0', 'milkfrog', '经典大笑', 'cards/milkfrog-c0.png', 'common', 1),
    ('milkfrog-c1', 'milkfrog', '托腮思考', 'cards/milkfrog-c1.png', 'common', 2),
    ('milkfrog-c2', 'milkfrog', '工位奶蛙', 'cards/milkfrog-c2.png', 'common', 3),
    ('milkfrog-c3', 'milkfrog', '旅行奶蛙', 'cards/milkfrog-c3.png', 'common', 4),
    ('milkfrog-c4', 'milkfrog', '街头奶蛙', 'cards/milkfrog-c4.png', 'common', 5),
    ('milkfrog-c5', 'milkfrog', '招财奶蛙', 'cards/milkfrog-c5.png', 'common', 6),
    ('delta-r', 'delta', '千万不能掉队', 'cards/delta-r.png', 'rare', 0),
    ('delta-c0', 'delta', '哈基蜂', 'cards/delta-c0.png', 'common', 1),
    ('delta-c1', 'delta', '威虫', 'cards/delta-c1.png', 'common', 2),
    ('delta-c2', 'delta', '刀盾狗', 'cards/delta-c2.png', 'common', 3),
    ('delta-c3', 'delta', '红狼', 'cards/delta-c3.png', 'common', 4),
    ('delta-c4', 'delta', '比特猫', 'cards/delta-c4.png', 'common', 5),
    ('delta-c5', 'delta', '鸟兽兽', 'cards/delta-c5.png', 'common', 6),
    ('opossum-r', 'opossum', '事情有了新退展', 'cards/opossum-r.png', 'rare', 0),
    ('opossum-c0', 'opossum', '窗边发呆', 'cards/opossum-c0.png', 'common', 1),
    ('opossum-c1', 'opossum', '敬自己一杯', 'cards/opossum-c1.png', 'common', 2),
    ('opossum-c2', 'opossum', '深夜加班', 'cards/opossum-c2.png', 'common', 3),
    ('opossum-c3', 'opossum', '晨间咖啡', 'cards/opossum-c3.png', 'common', 4),
    ('opossum-c4', 'opossum', '周末瘫平', 'cards/opossum-c4.png', 'common', 5),
    ('opossum-c5', 'opossum', '夕阳释然', 'cards/opossum-c5.png', 'common', 6),
    ('verify-r', 'verify', '牌没有问题', 'cards/verify-r.png', 'rare', 0),
    ('verify-c0', 'verify', '黑桃A', 'cards/verify-c0.png', 'common', 1),
    ('verify-c1', 'verify', '筹码堆', 'cards/verify-c1.png', 'common', 2),
    ('verify-c2', 'verify', '骰盅', 'cards/verify-c2.png', 'common', 3),
    ('verify-c3', 'verify', '轮盘', 'cards/verify-c3.png', 'common', 4),
    ('verify-c4', 'verify', '霓虹雨夜', 'cards/verify-c4.png', 'common', 5),
    ('verify-c5', 'verify', '风衣背影', 'cards/verify-c5.png', 'common', 6),
    ('softgun-r', 'softgun', '拥抱小僵尸', 'cards/softgun-r.png', 'rare', 0),
    ('softgun-c0', 'softgun', '含泪举枪', 'cards/softgun-c0.png', 'common', 1),
    ('softgun-c1', 'softgun', '可爱小僵尸', 'cards/softgun-c1.png', 'common', 2),
    ('softgun-c2', 'softgun', '废墟街道', 'cards/softgun-c2.png', 'common', 3),
    ('softgun-c3', 'softgun', '夜下篝火', 'cards/softgun-c3.png', 'common', 4),
    ('softgun-c4', 'softgun', '防毒面具猫', 'cards/softgun-c4.png', 'common', 5),
    ('softgun-c5', 'softgun', '樱花废墟', 'cards/softgun-c5.png', 'common', 6)
ON CONFLICT (card_key) DO NOTHING;

ALTER TABLE public.user_points
    ADD COLUMN IF NOT EXISTS draw_count bigint NOT NULL DEFAULT 0;
