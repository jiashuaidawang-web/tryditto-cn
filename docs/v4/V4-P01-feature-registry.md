# V4-P01 特征注册表

版本 `features-1`。每条定义的 `schemaVersion` 是 1。值域之外的值会被拒绝。`UNBOUNDED` 和 `WITHHELD` 是标记，不受枚举表限制。没有证据时注册表不会被拿来填默认值。

敏感分类只有两类：`ORDINARY` 可以进入快照；`SENSITIVE_REF` 只能存受控引用。本轮唯一的敏感引用是 `partner.gender` → `profile.expected_genders_enc`。

## 问卷可以直接投影的自身特征

来源允许 `DIRECT_ANSWER` 和 `SELF_REPORTED`。`opening.message` 放在行为节，不放在自身节。

| featureCode | 名称 | 类型 | 合法值 |
| --- | --- | --- | --- |
| relationship.goal | 关系目标 | ENUM_SET | lifePartner, serious, casual, friends, notSure |
| lifestyle.hobbies | 兴趣 | ENUM_SET | cooking, music, travel, reading, gym, gaming, movies, art |
| lifestyle.cooking | 做饭方式 | ENUM_SET | italian, asian, mexican, comfort, baking, grilling, healthy, brunch |
| lifestyle.music | 常听的音乐 | ENUM_SET | pop, hiphop, indie, electronic, rnb, rock, country, jazz, classical, kpop, latin |
| lifestyle.travel | 旅行方式 | ENUM_SET | backpacking, luxury, foodie, adventure, beach, city, solo, road |
| lifestyle.reading | 阅读类型 | ENUM_SET | litfic, fantasy, scifi, mystery, romance, memoir, selfhelp, poetry, classics, smut |
| lifestyle.gym | 运动方式 | ENUM_SET | lifting, running, yoga, pilates, crossfit, boxing, dance, cycling, climbing |
| lifestyle.gaming | 游戏类型 | ENUM_SET | fps, rpg, sports, moba, strategy, indie, cozy, fighting, mmo |
| lifestyle.movies | 影像偏好 | ENUM_SET | indie, horror, romcom, action, scifi, documentary, a24, foreign, anime, reality |
| lifestyle.art | 创作媒介 | ENUM_SET | drawing, painting, digital, pottery, sculpture, collage, crafts, mixed |
| matching.pace | 筛选节奏 | ENUM | faster, steady, fewer, theOne |
| life.student | 是否在读 | BOOLEAN | true / false。只接受直接答案 |
| education.year | 年级 | ENUM | freshman, sophomore, junior, senior, master, phd, other |
| education.level | 学历 | ENUM | highSchool, college, graduate。`preferNot` 不在表内，用 `WITHHELD` |
| spending.first_date | 第一次见面的花费口径 | ENUM | under20, 20to50, 50to100, 100plus。`noPref` 用 `UNBOUNDED` |
| occupation.text | 自述近况 | TEXT | 清洗后最长 60。去掉邮箱和手机号 |
| lifestyle.stated_interest | 自述兴趣原文 | TEXT | 题库 `allowCustom` 的自定义兴趣。不是八个固定选项之一 |
| support.help_wanted | 希望被帮忙的事 | ENUM_SET | approaching, convo, intoMe, confident, whatIWant, theDate, justDate |
| opening.message | 自述开场白 | TEXT | 行为节。只保存原文，不推断性格 |

## 问卷里已经分开问的伴侣偏好

来源本轮只有 `DIRECT_ANSWER`。

| featureCode | 名称 | 类型 | 范围或合法值 | 敏感 |
| --- | --- | --- | --- | --- |
| partner.age | 希望对方的年龄段 | RANGE | 18–80 | 普通 |
| partner.height | 希望对方的身高范围 | RANGE | 120–230。`any` 不使用这个区间 | 普通 |
| partner.education | 对对方学历的要求 | ENUM | atLeastMine。`none` 用 `UNBOUNDED` | 普通 |
| partner.gender | 希望对方的性别 | TEXT 引用 | 只允许 `profile.expected_genders_enc`。`everyone` 用 `UNBOUNDED`，不写 men/women | SENSITIVE_REF |

`partner.education` 的 `importance=SUPER` 且来源是已确认直接答案时，约束才是 `DOCUMENTED_EXISTING_RULE`。`KINDA` 是 `SOFT`。重要程度和置信度是两个字段。

## 注册了但投影器不填的槽

这些维度没有对应题目。留着是为了以后的明确自述、用户确认过的推断，或真实反馈。投影器看到未知答案键也不会填它们。

自身槽还允许记忆节，但记忆节只接受 `FEEDBACK_OBSERVATION`。模型推断只能是 `PROVISIONAL`，不能进记忆节。

| featureCode | 名称 | 节 | 合法值 |
| --- | --- | --- | --- |
| self.social.initiative | 自述社交主动性 | SELF, MEMORY | low, steady, high |
| self.communication | 自述沟通习惯 | SELF, MEMORY | quiet, direct, frequent |
| self.exploration | 自述探索倾向 | SELF, MEMORY | low, steady, high |
| self.pace | 自述相处节奏 | SELF, MEMORY | slow, steady, fast |
| self.schedule | 自述作息 | SELF, MEMORY | early, flexible, late |
| partner.social.initiative | 希望对方的社交主动性 | PREFERENCE | low, steady, high |
| partner.communication | 希望对方的沟通习惯 | PREFERENCE | quiet, direct, frequent |
| partner.pace | 希望对方的相处节奏 | PREFERENCE | slow, steady, fast |
| partner.support | 希望得到的情感支持 | PREFERENCE | space, steady, close |
| partner.schedule | 希望对方的作息 | PREFERENCE | early, flexible, late |

偏好槽如果来自 `MODEL_INFERENCE`，约束必须是 `SOFT`，状态必须是 `PROVISIONAL`。不能标成既有硬规则。

## 明确没有的特征

没有 MBTI，没有颜值，没有价值排名，没有人格优劣。注册表测试会拒绝码里出现 `mbti`、`attractiveness`、`rank`，也会拒绝显示名里出现这些等级词。
