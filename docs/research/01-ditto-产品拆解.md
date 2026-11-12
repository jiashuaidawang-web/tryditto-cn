# Ditto 产品拆解（给中国版 PRD 用）

调研日期：2026-11-12。对象是美国大学生 AI 红娘 [Ditto](https://tryditto.com/)（公司主体 Popcorn AI Tech, Inc.，亦用 ditto.ai）。本文只拆产品，不写实现。

证据标记：

- **【已证实】**：下面给出的来源页面里能直接读到的事实或引述。引文按抓取到的英文原意压缩，不编造原文没有的句子。
- **【推断】**：多条已证实事实拼出来的产品判断，不是对方公开说过的原话。
- **【猜测】**：没有足够来源，只用来标出缺口，不能写进 PRD 当需求。

数字全部是公司自报或记者转述，没有独立审计。同一指标在不同日期互相打架，见第 9 节，不要合成一个「官方最新数字」。

---

## 1. 一句话

Ditto 不做约会 App。学生用学校邮箱证明身份，在 iMessage 里跟 AI 说自己要什么样的人；每周三 19:00 收到**一个**匹配、一张拼贴介绍，再双方填可约时间，由 Ditto 定校园附近的见面。核心承诺是砍掉滑动和见面前的闲聊。【已证实】[官网 How it works / FAQ](https://tryditto.com/)、[TechCrunch 2026-08-06](https://techcrunch.com/2026/08/06/gen-z-dating-apps-like-ditto-ditch-swiping-in-favor-of-ai-matchmaking/)。

创始人 Allen Wang、Eric Liu，UC Berkeley 计算机专业，大一后退学。Wang 在 2025-10 接受 KTVU 采访时 22 岁。【已证实】[KTVU 2025-10-07](https://www.ktvu.com/news/uc-berkeley-dropouts-launch-dating-service-kill-tinder-ai)。产品动机被他们反复说成：约会 App 靠「人停在 App 里」赚钱，和「尽快见面」相反；他们要做的是把人送出屏幕。【已证实】同上 KTVU；[Business Insider 2026-02-03](https://www.businessinsider.com/read-pitch-deck-college-ai-matchmaking-dating-app-ditto-seed-2026-2)。

方法上的自我叙事：不是比谁爱好相同，而是让模型从爱好读出性格（冒险、个人主义等），并用「模拟一次约会」判断会不会聊得来。Wang 举过的例子：男生攀岩、跳伞、户外，女生嘻哈、街头、滑板，表面不像，底层都冒险。【已证实】[TechCrunch 2026-08-06](https://techcrunch.com/2026/08/06/gen-z-dating-apps-like-ditto-ditch-swiping-in-favor-of-ai-matchmaking/)；同一类比更早出现在 [KTVU 2025-10-07](https://www.ktvu.com/news/uc-berkeley-dropouts-launch-dating-service-kill-tinder-ai)（嘻哈 vs 攀岩）。Wang 在 LinkedIn 写过，灵感来自 2023 年 Joon Sung Park 的生成式智能体小镇论文，以及英剧《黑镜》那集 AI 配对。【已证实】[Wang LinkedIn 帖，检索日期对应 2026-02-05](https://www.linkedin.com/posts/allen-wang-1b82761b8_i-dropped-out-of-berkeley-with-eric-liu-to-activity-7425284654850682880-pdHV)。

---

## 2. 用户旅程（按现在官网能对上的版本）

官网在 2026-11-12 仍是四步，没有第五步「先聊天再决定见不见」。【已证实】[tryditto.com](https://tryditto.com/)。

| 步 | 用户看到什么 | 用户要做什么 | 系统做什么 |
|---|---|---|---|
| 0 获客 | 校园海报 / 二维码 / 群聊链接 / 推荐码；部分学校 SEO 页写「发 iMessage 给 Ditto，用 .edu 验证」 | 点开或发短信 | 把人送进短信线程，而不是应用商店 |
| 1 入驻 | AI 自称 Ditto，先要基本身份，再问性格、兴趣、约会偏好；官网另有一份价值观问卷 | 短信里答，和/或打开问卷链接；可传自己的照片和名人「type」照片 | 建成档案。TechCrunch 记录的开场白大意是：大学生做的约会计划，不滑动、不尴尬私信，每周三 19:00 真约会 |
| 2 截止 | 文案：「周二 23:59 前提交偏好」 | 在截止前改偏好 | 进入当周批次。时区官网没写 |
| 3 周三投放 | 周三 19:00 一条 iMessage：一个人，不是信息流 | 打开短信 | 附拼贴海报（照片 + 为什么合适），以及本周可约时间表 |
| 4 排期 | 双方各自填空闲；填完后 Ditto 定时间、地点，并给几条约会提示 | 填时间；临时去不了要尽快短信通知对方 | 双方都填完才敲定。记者实测：敲定前往往只有名字和照片，电话在双方确认可约之后才给 |
| 5 见面 | 校园附近的咖啡/低压力公共场所；暑假实习季出现过城市约会 | 自己出现场 | 不提供见面前的聊天产品 |
| 6 反馈 | 约会后 AI 追问怎么样；不喜欢可以直接说原因 | 文字反馈，或改档案 | 反馈进入下一次匹配 |

来源分列，避免把不同月份的说法焊死：

- 四步文案、周二 23:59、周三 19:00、海报、双方填表后才安排时间地点和约会提示、不喜欢就说原因、临时取消不及时会 ban、约会在「你现在附近的校园」、只匹配已验证大学生：【已证实】[官网 FAQ](https://tryditto.com/)。
- 短信号码 + 一个 code 开始；开场白；入驻从姓名、性别、生日开始，再到性格、兴趣、约会偏好；有人上传名人 crush 照片：【已证实】[TechCrunch 2026-08-06](https://techcrunch.com/2026/08/06/gen-z-dating-apps-like-ditto-ditch-swiping-in-favor-of-ai-matchmaking/)。
- 问卷是链接、几乎每题可选、含「多快要一个匹配」四档、绿旗红旗、政治宗教、名人照片；匹配后先给照片和基本信息，日历链接，**双方确认可约之后才交换电话**；见面前不能联系：【已证实】亚利桑那州立大学校报记者亲自走完流程，[The State Press 2026-05-05](https://www.statepress.com/article/2026/05/campus-online-dating-ai)。
- 兴趣要双向才进入排期链接：【已证实】[Forbes 2026-07-11](https://www.forbes.com/sites/sofiachierchio/2026/07/11/these-gen-z-founders-are-reinventing-dating-apps-without-the-swipe/)。
- 创始人口径更「压缩」：匹配短信里已经带人、时间和地点，人只要出现，回来再反馈：【已证实】Wang 对 TechCrunch 的说法，同上 2026-08-06；CBS 也写成「匹配 + 完整行程，你只要出现」，[CBS San Francisco 2026-08-21](https://www.cbsnews.com/sanfrancisco/news/bay-area-startup-ditto-ai-dating-app/)。
- 供应商博客写成每次一个提案、Yes or No，约会后短信复盘：【已证实】[Photon 2026-10-01](https://photon.codes/blog/ditto-brings-ai-powered-matchmaking-to-college-campuses-through-imessage-with-photon)。这是 iMessage 供应商的客户故事，产品规则以官网和用户实测为准。

**【推断】** 现在能稳定对上的产品是「每周一个候选人 + 双方用时间表确认 + 不喜欢就反馈换下一次」，不是单方强制拽去见面，也不是先聊三天再决定。创始人采访把中间的确认步说没了，营销和实现之间有落差。PRD 不要照着口号写「用户零操作直接出现」。

**【推断】** 周三是「发匹配」的日子，不是「约会必须发生」的日子。KTVU 举的例子是周六 18:00；CBS 拍到的一场是周五晚上。见面日落在当周双方都空的时候。

首日速度，官网自己有两套说法，都留着：

- 因为还在少量学生里放，估计 1–2 周才能保证一场校园咖啡。
- 「系统升级后」，70% 用户在注册后 2 天内拿到第一次约会。

【已证实】[官网 FAQ](https://tryditto.com/)。CTO Eric Liu 在 2026-07 对 Mashable 说，周二注册的人第二天就能有约会。【已证实】[Mashable 2026-07-30](https://me.mashable.com/sex-dating-relationships/74449/ai-is-playing-matchmaker-for-college-students-with-yacht-parties)。

运营故障：短信线程忙、收不到回复时，官网让用户换一个新的 Ditto 号码重开对话；档案、偏好、匹配不重置。【已证实】[官网 FAQ](https://tryditto.com/)。Photon 称有 RCS/SMS 回落、输入中状态、图片、语音条，以及周三 19:00 同时群发。【已证实】[Photon 2026-10-01](https://photon.codes/blog/ditto-brings-ai-powered-matchmaking-to-college-campuses-through-imessage-with-photon)。**【推断】** 「换号码」写进 FAQ，说明 iMessage 通道本身不可靠，是产品风险不是边角客服。

注销：对任意一条短信回复 `DEACTIVATE MY ACCOUNT` 或 `DELETE MY ACCOUNT`，或写 team@ditto.ai。条款写明邮件退订不算退出短信服务，必须按短信关键词退。【已证实】[Terms，Last Updated 2026-01-16](https://tryditto.com/legal/tos)。隐私政策另给短信 `STOP` 退营销/短信。【已证实】[Privacy，生效 2026-07-28](https://tryditto.com/legal/privacy)。

---

## 3. 入驻问卷：问什么、没问到什么

Ditto **没有公开完整题库**。下面是能对上来源的字段，不是猜测补全的表单。PRD 若要「一题不差地抄」，做不到，也不该做。

### 3.1 记者走完的三层（2026-05，ASU）

[The State Press 2026-05-05](https://www.statepress.com/article/2026/05/campus-online-dating-ai) 的记者从短信进到一份问卷链接。她写的结构：

1. **第一层，认识你和你的 type。** 背景、兴趣、约会目标、理想对象的外形和情绪特质。她写：每一题都可选，**包括自己的照片**。
2. **匹配松紧四档（可多选语境下的选项，原文是 several options）：** 尽快给一个匹配；还行的匹配；认真匹配（大部分偏好都要对上）；「wait for the one」（所有条件都满足再给）。
3. **第二层，in-depth，仍可选。** 绿旗 / 红旗、第一次约会想做什么、政治观点、宗教信仰。
4. **第三层，ideal look。** 上传名人 crush 和其他「长得像理想型」的人的照片。
5. 另有一处「你在找什么」，可以多选。记者没有列出选项原文。
6. 她注意到：问你喜欢对方什么脸，**不问你自己的五官**。她推断外貌判断交给模型看照片。这是记者推断，不是公司确认的题干。

### 3.2 其他来源补上的题

| 字段 | 证据 |
|---|---|
| 姓名、性别、生日 | TechCrunch 2026-08-06，入驻从这三项开始 |
| 性格、兴趣、约会偏好 | 同上 |
| 名人 crush 照片，用来让 AI 理解 type | 同上；与 State Press 的 ideal look 一致 |
| 价值观、什么对你重要 | 官网 FAQ「questionnaire about your values」 |
| 自然语言偏好，而不是只勾标签 | KTVU：Wang 举例「我在 USC，想要 6 尺 2、会带花的 hot nerd」。LinkedIn 另有「rave abg、有纹身、CS 课 A+」这种句子 |
| 自由周六怎么过、好的第一次约会是什么感觉、你在乎什么 | Photon 客户故事对入驻对话的描述。供应商文案，细节可能被美化 |
| 学校、.edu 邮箱、电话 | 隐私政策「联系信息」；学院 SEO 页写用本校 .edu 验证，例如 [UPenn 页](https://tryditto.com/social/colleges/university-of-pennsylvania) |
| 推荐码 / redemption code | TechCrunch 的 signup code；隐私政策 profile 字段含 redemption code；[Play Cupid](https://tryditto.com/play-cupid) 的 link or code |
| 性取向、种族、民族、宗教、哲学、政治观点 | 隐私政策把这些列为**可能**在同意下收集的敏感信息，不代表每题必填、也不代表选项列表 |
| 城市、州、国家、邮编、年龄 | 隐私政策 demographic data |
| 社交账号链接 | 隐私政策 profile data |

### 3.3 明确没查到的题

下列在任务里被点名，但**没有任何一手页面列出题干或选项**：对方年龄范围滑杆、取向的具体选项（异性恋/双性/……）、身高体重的结构化字段、是否吸烟喝酒、关系意图的枚举（恋爱/约炮/结婚）。State Press 只写到「约会目标」「理想外形」，没有枚举。

**【猜测】** 不要在 PRD 里写「Ditto 有年龄范围题」。隐私政策收年龄，只能说明系统知道用户自己多大。

外貌怎么进模型，有三处公司侧说法，级别不同：

- Wang 对 KTVU：传统 App 听不懂「要有纹身和穿孔的人」，Ditto 让 AI 看照片。【已证实】KTVU 2025-10-07。
- Business Insider 对融资 deck 的转述：分析 agent 做图像分析、**吸引力分析**、档案打标；匹配 agent 做 vibe check、爱好匹配，再跑 date simulation（第一印象、对话流）。【已证实】这是记者对幻灯片的描述，[BI 2026-02-03](https://www.businessinsider.com/read-pitch-deck-college-ai-matchmaking-dating-app-ditto-seed-2026-2)。deck 原文未公开，部分被编辑打码。
- Liu 对 Mashable：外貌和身体特征「肯定是算法的一部分」。同文写到官网上有过一个 intern 做的 “bodycount detector”，要人脸扫描，Liu 说**不是产品的一部分**。【已证实】[Mashable 2026-07-30](https://me.mashable.com/sex-dating-relationships/74449/ai-is-playing-matchmaker-for-college-students-with-yacht-parties)。

照片可选，但匹配理由和海报都依赖照片（见第 5 节）。**【推断】** 不上传照片的人会在「只给约会对象看照片」的主路径上变成信息不足的一方，记者已经在担心这点。

AI 会把问卷聊长。UCF 学生 Anna 对 Her Campus 说：问题让她认真想过关系目标，这是加分；但 AI 像要结束了又抛出三个问题，补充信息很耗时间。【已证实】[Her Campus](https://www.hercampus.com/wellness/what-is-ditto-ai-matchmaking-app-college-students/)。该文没有署发布日期，正文把上线写成 2026 年 7 月，和 2025 年已有报道冲突，见第 9 节。学生引言仍可当作体验证据。

---

## 4. 匹配逻辑

### 4.1 创始人公开过的方法

按他们自己的话，流水线是：

1. 读偏好和档案，不把「爱好相同」当化学的定义。Wang：「chemistry is actually predictable with the right signals。」【已证实】TechCrunch 2026-08-06。
2. 用照片理解外观约束（纹身等）。【已证实】KTVU。
3. 用档案模拟一场约会：聊得起来吗、幽默对不对、vibe 和价值观像不像。【已证实】Wang 对 BI，2026-02-03。
4. Deck 上的模块名（记者转述）：分析 agent（图像、吸引力、打标）→ 匹配 agent（vibe check、hobby match）→ date simulation（第一印象、对话流）→ 再把匹配给人。【已证实】BI 对 deck 的描述。
5. Liu 对 Mashable：预先填好的静态档案预测不了化学；更重要的训练信号是约会反馈。也用和红娘的对话、用户之间的互动。他说有人已经通过 Ditto 约会 20 次，模型吃这些数据。【已证实】Mashable 2026-07-30。
6. 官网在 Mashable 采访时的 FAQ 写法（记者转述）：比对档案和偏好，「rigorous computation and simulation」。【已证实】Mashable 2026-07-30。2026-11-12 抓到的官网 FAQ 已改成更短的「价值观问卷 → 发时间地点和匹配细节」，不再写这句。

没有公开：打分公式、权重、是否稳定匹配（Gale-Shapley 一类）、性别比约束的数学、同一人能否一周被匹配多次、拒绝后的冷却、黑名单是否双向。这些都是缺口，不能脑补成算法。

### 4.2 每周批次

- 硬节奏：周二夜前提偏好，周三 19:00 发**一个**匹配。【已证实】官网。
- 不是每人每周必有。SJSU 子版块用户写：不是每周都有，可能是没找到合适的，自己约成过 2 次，感觉还行。【已证实】[r/SJSU](https://www.reddit.com/r/SJSU/comments/1sllyh2/ditto_dating_app/)，评论时间在检索结果里为 2026-04 前后。
- UCSD 用户写注册两次、做了档案，说好几天内会收到匹配，两次都没有下文。【已证实】[r/UCSD 广告帖](https://www.reddit.com/r/UCSD/comments/1qvam13/whats_up_with_all_the_ditto_stuff/)。
- 招聘启事把「某一侧密度不够」写成匹配质量问题，要和匹配团队一起看哪所学校哪一侧人少。【已证实】[Head of Community Operations，Ashby](https://jobs.ashbyhq.com/dittoai/6e8312ad-c02d-4727-8b19-f9112fb10abf)。**【推断】** 批次是按学校（或「你现在附近的校园」）切池，池子薄就空周，而不是从全国随机拉人。
- 暑假学生分散时，公司在试按城市匹配。【已证实】Forbes 2026-07-11。CBS 2026-08-21 的样本是俄勒冈州立的实习生在旧金山日本城见面，和「只在本校咖啡」的官网安全文案不一致。**【推断】** 学期中主路径是校园，假期有城市旁路，对外口径还停在校园咖啡。

### 4.3 双向确认，还是直接安排

三层说法同时存在，PRD 必须拆开，不能写成一个按钮：

| 说法 | 来源 | 含义 |
|---|---|---|
| 双方填完本周空闲，Ditto 再定时间地点 | 官网 FAQ，2026-11-12 | 确认的是**时间**，页面没写一个「我接受这个人」按钮 |
| 不喜欢就告诉 Ditto 原因，它按反馈再安排；也可以改档案 | 官网 FAQ | 拒绝是对话，不是滑走 |
| 兴趣双向之后，才发协调链接，再定时间地点 | Forbes 2026-07-11 | 有一步「对这个人感不感兴趣」 |
| 确认可约之前只有名和照片（若有），电话在双方确认后才交换；见面前不能聊天 | State Press 2026-05-05；KTVU 也说见面前不能聊 | 联系方式是闸门，不是注册即公开 |
| 短信里已经带人、时间、地点，人只要出现 | Wang → TechCrunch；CBS | 营销压缩，和 FAQ 的填表闸门冲突 |
| 一个提案，Yes or No | Photon 2026-10-01 | 供应商叙事 |

**【推断】** 真实闸门至少有两个，而且被对外说成一个：先决定要不要这个人（不喜欢就反馈），再双方交出空闲，系统定地点并交换联系方式。没有「匹配后无限聊天」这个产品面。Her Campus 写「如果双方都有兴趣，服务帮他们往见面走」，和 Forbes 一致。【已证实】[Her Campus](https://www.hercampus.com/wellness/what-is-ditto-ai-matchmaking-app-college-students/)。

### 4.4 拒绝之后

官网只承诺：说出原因和其他反馈，Ditto 按反馈再安排；可改偏好和个人信息。【已证实】FAQ。没有写：

- 是本周立刻换一个人，还是等到下周三。
- 被拒绝的一方会不会被告知原因。
- 同一个人会不会被再次配对。
- 连续拒绝会不会降权或停止发匹配。

**【推断】** 「每周三一个」加上「空周是正常现象」，立刻换人只在池子够深时做得到。UCF 学生三周、大约三次配对、没有一个想见面，说明拒绝后系统仍会在后续周三继续发，而不是把人踢出池子。【已证实】Her Campus 对 Anna 的采访。空周和「继续发但不换质量」同时被学生抱怨，见第 8 节。

隐私政策写：决定继续这个匹配时，把联系方式分享给对方；姓名和照片在匹配过程中可能被其他用户看到。【已证实】[Privacy](https://tryditto.com/legal/privacy)。这和首页「Only your date sees you」一起看：可见范围是**这一次的约会对象**，不是公开主页。【已证实】首页安全三条。

---

## 5. 介绍海报、地点、排期

### 5.1 海报里有什么

能对上的内容块，不是一张设计稿：

- 对方照片，拼贴（collage）风格。TechCrunch 说这是 Gen Z 常见审美，同类年轻团队的产品也在用。BI 写匹配短信里有照片拼贴。
- 一段短说明：为什么你们会合适。
- 对方的基本信息和兴趣（State Press：收到匹配时给照片和兴趣；若没传照片，就可能只有名字）。
- 学生侧反例：理由很宽、很空，例如「都喜欢深聊」「都是吃货」，和问卷里那些很私密的问题对不上。【已证实】Her Campus，Anna。
- 敲定之后另有约会提示，不是海报正文。KTVU：不要只聊「你也徒步吗」，而要具体到对方分享过的上次出国、或徒步鞋型。Wang 举的见面指令是：「周六 18:00，USC Village 这家咖啡馆。」

**【推断】** 海报负责「这个人是谁、为什么是你」，提示负责「坐下后第一句话」。两段都由模型生成，所以条款里专门写了生成式 AI 会幻觉，用户必须自己核对（见第 6 节）。

没有公开：海报是否含学校、年级、专业、身高、共同好友、二维码、或对方的偏好原文。不要编。

### 5.2 地点怎么选

已证实的规则很粗：

- 「精心挑选的校园地点」，为了安全和体验。官网 FAQ「Where do the dates happen?」。
- 首页安全第三条：校园咖啡。
- 「你目前附近的校园」，只匹配已验证大学生。FAQ。
- 活动由 AI 定，依据是对两人互动的模拟，而不只是丢一个地址。KTVU，Wang。
- State Press 记者收到的方案：主活动是本地咖啡馆，附加清单是逛黑胶店，或晚餐加电影。她觉得对一个号称深度匹配的产品来说太普通。
- 考虑双方大致兴趣。State Press 的描述。
- 候选商业化里出现过「向场地方抽成 10%–15%」。这是 2025-10 Wang 在谈**未来**怎么收费，不是已经上线的选址规则。KTVU。

没有公开：地点库谁维护、是否与咖啡店有合同、下雨/关门的备选、无障碍、夜间是否避免、两人校区不同时听谁的。

**【推断】** 默认是低成本、公共、短时的咖啡，附加活动是建议不是预订。和「帮你订好一整晚」不是同一产品。CBS 的日本城约会说明规则会被城市模式打破。

### 5.3 排期怎么做

1. 用户交的是**一周的空闲**，不是直接选「周六 18:00」。官网：「a scheduler to share your availability for the week。」
2. **两个人都填完**，Ditto 定时间、地点，并给提示。
3. 见面前不开放聊天，所以时间冲突不能靠两人自己商量，只能靠系统或事后短信对方取消。State Press；KTVU。
4. 临时去不了：尽快短信**对方**，否则可能被 ban 出以后的体验。官网 FAQ。没有公开 ban 的次数、时长、误伤申诉是否走同一套 appeals。
5. 招聘启事把社区运营绑进周三循环：催注册截止、处理投放当天的校园异常。【已证实】Ashby 职位说明。**【推断】** 投放是运营事件，不是纯离线算法跑完就结束。

付费活动（Sidequest，见第 7 节）的取消是另一套：邮件、活动开始前 24 小时全额退，迟到或爽约不退。那是活动票，不是每周咖啡约会的 ban 规则。【已证实】[Refunds](https://tryditto.com/legal/refunds)。

---

## 6. 安全、隐私、条款

页面均在 2026-11-12 抓取。

### 6.1 谁可以进来

- 条款开篇：不满 18 岁不得使用。服务**目前只对特定大学的学生或教职员工**开放，不保证哪所学校有；想开通可写 team@ditto.ai。【已证实】[Terms 2026-01-16](https://tryditto.com/legal/tos)。
- 官网 FAQ 和营销只说 18 岁以上大学生，不提教职员工。【已证实】官网。**【推断】** 法律资格比品牌承诺宽，教职员工进学生池是未澄清的风险。
- 验证手段是学校邮箱，不是政府身份证，也不是人脸活体（条款未要求）。学院页写 Penn、Penn State 等用本校 .edu。【已证实】[UPenn 页](https://tryditto.com/social/colleges/university-of-pennsylvania)、[Penn State 页](https://tryditto.com/social/colleges/pennsylvania-state-university)。
- Wang 对 KTVU 的安全论点：邮箱绑本校身份，做坏事会被开除。这是创始人的威慑叙事。**【已证实】**他说了这话。没有检索到任何大学和学生行为守则的合作公告。**【推断】** 不能把「会被开除」写进中国版承诺。
- 一人一号；资料不真实可暂停或终止；被 ban 过不能再注册。【已证实】Terms §2.3、§11.5。
- 条款原文：目前**不做犯罪背景调查**，不核实用户陈述，不保证用户行为和匹配质量。保留将来查性犯罪者登记等公共记录的权利。【已证实】Terms §6.1、§9.2。线下见面的责任在用户。

llms.txt 把受众写成「美国和加拿大大学生」；Play Cupid 写推荐对象必须是美国大学生。【已证实】[llms.txt](https://tryditto.com/llms.txt)、[Play Cupid](https://tryditto.com/play-cupid)。两处冲突，以产品页为准时要单独核对加拿大是否真能匹配。

### 6.2 照片谁能看

- 首页：只有你的约会对象看得到你；只和本校已验证学生匹配；校园咖啡。【已证实】官网。
- 隐私政策：匹配过程中，姓名和照片可能对其他用户可见；你决定继续后，联系方式给对方。【已证实】Privacy「Other users」。
- 档案不得含裸露、暴力、色情、冒犯内容（公司自行判断）；不得未经允许上传别人的照片。【已证实】Terms §4。
- 自己的照片在问卷里可选。【已证实】State Press。

**【推断】** 「不公开信息流」成立；「全世界只有一个人看得到」不成立到匹配计算之前——系统、AI 供应商、以及「匹配过程中的其他用户」都在可见范围内。隐私政策没有写匹配算法的操作员是否人工看照片。

### 6.3 举报和处罚

- 安全举报：邮件 team@ditto.ai，主题 “Safety report”，附账号、内容位置、日期、描述。不是应用内按钮。紧急情况让用户打当地急救电话，并写明这个邮箱**不保证实时响应**。【已证实】[Safety & Community Policy，更新 2026-11-03](https://tryditto.com/legal/safety)。
- 政策禁止：CSAM/CSEA（含 AI 生成）、人口贩运、性暴力和胁迫、非自愿私密影像和 deepfake、帮助虐待或人肉、暴力极端主义。CSAM 按规定报 NCMEC CyberTipline。
- 处罚：删内容、限制功能、暂停或终止。审查期间可以先限制。
- 申诉：另一封邮件，主题 “Account or content appeal”。3 个工作日内确认，目标 7 个工作日内书面决定；由**没有参与原决定的人**重审，可维持、改或撤销。申诉不阻断法定的儿童安全报告。【已证实】[Appeals，更新 2026-11-03](https://tryditto.com/legal/appeals)。
- 爽约：FAQ 只写「临时取消请马上短信对方，否则可能被 ban」。没有档位。付费 Sidequest 的爽约是不退款，不是同一条规则。

执法请求、证据保全、紧急披露有单独页面，入口仍是 team@ditto.ai。【已证实】[Law enforcement](https://tryditto.com/legal/law-enforcement)。

### 6.4 隐私政策里和产品有关的要点

生效日 2026-07-28，主体 Popcorn AI Tech Inc.，联系邮箱 team@ditto.ai，邮寄 604 Mission St, Fl 3, San Francisco, CA 94105，电话 +1 (347) 348-2642。【已证实】Privacy。条款里的仲裁通知地址是另一处：65 Bountiful, Irvine 92602；法律通知邮箱写成 eric@ditto.ai。【已证实】Terms §13.2、§14.8。**【推断】** 地址和邮箱不统一，是小公司条款拼装，不是两家公司。

和匹配直接相关的：

- 收集问卷、短信、照片、反馈；敏感信息（种族、民族、宗教、哲学、政治、性取向）声称仅在同意或法律允许时收集。
- 可邀请好友，收集被邀请人联系方式；要求邀请人先得到对方允许。
- 自动收集设备信息、授权后的位置、邮件打开、页面行为。用 PostHog 做会话回放（点击、滚动、移动）。
- 用途包含：建档、推荐连接、校园未开通时保留注册信息做需求判断和上线通知、营销、安全。
- **短信同意数据、手机号不出售、不交给第三方做他们自己的营销。** 可以交给发短信的服务商。
- 共享对象明确写出 AI 供应商：**Anthropic、OpenAI、Google**。条款另写使用 OpenAI 等第三方模型，用户同意把输入和内容交给这些 AI 工具。Terms §1.1。
- 用户内容许可非常宽：非独占、可转让、永久、不可撤销、全球、免费、可再许可，用于运营服务；并写明内容可被用来训练、改进模型和算法。Terms §3.4。
- 条款写：用户对聊天、短信、语音**没有隐私期待**，因为公司可以监测。Terms §5。
- 账号里有 “What Ditto knows about you” 可以查看和更新部分信息。Privacy「Your choices」。
- 不面向 18 岁以下。若发现在未取得监护人同意的情况下收集了儿童信息，按法律删除。
- 不响应浏览器 Do Not Track。
- 安全措施存在，但不保证安全。数据可能转到美国或其他国家。

### 6.5 条款里 PM 该知道的其他限制

- 服务是生成式 AI，不是人。明确写会幻觉，只供信息和娱乐，用户必须自己核对。Terms §1.1。
- 责任上限：法律规定的最高救济，或 100 美元，取高者。人身伤亡和欺诈除外。Terms §10.2。
- 加州法律；强制仲裁；集体诉讼弃权；首次受约束后 30 天内可邮件退出仲裁。Terms §13。
- 公司可随时改服务、不通知。Terms 前言。
- 国际用户自担本地法律合规。Terms §12。

---

## 7. 增长

### 7.1 扩校顺序

| 时间 | 公开范围 | 来源 |
|---|---|---|
| 2025-01 | 先在 UC San Diego 上线。Wang 说选季度制学校，学年开始得早 | KTVU 2025-10-07 |
| 2025-05-10 | Berkeley、San Jose State、UCSD | [NBC Bay Area](https://www.nbcbayarea.com/news/local/former-uc-berkeley-students-create-ai-matchmaker-app/3865750/) |
| 2025-10-07 | UCSD、Berkeley、USC；计划全国，最终想走出校园、做到按活动找人（共同创始人、导师、飞盘、跑步俱乐部） | KTVU |
| 2026-02 | UCSD 起，已到 Berkeley、USC、UCLA、UC Davis；约 4.2 万用户。Wang 说先在姐妹会群聊里传开 | [Global Dating Insights 2026-02-04](https://www.globaldatinginsights.com/featured/ditto-raises-9-2m-to-expand-imessage-college-matchmaking/)；[Wang LinkedIn](https://www.linkedin.com/posts/allen-wang-1b82761b8_i-dropped-out-of-berkeley-with-eric-liu-to-activity-7425284654850682880-pdHV) |
| 2026-03 | State Press 称 ASU 上线，并称 BI 写了 2026 扩张。记者原文如此 | State Press 2026-05-05 |
| 2026-07-11 | 约 15 万用户，点名 UCSD、Berkeley、UCLA、USC、Michigan、UT Austin；暑假试城市匹配 | Forbes |
| 2026-08-06 | 「几十所」大学，同时要解决怎么在扩校时保持安全，并最终走出校园 | TechCrunch |
| 招聘页（抓取日 2026-11-12，发布日未在页面上看到） | 「We're live at 733 US colleges」，17.6 万+ 注册 | [Ashby](https://jobs.ashbyhq.com/dittoai/6e8312ad-c02d-4727-8b19-f9112fb10abf) |
| 2026-11-12 | llms.txt 列了大量美国大学和城市攻略页。抽查 UPenn、Penn State 页用「已经能用」的口吻，并写 150,000+ students already joined、完全免费、.edu 验证 | [llms.txt](https://tryditto.com/llms.txt) 及上述学院页 |

**【推断】** 「733 所 live」和「几十所」、以及大量 SEO 攻略页，不是同一个「能稳定每周匹配」的池。招聘启事同时要求看每所学校的合格用户和匹配量、哪一侧人不够。SEO 页会把「有一个介绍页」说成「这所学校能约会」。PRD 的扩校完成标准必须是「两侧都有足够合格用户」，不能是「页上线了」。

第三方 2026-11-04 的爬虫文章称 202 个学院页里多数写着还没上线、只能排队（[programmable.marketing](https://programmable.marketing/growth-plan/ditto.ai)）。本文 2026-11-12 抽查的 UPenn / Penn State 页没有这句。**【推断】** 页面状态在变，或模板不统一。不要引用那篇文章里的精确页数当事实。

### 7.2 邀请码和 referral

- 注册线程可以带一个 code。【已证实】TechCrunch；隐私政策的 redemption code。
- 不是全程邀请制。学院页写「发短信加入 + .edu」，没有写必须有码才能注册。【已证实】UPenn 页。**【推断】** 码主要是归因，不是门槛。
- [Play Cupid](https://tryditto.com/play-cupid)：填姓名、邮箱、学校，生成专属链接。朋友用链接或码完成注册才算：要发短信给 Ditto **并且做完档案**。只点击、老用户都不算。大约一周后付。仪表盘区分「开始注册」和「qualified」。不封顶，有排行榜。允许用自己的链接给自己注册，每人只能被推荐一次。谁都能当推荐人，但被推荐人要是美国大学生才合格。禁止编造 Ditto 没有的功能和价格。
- **单次报酬金额官网这一页没有写。** 不要采用二手文章里的美元数字。
- 早期增长：超过 25% 用户来自推荐。【已证实】GDI 2026-02-04，公司自报。Photon 在 2026-10-01 仍写约 25% 增长来自学生带来朋友，没有给出更新后的比例。

### 7.3 校园大使

公司侧公开过的打法（LinkedIn 帖是检索摘要，不是全文复核）：

- 增长负责人 Elsa Cai 的帖：5 个月 21.5 万注册，市场花费每月不到 1 万美元；招「校园里大家都认识、粉丝不到 2 千」的普通人，不招大网红；在 LinkedIn 和 Instagram 班级页找人，15 分钟电话筛能不能聊天、校园认不认识他；付费 cohort、每校名额有限、每月交付摆摊、学校主题传单、TikTok 和 IG；漏斗分成听说过、做完入驻、进池、去约会。约 20% 匹配变成见面。【已证实为该帖检索摘要】[Elsa Cai LinkedIn](https://www.linkedin.com/posts/elsa-cai_i-dropped-out-of-college-to-sell-to-college-activity-7518084804777541633--pAL)。
- Campus Marketing Lead Lindley Patton 的帖：12 个新校园、100 名且还在增加的大使；点名在招的学校包括 CU Boulder、NYU、USC、UF、FSU、Ohio State、Wisconsin–Madison、ASU、UNC Chapel Hill、UC Irvine、UC Santa Barbara、Indiana Bloomington。【已证实为检索摘要】[Lindley Patton LinkedIn](https://www.linkedin.com/posts/lindleypatton_new-year-new-career-updates-im-excited-activity-7414035306573795328-hFOO)。
- 社区运营负责人职位：大使的招、训、薪酬、淘汰；从第一个大使到稳定周注册的开校 playbook；日历节奏（搬入、开学周、formal、期末）；海报印刷运输；每周报注册、合格用户、匹配量、哪段管道薄。【已证实】Ashby。

### 7.4 社媒和线下

- 校园物理广告很密。UCSD 帖标题就是「为什么到处都是 Ditto」。评论：开学前情人节档期、公司付钱贴海报、有人注册后两次没回音、有人说是骗局别乱扫二维码。海报文案被校报记录为 “Matthew cheated on me. Here's proof.”，二维码进官网；另有《黑镜》Hang the DJ 梗。【已证实】[r/UCSD](https://www.reddit.com/r/UCSD/comments/1qvam13/whats_up_with_all_the_ditto_stuff/)、State Press。
- SJSU 评论：大量「不合法张贴」的标题党海报；有人说产品本身不适合约会、体验差；有人说人群比其他约会 App 好。【已证实】[r/SJSU](https://www.reddit.com/r/SJSU/comments/1sllyh2/ditto_dating_app/)。
- UW 子版块：有人扫二维码问是不是骗局，高赞回复是「别扫随机二维码」。【已证实】[r/udub](https://www.reddit.com/r/udub/comments/1sylv1v/ditto_dating/)。
- TechCrunch：不敬的营销、机器人特技视频；Wang 说用户闻得到企业营销，特技只是漏斗顶部，留下人的是第一次约会好不好。融资上 99% 投资人是看到 LinkedIn 和 Twitter 后自己找来的，Duolingo 联合创始人 Severin Hacker 也联系过他。团队在旧金山，当时 12 名全职，并在招 “chief yacht officer” 办宣传派对。
- 游艇局：2026 年初计划 10 场美国城市游艇派对，情人节从洛杉矶开始，每场 100 个大学生配成 50 对，和校园社团、Greek life 共办。GDI 说 2025 年 10 月旧金山办过一场。GDI 2026-02-04；BI 2026-02-03 同数。
- Mashable：首席游艇官的职责包括设计流程，让盲约发生；旧金山还有跑步、读书、走路俱乐部。2026-07-30。
- UCLA 豪宅局页面：只邀请 UCLA 的 Ditto 用户抽 150 张免费票，可带朋友一起抽；21 岁以上有开酒吧；地址当天公布；从 Westwood 开车约 15–20 分钟。【已证实】[UCLA Mansion Party](https://tryditto.com/ucla-mansion-party)。
- 和匿名校园 App Yik Yak 合作过：30 题问卷，友情或恋爱匹配，三周，全国学生可加入。【已证实】State Press 2026-05-05。题库未公开。
- 早期传播路径被 Wang 归到姐妹会群聊，姐妹会分享约会对象好不好看。【已证实】其 LinkedIn 帖。

**【推断】** 增长是「一所学校的密度 × 群聊 × 付费摊位/海报 × 推荐完成档案才结算」，不是买量信息流。社媒负责让人愿意扫，产品负责周三真的有人。学生已经把标题党海报读成诈骗，这是获客方式的负债。

---

## 8. 指标和商业模式

### 8.1 公开数字（日期、口径、来源）

全部是公司对记者说的，或官网文案。没有财报。

| 日期 | 数字 | 口径 | 来源 |
|---|---|---|---|
| 2025-07-23 的 LinkedIn | 前一轮融资 160 万美元；3 个月，1.2 万+ 用户，6000+ 场约会，等待名单数万 | 公司帖 | [Wang LinkedIn](https://www.linkedin.com/posts/allen-wang-1b82761b8_i-dropped-out-of-berkeley-with-eric-liu-to-activity-7353861468909391877-za3C) |
| 2025-10-07 | 约 2.5 万用户；69% 拿到过匹配，Wang 拿来对比其声称的 Tinder 约 25%；「超过一半用户真的去约会了」；当时 5 名员工；已完成一轮未公开金额的第二轮 | 公司对 KTVU | KTVU |
| 2026-02-03 / 02-04 | 种子轮 **920 万美元**，Peak XV 领投，Gradient、Scribble Ventures、Alumni Ventures、Llama Venture 参与。BI 写累计融资 950 万美元、10 名员工、2025 年初上线产品、加州几所校园约 **4.2 万**注册。20% 匹配变成真约会。推荐占用户 25% 以上。当时免费，增长优先于变现 | BI；GDI；[TechFundingNews 2026-02-03](https://techfundingnews.com/ditto-9-2m-seed-peak-xv-ai-college-dates/) |  |
| 2026-05，维基引用官网 | 官网称超过 15.1 万人加入 | 维基编辑于 2026-05-02 检索的官网，本文未复核当时页面 | [Wikipedia](https://en.wikipedia.org/wiki/Ditto_(dating_service)) |
| 2026-07-11 | 约 15 万用户，覆盖学校含 Michigan、UT Austin；宣布融资后用户基数变为约 5 倍；仍免费；用户表示大约愿意为一场约会付 15–20 美元 | Forbes 对公司和 Wang 的采访 | Forbes |
| 2026-07-30 | 官网当时自称成功率 65%，Liu 说这是「匹配里有多少去了约会」。和 2 月、8 月的 20% 不是同一个数 | Mashable 转述官网 + Liu | Mashable |
| 2026-08-06 | **15 万**注册；匹配里约 **20%** 去约会；12 名全职；「几十所」大学；920 万美元来自 Gradient、Peak XV、Scribble | TechCrunch，Wang | TechCrunch |
| 2026-08-21 | 超过 **16 万**学生注册，产生超过 **8 万**场约会；目前免费，公司说最终要收订阅费 | CBS，Wang | CBS |
| Her Campus（文内无日期） | 「2026 年 7 月上线以来」203,949 名学生。上线月份与 2025 年报道冲突，这个人数只能当该文写作时的营销数字 | Her Campus |  |
| 2026-10-01 | 20.5 万+ 注册；约 20% 匹配变成见面；约 25% 增长来自推荐；文内校园列表仍只写 5 所，和当年夏天的扩张报道冲突 | Photon 客户故事，引用了维基等 | Photon |
| 招聘页，2026-11-12 可见 | 733 所美国大学 live，17.6 万+ 注册 | 公司招聘文案，注册数低于 10 月供应商故事 | Ashby |
| 2026-11-12 学院 SEO 页 | 文案仍写 150,000+ already joined | 模板可能没更新 | UPenn / Penn State 页 |
| Elsa Cai 帖（检索摘要，帖日期以 LinkedIn 为准） | 5 个月 21.5 万注册，月市场花费 <1 万美元；20% 匹配变成见面 | 员工帖 | LinkedIn |

20% 这个「匹配 → 见面」从 2026-02 的 GDI 说到 2026-08 的 TechCrunch，是重复最多次的公司口径。它**不是**「注册用户里 20% 去约会」。KTVU 的「超过一半用户去过约会」是用户口径；69% 是「拿到过匹配」；Mashable 的 65% 没有定义清楚，和 20% 直接矛盾。8 万场约会 / 16 万注册（CBS）也不能反推出 20%，因为一人可以有多次匹配。**【推断】** 对外把不同分母的比率放在一起讲。中国版若公开转化率，必须先写死分母：注册、合格档案、发出的匹配、双方接受、双方填了时间、实际出现。

Wikipedia 写公司成立于 2024。KTVU 写匹配工具 2025 年 1 月在 UCSD 上线。Forbes 写 Founded in 2025。Her Campus 写 2026 年 7 月上线。**【已证实】**这些句子分别出现了。**【推断】** 2024 是公司，2025-01 是第一所学校的产品，2026-07 不是首次上线。

融资用途（BI）：主要雇 AI 和增长的人，以及市场。Wang 当时说正在访谈用户愿意为约会付多少钱。

### 8.2 收费：现在收什么，说过想收什么

**现在，基础匹配是 0 美元。** 注册不开启订阅；不给小费也能继续收到以后的匹配。饭、酒、交通不包。【已证实】[Pricing & Payments](https://tryditto.com/legal/pricing)，2026-11-12。

已经写进价格页、但不是「每周一场的门票」的收费：

- **自愿小费。** 约会之后、针对已经提供的匹配服务，用户自己填美元金额，可跳过。小费不换优先级、不换更好的匹配、不构成订阅。原则上不退，重复扣款和未授权付款会查。【已证实】Pricing；Refunds。
- **Paid Sidequests。** 小组活动。有的免费，有的在场地付钱，有的在线付给 Ditto。在线支付走 Stripe，价格按活动单列，没有统一票价。开始前 24 小时邮件可全额退 Ditto 收到的那部分；晚于 24 小时或爽约不退；Ditto 取消则全额退。付给场地的钱 Ditto 不退。【已证实】Pricing；Refunds。
- **形象课。** 1 对 1 形象咨询 + 发型打理课 + 2 小时拍摄，130 张以上照片，**699 美元**一场，确认档期后再付款。页面写男女都做。【已证实】[Dating Coach](https://tryditto.com/datingcoach)。这是附属服务，不是匹配费。

说过、但价格页没有执行的未来模型：

- 2025-10，Wang 对 KTVU：可能对成功约会收 15–20 美元，或对场地收 10%–15%。当时服务免费。
- 2026-02，BI：免费，增长优先，还在问付费意愿。
- 2026-07，Forbes：仍免费，但说用户表示大约愿意付 15–20 美元一场。注意 Forbes 同一篇里 Known 的创始人在用「一杯酒 15 美元」给自己的产品定价，不要把 Known 的标价安到 Ditto 头上。Ditto 这句是 Forbes 写的 “the company says users have indicated”。
- 2026-08，CBS：最终计划收**订阅费**。

**【推断】** 三套未来模型（按成功约会、场地抽成、订阅）在十个月里都出现过，上线的却是「基础免费 + 小费 + 活动票 + 699 美元形象课」。小费被规则写成不能买更好的匹配，是在避免「给钱就配更好的人」。订阅和「尽快把人送去见面」在激励上是反的，他们自己在 KTVU 用这个理由攻击过 Tinder。

PitchBook 对投资人 Kyle Qi（Llama Ventures）的引述：他们是按社交平台而不是约会平台来投的，想做「最终的社交连接器」。【已证实】[PitchBook](https://pitchbook.com/news/articles/ai-takes-a-swipe-at-the-online-dating-scene)。和 KTVU、TechFundingNews 里「以后也做朋友、找联合创始人、找工作」一致。约会是楔子。

---

## 9. 差评和已知问题

没有 App Store / Google Play 评分：产品不在应用商店。TechCrunch、官网、Photon 都强调没有 App。因此不存在可引用的星级。

### 9.1 学生原话（校报，有名有姓或匿名）

[Her Campus](https://www.hercampus.com/wellness/what-is-ditto-ai-matchmaking-app-college-students/)：

- Alondra Silva，UCF 大四：注册比 Hinge、Tinder 轻松，概念她喜欢；用了约三周；匹配「和我交的偏好完全相反」；事先不知道是 AI，知道以后觉得「那就说得通了」；她觉得真人红娘、学生大使会更好。
- Anna（匿名），UCF 大四：问题让她想清楚关系目标；喜欢「被动约会」、不用一直滑、一周看一次。大约三次配对，没有一个想见面。UCF 池子很小。配对给的信息太少，除了外表很难产生兴趣。匹配理由空泛，和深度问卷不匹配。跟 AI 补信息很耗时。

[State Press 2026-05-05](https://www.statepress.com/article/2026/05/campus-online-dating-ai)，记者本人：

- 收到三次匹配和约会方案，觉得方案普通（咖啡、黑胶、晚饭电影），和「精密连接」的宣传不符。
- 题几乎全可选，她不相信缺答时还能公平比较。
- 「granola / indie」这种 vibe 词完全靠 AI 归类，她不信任这个归类。
- 见面前不能说话，匹配只是一张照片加一段 AI 摘要，会产生另一种疏离。
- 采访的 ASU 学生（说的是约会 App 通病，不全是 Ditto 用户）担心看不出对方意图、决策疲劳、付费墙。Ditto 免费被写成加分项。

### 9.2 Reddit（校园子版块，不是产品官方社区）

| 帖 | 能核对到的抱怨 |
|---|---|
| [r/UCSD 广告密度](https://www.reddit.com/r/UCSD/comments/1qvam13/whats_up_with_all_the_ditto_stuff/) | 海报和粉笔字过密；有人两次注册无回音；有人警告是 AI 骗局、标题党、别扫不明二维码 |
| [r/UCSD「这是真的吗」](https://www.reddit.com/r/UCSD/comments/1rpto8i/is_ditto_legit/) | 有人收到「你有一场 Ditto 约会」并被分享了电话，但想不起自己注册过。回复：功能上能用但不信任；有人注册几次从未得到约会；有人骂它是拿投资人的钱做的 LLM 套壳 |
| [r/SJSU](https://www.reddit.com/r/SJSU/comments/1sllyh2/ditto_dating_app/) | 违规张贴、标题党；「产品本身就不适合约会」；不是每周都有匹配；也有人约成 2 次且觉得人群更好 |
| [r/Purdue](https://www.reddit.com/r/Purdue/comments/1w8kini/ditto_purdue_cupid/) | 问 Purdue Cupid / Ditto 有没有人匹配到。高赞是玩梗。有人说没人在用、别费劲。一人说匹配到了但双方都鸽了，并指控 Ditto 靠翻用户短信来匹配。**后半句没有其他来源，只能当传闻，不能写进机制。** |
| [r/udub](https://www.reddit.com/r/udub/comments/1sylv1v/ditto_dating/) | 扫二维码后问是不是诈骗；回复集中在「别扫随机码」「你就是产品」 |

Mashable 2026-07-30 对 Reddit 的归纳：校园传单被说成 spam、有人说 scam、有人说允许广告商追踪数据。广告追踪在隐私政策里确实存在（兴趣广告、PostHog、第三方 cookie），「允许广告商追踪」不是空穴来风，但是 Reddit 句子本身很粗。

### 9.3 从证据里抽出的产品问题（给 PRD 的风险清单）

1. **偏好和结果对不上。** Silva 的三周体验是最清楚的质量投诉。空泛理由加重「问卷是白填的」感受。
2. **新校池子空。** UCF、Purdue 的帖和 Her Campus 一致。扩校文案跑在密度前面。
3. **空周、没回音、换号码。** 和「每周三一定有约会」的标题直接冲突。FAQ 已经承认线路忙。
4. **见面前信息太少，又不能聊天。** 想先判断人品的学生会拒见。这是设计选择，不是漏做的聊天功能；但信息少会让「为什么是这个人」更重要，而理由又被说成空话。
5. **获客像诈骗。** 出轨标题、不明二维码、突然收到约会短信却不记得注册。UW、UCSD 都出现了。
6. **安全承诺大于条款。** 营销是「验证过、私密、安全」；条款是不做背景调查、不核实陈述、线下出事公司不负责、责任上限 100 美元。Wang 的「做坏事会被开除」没有学校背书。
7. **AI 身份后置。** Silva 用了才知道是 AI。条款要求用户知道这不是人，产品前台没有把这句话说够。
8. **问卷长、题可选、结果却像在看脸。** 可选照片 + 吸引力分析（deck）+ 学生说「除了外表没有别的信息」，三件事叠在一起。
9. **iMessage 绑定。** 安卓用户被排除或掉进不稳定的 SMS。Photon 自己把「蓝气泡」当产品特性。这是美国阶层信号，不是通用架构。
10. **指标不可对账。** 20% / 65% / 69% / 一半用户 / 8 万场约会，分母不清。对外承诺「成功率」会反噬。
11. **官网曾出现 intern 的 bodycount detector。** Liu 说不是产品。它和约会品牌放在同一域名上，已经够成为信任事故。Mashable 2026-07-30。
12. **「收到电话但想不起注册」。** 可能是推荐人提交了别人的联系方式（隐私政策允许收集被邀请人联系方式），也可能是用户忘了。两种都成立时，产品缺乏「这是谁把我加进来的」提示。r/UCSD 只证明有人这样投诉，没有证明是哪一种。

Mashable 同时记录了公司提供的正面引言：USC 的 Adriana 说跟 Ditto 短信不难；Berkeley 的 Noah 说比下载 App 摩擦小。样本由公司挑选后发给记者，不能当满意度。

---

## 10. 口径冲突（写 PRD 前先选边）

| 议题 | A | B | 本文采用 |
|---|---|---|---|
| 见面前要不要双方点头 | FAQ：填时间表；不喜欢就反馈 | Wang/CBS：人、时间、地点一起到，你出现就行 | 以 2026-11 官网 FAQ + State Press 实测为准：有闸门。口号是压缩 |
| 匹配→约会转化 | 多次 20% | Mashable 转述官网 65%；KTVU「超过一半用户去约会」 | 20% 是重复最多的**匹配**口径；其他是不同分母或未定义。全部自报 |
| 上线学校数 | TechCrunch「几十所」 | 招聘「733」；SEO 页一大片 | 能匹配的学校远少于有页面的学校。用「两侧密度」定义上线 |
| 首次上线 | 2025-01 UCSD（KTVU） | Her Campus 写 2026-07 | 采信 2025-01。Her Campus 的月份视为笔误或指南方大推 |
| 谁能注册 | FAQ：大学生 18+ | 条款：特定学校的学生**或教职员工** | 品牌是学生；法律文本含教职员工 |
| 地域 | llms.txt：美加 | Play Cupid：美国大学生 | 未再核实加拿大是否真能进池 |
| 注册规模 | 2026-08 CBS 16 万+、Photon 10 月 20.5 万、Elsa 帖 21.5 万 | 招聘页 17.6 万、11 月 SEO 页仍写 15 万+ | 只按日期引用，不取最大值当「现有用户」 |
| 未来收费 | 按约会 15–20 美元；场地抽成；订阅 | 价格页：基础 0 元，小费和活动另计 | 已上线的是后者。15–20 是付费意愿访谈，不是标价 |

---

## 11. 对中国版的启示

决策已定：独立 H5，edu.cn 认证，第一阶段不写代码。下面只说产品该抄什么。

### 值得照搬

1. **一周一个人，而不是信息流。** 稀缺是产品，不是限额缺陷。它同时降低决策疲劳，并给「周三有一件社交上的事」一个仪式。官网、TechCrunch、Her Campus 里喜欢它的学生，夸的都是这件事。
2. **红娘把排期和地点做完，见面前不开放聊天。** 要打的是「聊死在『在吗』」而不是「让人多看几个档案」。State Press 采访的传播学者把 flake/ghost 放在见面前；Ditto 用结构躲开这段。中国版若加回长聊天，就回到 Hinge。
3. **介绍物必须包含「为什么是你」，不能只是照片。** 海报 + 短理由是见面意愿的主要燃料。学生已经证明：理由空泛时，深度问卷会变成背叛。
4. **第一次见面默认公共、短、低消费、在人群里。** 校园咖啡是安全设计，不是没创意。附加活动是建议，不替用户花钱。
5. **约会后的反馈写进下一次，而不是只做 NPS。** Liu 把反馈当成比静态档案更重要的信号。这是「越用越懂」的唯一公开机制。
6. **照片默认只给这一次的对象，没有可浏览的主页。** 首页第二条是隐私卖点，也减少公开处刑和比对。
7. **学校邮箱把池子收进同一个生活半径。** Wang 对密度的解释（18–24 岁、地理近、见面成本低）对大学城成立。推荐要结算在「做完档案」而不是点击，Play Cupid 这点值得抄。
8. **一所学校两侧都有人再开匹配，页面可以先排队。** 招聘启事已经把「哪一侧不够」当成运营指标。Her Campus 的 UCF 是反面教材：页开了，池是空的，匹配像随机。
9. **基础匹配免费，直到真的有人见面。** 学生反感为「还没见着人」付费。价格页把小费写成不能买更好的匹配，这个边界要保住。
10. **大使找「圈子中心的普通人」，交付物是摆摊、传单、本校本群，不按粉丝计价。** Elsa Cai 帖和 Ashby 职位说明是同一套。漏斗要拆到「进池」和「去见面」，不只看注册。

### 需要改造

1. **通道。** iMessage、蓝气泡、换号码重开线程，都不能进中国版。H5 做档案、问卷、海报、时间表；通知走用户真的会看的消息渠道。不要为了「像朋友发短信」去借一个不稳定的短信网关。Photon 解决的是美国 iMessage 没有官方 agent 接口，不是用户需求本身。
2. **认证。** edu.cn 比 .edu 更脏：教职工、继续教育、校友邮箱、二级学院域名、国际学生没有 edu.cn。Ditto 条款已经不小心把 faculty 写进来。中国版应白名单「在读学生」域名或学邮规则，并和毕业年份或在读证明分开，不要「后缀对了就算」。
3. **日历。** 周二 23:59 / 周三 19:00 没有公开时区，照抄会在跨校实习时崩。中国学期有晚课、考试周、寒暑假、实习季。Ditto 自己在暑假改成城市匹配（Forbes、CBS 日本城）。中国版要事先定义：学期中只匹配本校，实习期匹配同城，空校周不发「你失败了」而发「本周池子暂停」。
4. **地点。** 校园咖啡不是中国校园的默认公共约会。食堂、奶茶、图书馆周边、校内商业街更真实。原则留下（公共、短、低消费、不进对方宿舍），地点库按学校重做。不要学习「向咖啡店抽成」直到真的有合同和用户知情。
5. **问卷语言。** granola / indie / hot nerd / rave abg 是美国校园黑话。中国版要收集的是同样的三层：我是谁、我要谁、我能多将就；但选项和例子必须来自中国学生的原话，不能翻译美国亚文化。政治、宗教、性取向在隐私政策里是敏感信息。即使用，也必须单独同意、可拒绝、且拒绝仍能匹配，Ditto 的「全题可选」在这点上是对的。
6. **确认闸门要做成看得见的两步。** Ditto 把「我接受这个人」和「我这周有空」混在口号里。中国版应明示：先看海报选择去或不去；双方都去，再填空闲；系统给时间和地点；电话或微信只在这一步交换。拒绝原因只进模型，不转发给对方。
7. **海报理由要能指回用户说过的话。** 空泛的「都喜欢深聊」是 Her Campus 里的信任事故。生成理由时应引用双方档案里的具体点，并允许用户说「这句是错的」，这句错误进入下周，而不是再生成一句同样空的话。
8. **性别比和一侧密度。** 美国团队已经在招人专门看「哪一侧不够」。中国校园若异性恋池严重不平衡，一周一个人会变成长期空周或反复违偏好。扩校标准、等待文案、要不要允许「本周没有合适的人」都要在 PRD 写出来。Ditto 没有公开他们怎么处理不平衡。
9. **收费实验。** 不要第一阶段上订阅。若试付费，Ditto 价格页的结构比创始人采访更接近可执行方案：见面免费，事后自愿小费且不改变匹配，活动票单独标价。15–20 美元是美国访谈，不是中国标价。699 美元形象课与红娘信任无关，不要做成启动功能。
10. **安全运营要比 Ditto 前台更显眼。** 他们把举报放在邮件主题 “Safety report”，学生找不到。H5 里每一次匹配都应有举报入口，并写清处理时效。申诉的「非原审核人、几个工作日回复」可以抄，邮箱孤岛不要抄。

### 应该避免

1. **吸引力打分。** BI 对 deck 的转述里有 attractiveness analysis。这会把产品做成外貌排序，并和「价值观匹配」的宣传对着干。学生已经感觉结果在看脸。
2. **色情化或羞辱性的增长道具。** bodycount detector（即便是 intern 的）、 “Matthew cheated on me” 标题党、让人以为是钓鱼的二维码。UW 和 UCSD 的反应是「别扫」。edu.cn 用户更不会为标题党交出学邮。
3. **「验证 = 安全」的话术。** 条款写明不做背景调查、不核实自述。Wang 说的「会被学校开除」没有制度。中国版最多承诺「我们核对了学邮」，并告诉用户这不能证明人品。
4. **把聊天和照片默认送去境外通用模型，再用永久不可撤销许可覆盖训练。** Terms §3.4 和隐私政策点名的 Anthropic、OpenAI、Google，在中国的个人信息合规上不能原样搬。敏感字段（性取向、宗教、政治、人脸）需要单独、可撤回的同意，不能藏在「继续使用即同意」。
5. **见面前零信息 + 理由空泛 + 不能拒绝就换人。** 这是 Silva / Anna 的组合拳。稀缺可以抄，随机不可以。
6. **用 SEO 页数量冒充开校。** 733 所 “live” 与学生「池子是空的」同时为真。没有两侧合格用户就不要发第一次匹配。
7. **公开一个没有分母的成功率。** 20%、65%、69%、一半用户，已经互相打架。
8. **爽约规则只有一句「否则 ban」。** 没有档位、没有例外（生病、考试）、没有用户找得到的申诉路径。FAQ 的 ban 和 2026-11 的申诉页没有写明是不是同一件事。中国版要写清警告、暂停、永久，以及误伤怎么恢复。
9. **把教职工写进学生池。** 条款已经这样写了。校园红娘若混进老师，安全属性会反过来。
10. **为了像 Ditto 而做 App 内的无限会话式问卷。** Anna 的投诉是 AI 停不下来。H5 应有可见进度、可跳过、可保存，短信只承担通知和反馈，不承担 40 轮闲聊。

---

## 12. 信息缺口

下列事项本次没有一手来源，PRD 不能写成「Ditto 就是这样」：

- 问卷全文、选项枚举、必填项最终名单。尤其没有：期望年龄范围、性取向选项、身高体重结构化字段、关系意图枚举。
- 周二 23:59 和周三 19:00 的时区。
- 拒绝后是立即换人还是下周；被拒方是否知情；同一对是否会再次出现。
- 匹配算法的约束：性别比、距离、一人一周最多一次、照片缺失如何降权。吸引力分析只有 deck 转述，没有权重。
- 地点库的数据来源、是否预订座位、坏天气和关门怎么办。
- 海报的固定字段清单和视觉规范。
- ban 的触发次数、时长、是否可申诉；FAQ 的 ban 和 appeals 页是否打通。
- Play Cupid 每成功一名的金额、结算方式和税务表格。
- 小费的实际支付转化率、Sidequest 的真实票价例子（价格页说逐场展示）。
- 15–20 美元付费意愿的样本量和问法。
- 加拿大用户是否真的进入匹配池。
- 2026-11 的真实「可匹配学校」名单。招聘 733 与 SEO 页都不可靠。
- Daily Californian 原文（维基引用了标题 “New AI-powered dating app Ditto does all the work for you”，本次没打开到正文）。
- 独立的留存、男女比、首周空匹配率、爽约率。公司没公布。
- Purdue 帖里「翻用户私人短信来匹配」没有第二来源，不采用。

---

## 来源

一手或接近一手，均在 2026-11-12 打开过正文，除非另行注明：

- https://tryditto.com/
- https://tryditto.com/legal/tos
- https://tryditto.com/legal/privacy
- https://tryditto.com/legal/safety
- https://tryditto.com/legal/appeals
- https://tryditto.com/legal/refunds
- https://tryditto.com/legal/pricing
- https://tryditto.com/legal/law-enforcement
- https://tryditto.com/play-cupid
- https://tryditto.com/llms.txt
- https://tryditto.com/datingcoach
- https://tryditto.com/ucla-mansion-party
- https://tryditto.com/social/colleges/university-of-pennsylvania
- https://tryditto.com/social/colleges/pennsylvania-state-university
- https://techcrunch.com/2026/08/06/gen-z-dating-apps-like-ditto-ditch-swiping-in-favor-of-ai-matchmaking/
- https://www.hercampus.com/wellness/what-is-ditto-ai-matchmaking-app-college-students/
- https://en.wikipedia.org/wiki/Ditto_(dating_service)
- https://www.cbsnews.com/sanfrancisco/news/bay-area-startup-ditto-ai-dating-app/
- https://www.businessinsider.com/read-pitch-deck-college-ai-matchmaking-dating-app-ditto-seed-2026-2
- https://www.statepress.com/article/2026/05/campus-online-dating-ai
- https://www.ktvu.com/news/uc-berkeley-dropouts-launch-dating-service-kill-tinder-ai
- https://www.nbcbayarea.com/news/local/former-uc-berkeley-students-create-ai-matchmaker-app/3865750/
- https://www.forbes.com/sites/sofiachierchio/2026/07/11/these-gen-z-founders-are-reinventing-dating-apps-without-the-swipe/
- https://www.globaldatinginsights.com/featured/ditto-raises-9-2m-to-expand-imessage-college-matchmaking/
- https://techfundingnews.com/ditto-9-2m-seed-peak-xv-ai-college-dates/
- https://me.mashable.com/sex-dating-relationships/74449/ai-is-playing-matchmaker-for-college-students-with-yacht-parties
- https://pitchbook.com/news/articles/ai-takes-a-swipe-at-the-online-dating-scene
- https://photon.codes/blog/ditto-brings-ai-powered-matchmaking-to-college-campuses-through-imessage-with-photon
- https://jobs.ashbyhq.com/dittoai/6e8312ad-c02d-4727-8b19-f9112fb10abf
- https://www.reddit.com/r/UCSD/comments/1qvam13/whats_up_with_all_the_ditto_stuff/
- https://www.reddit.com/r/UCSD/comments/1rpto8i/is_ditto_legit/
- https://www.reddit.com/r/SJSU/comments/1sllyh2/ditto_dating_app/
- https://www.reddit.com/r/Purdue/comments/1w8kini/ditto_purdue_cupid/
- https://www.reddit.com/r/udub/comments/1sylv1v/ditto_dating/

检索摘要、未逐段打开全文：Wang 与 Elsa Cai、Lindley Patton 的 LinkedIn 帖（URL 见正文）。维基引用但未打开正文：The Daily Californian。
