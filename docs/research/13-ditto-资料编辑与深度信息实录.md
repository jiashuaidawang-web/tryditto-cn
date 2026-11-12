# 13 Ditto 资料编辑与深度信息实录（职场账号只读走查）

- 采集日期：2026-11-12 晚。视口 390×844。截图在 `assets/ditto-onboarding/shots/profile-edit-2/`。
- 账号：已入驻的职场测试账号（isStudent = No，1996 年生，性别 Man，只期望 Women）。和 `09` §10 是同一个账号。
- 只读：每个编辑页只打开、截图、抄 DOM，然后点左上 Back 离开。没有点 Done / Send / Block / Add，没有输入，没有切换任何胶囊。会话内 `api.ditto.ai` 只出现 GET 和埋点（`web-activity/page-view`、`user/photo-effort`），没有任何资料写请求。【已证实】
- 对照底稿：`assets/ditto-app/questionnaire.json`、`08`、`09`（尤其 §3 和 §10）。

证据标记沿用 `09`：

- **【已证实】**：当次 DOM 文本、计算样式、网络记录，或截图里能直接读到的事实。
- **【推断】**：用多条已证实事实拼出的判断。

## 0. 先说意外：首页、Where、You 被重定向回入驻照片页

这次直接打开 `/you`、`/where`、`/` 时，地址都被改写成 `/onboarding`，落在入驻的照片步。【已证实】

- 页面文案和入驻照片步相同：`Let's add at least 2 photos 📷`、`Choose photos that show your vibe`、`Photos from different moments help us find better matches. You can swap them anytime.`、`Hold and drag a photo to reorder.`。顶栏有返回、Ditto 头像、锁，进度条第二段已满。
- 同时弹一条 toast（sonner 结构，`data-title` / `data-description`）：`Your photos need a clearer shot of you` / `We couldn't tell which person is you. Add one that clearly shows your face.` 截图 `41-redirect-photo-issue-banner.png`（只截了文字，没截照片格）。
- 圆形 Next 是 `aria-disabled=false`。没有点。
- `GET /user/status` 返回 `onboardingStatus: "noPhotoMatchable"`、`photoIssue: { reason: "photo_unidentifiable" }`、`enrollmentStatus: "enrolled"`、`onboardingVariant: "professional"`、`onboardingFlow: "chat"`、`matchingStatus: "Waiting"`、`pool: ["wednesday"]`。【已证实】
- `GET /face-verification/status`：`mode: "shadow"`、`required: false`、`status: "retry_capture"`、`reason: "turn_not_detected"`，最后更新时间是 11-06。【已证实】
- 照片页加载后前端自动发了 `POST /user/photo-effort`，请求体 `{"score":64,"scoreVersion":"2026-10-23-bounded-decode"}`，返回 204。这是页面自己发的埋点，不是我们点出来的。【已证实】

还能正常打开的路由：`/edit-profile`、`/deep-info`、`/blocklist`、`/account`，以及所有 `/onboarding/edit/<id>`。【已证实】

结论：**「已入驻但照片不合格」会被路由守卫拦回入驻照片步**。首页三个 Tab 都进不去，资料编辑类页面不受影响。【推断】守卫看的是 `onboardingStatus`。这个状态是何时出现的不清楚：本次开始时页面停在 `/edit-profile`，没有经过首页。

因为首页进不去，☰ 菜单这次没有复查，以 `08` 第 11 条为准。

中国版启发：照片审核失败要有一个明确的「补照片」回流页，不能只让用户在首页卡住。Ditto 的做法是复用入驻照片步，加一条 toast 说原因。

## 1. 页面清单

| # | 页面 | 路由 | 标题 / 气泡 | 截图 |
|---|---|---|---|---|
| 00 | 资料页首屏 | `/edit-profile` | `Profile` | `00-edit-profile-full.png`（滚动容器在内部，整页截图只截到首屏；整页见 `profile-edit/03-profile-full.png`） |
| 01 | 工作 | `/onboarding/edit/occupation` | what do you do for **work**? | `01-work.png` |
| 02 | 学历 | `/onboarding/edit/educationLevel` | what's the highest level of **education** you've completed? | `02-education.png` |
| 03 | 学历要求 | `/onboarding/edit/educationImportance` | does your match's **education** have to be at least yours? | `03-education-importance.png` |
| 04 | 首约预算 | `/onboarding/edit/firstDateBudget` | what’s ur **budget** for a first date? | `04-first-date-budget.png` |
| 05 | 匹配节奏 | `/onboarding/edit/matchAccuracy` | how do u want ditto to **match u** rn? | `05-match-accuracy.png` |
| 06 | 怎么知道我们的 | `/onboarding/edit/referralSource` | how did u **hear about us**? | `06-hear-about.png` |
| 07 | 对方第一眼该知道 | `/onboarding/edit/firstImpression` | **first thing** your match should know? | `07-first-impression.png` |
| 08 | 雷区 | `/onboarding/edit/redFlags` | **dealbreakers**: what should Ditto avoid? | `08-dealbreakers.png` |
| 09 | 是否学生 | `/onboarding/edit/isStudent` | are you currently a **student**? | `09-is-student.png` |
| 10 | 生日 | `/onboarding/edit/birthday` | when's your **birthday**? | `10-birthday.png` |
| 11 | 性别 | `/onboarding/edit/gender` | what's your **gender**? | `11-gender.png` |
| 12 | 族裔 | `/onboarding/edit/ethnicity` | What's your **ethnicity**? | `12-ethnicity.png` |
| 13 | 身高 | `/onboarding/edit/height` | How **tall** are you? | `13-height.png` |
| 14 | 想约谁 | `/onboarding/edit/expectedGender` | Who are you looking to date? | `14-expected-gender.png` |
| 15 | 年龄范围 | `/onboarding/edit/ageRange` | What's your preferred **age range**? | `15-age-range.png` |
| 16 | 身高要求 | `/onboarding/edit/expectedHeightRange` | how **tall** should your match be? | `16-height-range.png` |
| 17 | 族裔偏好 | `/onboarding/edit/expectedEthnicity` | Any **ethnicity** preferences? | `17-expected-ethnicity.png` |
| 20 | 深度信息 | `/deep-info` | `Deep Info` | `20-deep-info.png` |
| 21 | 宗教 | `/onboarding/edit/religious` | what's your **religion**? | `21-religion.png` |
| 22 | 宗教重要度 | `/onboarding/edit/religionImportance` | does your match have to share your religion? | `22-religion-importance.png` |
| 23 | 政治 | `/onboarding/edit/politicalBeliefs` | what's your **political vibe**? | `23-politics.png` |
| 24 | 政治重要度 | `/onboarding/edit/politicsImportance` | does your match have to share your politics? | `24-politics-importance.png` |
| 30 | 屏蔽名单（空态） | `/blocklist` | `Blocklist` | `30-blocklist-empty.png` |
| 31 | 添加屏蔽（底部抽屉） | `/blocklist`，点 `Add block` | `Add block` | `31-blocklist-add-sheet.png` |
| 40 | 账号设置 | `/account` | `Account Settings` | `40-account.png` |
| 41 | 重定向 toast | `/you` → `/onboarding` | `Your photos need a clearer shot of you` | `41-redirect-photo-issue-banner.png` |

共 27 张截图。粗体是气泡里加粗的关键词，按截图读出。`08` 已记过的 `intention`（多选示例）、`name`（文本示例）和 `09` §10 的 `hobbyRanking` 这次没重截。

## 2. 资料页 `/edit-profile`

### 2.1 结构

- 顶栏：左 `Back to profile`（40×40 圆形，chevron-left），中间 `Profile`。【已证实】
- 三组，组标题是 `h2`，14px / 500，白字，带 `text-shadow: 0 1px 4px rgba(0,0,0,0.25)`，左右内边距 20px：`Tell Ditto about yourself`、`Tell Ditto your type`、`Pics of your vibe`。【已证实】
- 每组是一张毛玻璃卡：底 `rgba(255,255,255,0.04)`，`backdrop-filter: blur(6px)`，圆角 24px，阴影 `inset 0 0 8px rgba(255,255,255,0.15), 0 8px 24px rgba(0,0,0,0.1)`，左右外边距 16px。【已证实】
- 每行是一个 `<button id="application-summary-<字段>">`，aria-label 为 `Edit <问句>`，内边距 20px，高 84px。上面一行小标签 12px（`text-xs`），透明度 0.9。下面是答案，19px、行高 24px、白字，可换行。右侧 19px 的 lucide 铅笔，线宽 1.7。【已证实】
- 字体：Arial / Helvetica Neue / Helvetica。body 底色 `rgb(35,48,57)`，蓝色渐变和颗粒是上层纹理。【已证实】

### 2.2 行顺序、字段 id 与当前值

| 组 | 行标签（资料页） | 字段 id（按钮 id 与 `/onboarding/edit/` 后缀） | 当前显示值 |
|---|---|---|---|
| about | What’s your name? | name | （测试姓名） |
| about | When is your birthday? | birthday | 10/09/1996 |
| about | What’s your gender? | gender | Male |
| about | What’s your ethnicity? | ethnicity | White |
| about | How tall are you? | height | 6ft0in |
| about | Share your hobbies and interests | hobbyRanking | sport |
| about | Are you currently a student? | isStudent | No |
| about | What do you do for work? | occupation | （测试文本） |
| about | Highest level of education | educationLevel | Graduate degree |
| about | How did you hear about us? | referralSource | Google |
| about | What's the first thing your match should know? | firstImpression | （测试长文本） |
| about | Dealbreakers: what should Ditto avoid? | redFlags | （测试长文本） |
| type | What are you looking for right now? | intention | Life partner, Serious relationship, Casual dates, New friends, Not sure yet |
| type | Who do you wanna date? | expectedGender | Women |
| type | What age range would you like to date in? | ageRange | 26–34 |
| type | How tall should your match be? | expectedHeightRange | I don't care about height |
| type | What ethnicities are you attracted to? | expectedEthnicity | No Preference |
| type | How do you want Ditto to match you rn? | matchAccuracy | More dates, faster |
| type | Budget for a first date (per person) | firstDateBudget | No preference |
| type | Does your match's education have to be at least yours? | educationImportance | Doesn't matter |

全部【已证实】。

要点：

- **资料页的行标签不是气泡原句。** 例如 `What ethnicities are you attracted to?` 对应气泡 `Any ethnicity preferences?`；`Who do you wanna date?` 对应 `Who are you looking to date?`；`Highest level of education` 对应长句。实现时要两套文案：资料行标签和气泡问句。【已证实】
- **显示值是存储值，不是选项文案。** 性别显示 `Male`，编辑页的胶囊是 `Man`。学历显示 `Graduate degree`，胶囊是 `graduate degree`。预算显示 `No preference`，胶囊是 `🤷 no preference`。【已证实】这也解释了 `questionnaire.json` 写 Male / Female：JSON 记的是存储值。【推断】
- 资料页没有的入驻题：`location`（在 `/where`）、`photos`（在本页底部）、`datingHelp`、`schoolYear`（非学生）、两道体位题（Man + 只要 Women 时本来就不出现）。【已证实】`datingHelp` 在 `user/profile` 里有值，但前端没有任何入口能改。
- 宗教、政治四题不在本页，在 `/deep-info`。【已证实】

### 2.3 `user/profile` 的字段形状（只记键，不记个人值）

`GET https://api.ditto.ai/user/profile` 返回 `data`：【已证实】

- `basicInfo`：`isStudent`、`name`、`gender`、`expectedGender[]`、`birthday`（`YYYY-MM-DD`）、`ageRange[2]`、`ethnicity[]`、`expectedEthnicity[]`、`height`（字符串 `6ft0in`）、`expectedHeightRange`（字符串）、`physicalAttraction[3]`、`hobbyRanking`、`intention[]`、`matchAccuracy`、`occupation`、`firstDateBudget`、`referralSource`、`redFlags`、`educationLevel`、`educationImportance`、`politicalBeliefs`、`politicsImportance`、`religious[]`、`religionImportance`、`firstImpression`、`datingHelp[]`、`age`。
- `images[]`：每张有 `original`、`thumbnail`、`description`、`tags[]`（例：`professional`、`headshot`、`neat`、`clean-cut` 等 10 个）、`isSinglePerson`、`isGroupPhoto`、`isShowcasingFitness`、`isInterestOrLifestyle`、`presentableScore`、`webFaceDetected`。
- `datingImages[]`、`idealDateImages[]`（Deep Info 的理想型照片，本账号为空）、`issues[]`、`onboardStep`、`onboardStep2`、`onboardingStatus`、`smsOptOut`、`locationMatchingEnabled`、`locationSetupRequired`、`poolInfo`（空对象）。

值得注意：

- `physicalAttraction` 是 `["I don't care about height", "", ""]`，看起来是「身高要求 + 另外两个空槽」的三元组。另外两槽在 UI 上没找到入口。【推断】
- `datingHelp` 存的是 `Starting conversations`、`Keeping a convo going`、`Telling if they're into me`、`Confidence`、`Just get me a date`。其中 `Starting conversations` 和 `Confidence` 不是 `09` 实机看到的 `approaching girls` / `being more confident`。可能是旧版选项，也可能是展示文案与存储值分离。【推断】
- 每张照片都被打了风格标签和「是否单人 / 健身 / 生活方式」布尔值，有 `presentableScore`。说明照片是 AI 匹配的一路输入。【已证实】字段存在；【推断】用途。

## 3. 编辑页通用规格

和入驻同一套组件（`ob-glass` / `ob-selected` 胶囊、白气泡、文本条），差别在顶栏和提交按钮。【已证实】

- **顶栏**：只有左上返回（aria-label `Back`）和居中 `Edit Profile`（16px / 500）。没有 Ditto 头像、没有锁、没有进度条。【已证实】
- **气泡上方的小字 `Ditto`**：12px，白，左内边距 16px。【已证实】
- **提交按钮**：选择类和文本类都是右下 `Done`，96×48，白底 `rgba(255,255,255,0.95)`，字 `rgb(2,43,78)` 16px / 700，圆角 9999px，`backdrop-filter: blur(10px)`。打开时就是 `aria-disabled=false`，不需要先改动。【已证实】文本类的 `Done` 放在文本框里面右下角（`01-work.png`）。
- **生日例外**：生日页沿用入驻的圆形箭头 `Send`（72×48，白底 `rgb(255,255,255)`，图标 `rgb(26,26,26)`），没有 `Done`。打开就可点。【已证实】
- **Back 去向**：从资料页进的回 `/edit-profile`，从 Deep Info 进的回 `/deep-info`。两者顶栏都写 `Edit Profile`。【已证实】
- **选中态**：单选和多选都是品红 `#E40089`、粗体。**多选**右侧有白色圆形对勾（族裔、宗教、想约谁、族裔偏好），**单选**没有对勾（是否学生、学历、预算、匹配节奏、怎么知道我们的、性别）。【已证实】截图对比 `12-ethnicity.png` 与 `09-is-student.png`。
- **选中胶囊变宽**：选中胶囊宽 260px、左缘 x=114；未选 208px、左缘 x=166。也就是选中后向左伸出 52px，右缘对齐不变。【已证实】
- **多选 / 单选的 DOM 差异**：多选题的选项按钮都带 `aria-disabled="false"`，单选题的选项没有这个属性。【已证实】可以据此区分题型。
- **Other 胶囊**：外层同 `ob-glass`，里面是一个 `aria-label="Other"` 的按钮，文字 `Other` 透明度 60%，右侧 16px 铅笔，颜色 `#bcc9d3`。这次没点开。【已证实】`09` 记过点开后是同名占位输入框。
- **Private 提示**：气泡下右对齐，锁图标 + `Private. Only used to match you`。哪几题有，见下面逐页。

## 4. 逐页原文

### 01 工作 occupation

- 气泡：`what do you do for work?`，`work` 加粗。
- 无副文案，无 Private 提示。【已证实】
- textarea，占位 `e.g. software engineer at a tech company`，已回填当前答案。DOM `maxLength` 为 -1（未设）。JSON 写 `maxLength: 120`，前端没有体现。【已证实】
- 文本框样式：底 `rgba(8,23,33,0.25)`，圆角 24px，内边距 `16px 20px 72px`（底部留给 Done），`backdrop-filter: blur(20px) brightness(0.75) saturate(1.4)`，16px 白字。【已证实】
- `Done` 在框内右下，可点。

### 02 学历 educationLevel

- 气泡：`what's the highest level of education you've completed?`，`education` 加粗。
- Private 提示：有。JSON 没标。【已证实】
- 单选：`high school`、`college degree`、`graduate degree`、`prefer not to say`。无 emoji，无 Other。
- 预选：`graduate degree`。【已证实】

### 03 学历要求 educationImportance

- 气泡：`does your match's education have to be at least yours?`，`education` 加粗。
- Private 提示：有。
- 单选：`doesn't matter`、`kinda matters`、`super important`。
- **预选缺失**：资料页显示 `Doesn't matter`，存储值也是 `Doesn't matter`，但编辑页三项都没有选中。`Done` 仍然可点。【已证实】`03-education-importance.png`。
- 原因不能简单归为大小写：学历存的是 `Graduate degree`、选项是 `graduate degree`，回填正常；宗教 / 政治重要度存的是小写 `doesn't matter`，回填也正常。只有这题存 `Doesn't matter` 时没选中。【已证实】三组对照；【推断】这题的存储值到选项的映射表漏了一项。空选状态下点 Done 会存什么没有测。中国版要让存储值和选项用同一个枚举 key，不要拿展示文案做匹配。

### 04 首约预算 firstDateBudget

- 两条气泡：`what’s ur budget for a first date?`（弯引号，`budget` 加粗）和 `per person, roughly`。
- Private 提示：有。
- 单选，emoji 在左：`☕ under $20`、`🥂 $20–50`、`🍝 $50–100`、`✨ $100+`、`🤷 no preference`。价格里是 en dash。
- 预选：`🤷 no preference`。【已证实】

### 05 匹配节奏 matchAccuracy

- 气泡：`how do u want ditto to match u rn?`，`match u` 加粗。
- Private 提示：有。
- 单选：`⚡️ More dates, faster`、`⚖️ A steady mix`、`🎯 Fewer, better matches`、`💎 Wait for the one`。
- 预选：`⚡️ More dates, faster`。【已证实】
- 和 `09` 的 matchingStyle 同文。字段名实为 `matchAccuracy`。

### 06 怎么知道我们的 referralSource

- 气泡：`how did u hear about us?`，`hear about us` 加粗。
- 无 Private 提示。【已证实】
- 单选：`Friend`、`Instagram`、`Facebook`、`TikTok`、`X (Twitter)`、`LinkedIn`、`Google`、`RedNote (Xiaohongshu)`，最后是 `Other`（带铅笔）。
- 预选：`Google`。【已证实】
- **和学生路径不同**：`09` 学生路径是 `Friend`、`Poster`、`Tabling`、`Instagram`、`TikTok`、`X (Twitter)`、`RedNote (Xiaohongshu)`、`Google`、`Other`。职场版去掉了 `Poster`、`Tabling`（校园地推），加了 `Facebook`、`LinkedIn`，顺序也变了。【已证实】两边原文；【推断】选项集按 `onboardingVariant` 切换。

### 07 对方第一眼该知道 firstImpression

- 两条气泡：`first thing your match should know?`（`first thing` 加粗）和 `this helps your match get to know you better`。【已证实】补齐了 `09` 没抄到的第二句。
- 文本框上方右对齐：纸飞机图标 + `your match will see this`。【已证实】
- 无 Private 提示（这题本来就是给对方看的）。
- textarea，占位 `could be a fun fact, a vibe check…`，已回填。`Done` 在框内右下，可点。

### 08 雷区 redFlags

- 气泡：`dealbreakers: what should Ditto avoid?`，`dealbreakers` 加粗。
- 副文案：`include anything you care about: physical traits, behavior, personality, etc.`
- Private 提示：有。
- textarea，占位 `anything that's a hard no…`，已回填。`Done` 可点。【已证实】

### 09 是否学生 isStudent

- 气泡：`are you currently a student?`，`student` 加粗。
- Private 提示：有。
- 单选：`🎓 yes, i'm a student`、`💼 nope, not a student`。预选 `nope`。【已证实】
- 没有切换，所以不知道在编辑页改成 yes 会不会追加 schoolYear / 学校邮箱。

### 10 生日 birthday

- 气泡：`when's your birthday?`，`birthday` 加粗。
- 输入框上方居中小字：`Only your age is shown to others.`
- 输入框回填 `10 / 09 / 1996`，占位 `MM / DD / YYYY`。右侧圆形箭头 `Send`，可点。【已证实】
- 入驻时的确认层写着 `You can't change it later`，但编辑页仍然提供了入口。保存会不会被拒没有测。【已证实】入口存在；【推断】可能只在服务端拦。

### 11 性别 gender

- 气泡：`what's your gender?`，`gender` 加粗。无 Private 提示。
- 单选：`Man`、`Woman`、`Nonbinary`、`Other`。预选 `Man`。这里的 `Other` 是普通胶囊，不带铅笔。【已证实】

### 12 族裔 ethnicity

- 气泡：`What's your ethnicity?`。Private 提示：有。
- 多选，9 项，同 `09` §13。预选 `White`，带对勾。【已证实】

### 13 身高 height

- 气泡：`How tall are you?`，`tall` 加粗。
- 读数胶囊 `6'0"`，尺规刻度 `4'` 到 `8'`，中央粉色指针，下方 `6'`。单位切换 `ft / in`（选中，白底小胶囊）和 `cm`。【已证实】
- `Done` 打开即可点。入驻时要拖过尺规才放开 Next，编辑页不需要。【已证实】
- 没有点 `cm`，因为不确定单位偏好会不会被记住。

### 14 想约谁 expectedGender

- 气泡：`Who are you looking to date?`（无加粗）。Private 提示：有。
- 多选：`Men`、`Women`、`Nonbinary`、`Everyone`。预选 `Women`。【已证实】

### 15 年龄范围 ageRange

- 气泡：`What's your preferred age range?`，`age range` 加粗。Private 提示：有。
- 双滑杆，两个读数白胶囊 `26`、`34`。滑块是半透明圆角胶囊，选中区间的刻度变粉。【已证实】
- aria：`Minimum age` / `Maximum age`，`aria-valuemin=17`，`aria-valuemax=51`，当前 26 和 34。【已证实】
- **范围随用户年龄变化**：`09` 学生账号（22 岁）是 17 到 36；本账号（30 岁）是 17 到 51。【已证实】两组数；【推断】上限由本人年龄推出，公式没定（22→36、30→51 不是同一个加数）。

### 16 身高要求 expectedHeightRange

- 气泡：`how tall should your match be?`，`tall` 加粗。Private 提示：有。
- 因为当前值是 `I don't care about height`，尺规不显示，只剩左下一枚品红胶囊 `🤷 I don't care about height`（`aria-pressed=true`）和右下 `Done`。【已证实】
- 没有取消这枚胶囊，所以编辑态的双尺规没看到。

### 17 族裔偏好 expectedEthnicity

- 气泡：`Any ethnicity preferences?`，`ethnicity` 加粗。Private 提示：有。
- 多选，`No Preference` 在首位，后接 9 项。预选 `No Preference`。【已证实】

## 5. 深度信息 `/deep-info`

截图 `20-deep-info.png`。【已证实】

- 顶栏：`Back to profile`（id `deep-info-page-back`），居中 `Deep Info`（17px / 400）。
- **第一张卡**，四行，样式同资料页的行（id 前缀也是 `application-summary-`）：

| 行标签 | 字段 id | 当前值 |
|---|---|---|
| Religion | religious | Atheist |
| Religion importance | religionImportance | doesn't matter |
| Political beliefs | politicalBeliefs | Apolitical |
| Politics importance | politicsImportance | doesn't matter |

- **第二张卡**：内边距 `24px 16px`。标题 `The look you're into`（h2，16px / 400，ui-sans-serif）。说明 `Drop a celeb crush or someone whose vibe you love`（14px，透明度 0.9）。下面一个 98×98 的加号格：`<label class="_add_tu233_33">`，底 `rgba(255,255,255,0.7)`，圆角 16px，lucide 加号 40px。里面是 `input#photo[type=file]`，aria-label `Add photos`，`multiple`，accept 与入驻照片步完全一样（`image/jpeg,image/png,image/webp,image/bmp,image/heic,image/heif,.heic,.heif`）。
- CSS 模块名 `_tu233_` 和入驻照片步的 `_fileInputOverlay_tu233_51` 是同一个，说明理想型照片复用了照片格组件。【推断】
- 空态没有额外文案，就是一个加号格。没点，以免弹出文件选择器。存储字段是 `idealDateImages[]`。【已证实】字段为空。

### 21 宗教 religious

- 气泡：`what's your religion?`，`religion` 加粗。Private 提示：有。
- **多选**（选项带 `aria-disabled`、选中项带对勾）：`Atheist`、`Agnostic`、`Christian`、`Catholic`、`Jewish`、`Muslim`、`Hindu`、`Buddhist`、`Spiritual`，加带铅笔的 `Other`。预选 `Atheist`。【已证实】

### 22 宗教重要度 religionImportance

- 气泡整句：`does your match have to share your religion?`。【已证实】补齐了 `09` §28 只抄到半句的缺口。
- Private 提示：有。单选：`doesn't matter`、`kinda matters`、`super important`。预选 `doesn't matter`。

### 23 政治 politicalBeliefs

- 气泡：`what's your political vibe?`，`political vibe` 加粗。Private 提示：有。
- 单选：`Very left`、`Left-leaning`、`Center`、`Right-leaning`、`Very right`、`Apolitical`，加带铅笔的 `Other`。预选 `Apolitical`。【已证实】

### 24 政治重要度 politicsImportance

- 气泡：`does your match have to share your politics?`。Private 提示：有。
- 单选三档同上。预选 `doesn't matter`。【已证实】

## 6. 屏蔽名单 `/blocklist`

### 6.1 空态

截图 `30-blocklist-empty.png`。【已证实】

- 顶栏：`Back to profile`（id `blocklist-page-back`），居中 `Blocklist`（17px / 400）。
- 中间：lucide `shield-ban` 图标（`text-white/70`），下面 `No one on your blocklist yet`（16px / 500，白）。
- 按钮 `Add block`：358×48，白底 `rgba(255,255,255,0.95)`，字 `rgb(255,44,171)` 16px / 700，圆角 9999px，`backdrop-filter: blur(10px)`。
- 加载时请求 `GET https://api.ditto.ai/blocklist/entries`，200。

### 6.2 添加屏蔽抽屉

点 `Add block` 打开，看完点 `Cancel` 关闭。没有输入，没有点 `Block`，也没有点邮箱 / 手机行的 `Add`。之后网络里没有任何 blocklist 写请求。【已证实】截图 `31-blocklist-add-sheet.png`。

- Radix Dialog，底部抽屉：贴底，宽 390，最小高 50dvh，本次高 621。底 `rgba(255,255,255,0.9)`，`backdrop-filter: blur(4px) saturate(1.6)`，上圆角 28px，内边距 24px，字色 `#022b4e`。背景页被模糊。
- 左上 `×`，aria-label `Close`。
- 标题 `Add block`：24px / 700，居中，字距 -0.01em。
- 四个字段，每个上面一行「图标 + 标签」（14px，`rgba(2,43,78,0.7)`）：

| 图标 | 标签 | 控件 | 占位 | 右侧 |
|---|---|---|---|---|
| user | `Name` | `input#blocklist-entry-form-name`，text | `e.g. Alex from college` | — |
| mail | `Emails` | input，type email，inputmode email | `someone@example.com` | `Add`（aria-label `Add email`） |
| phone | `Phones` | input，type tel，inputmode tel | `(415) 555-1234` | `Add`（aria-label `Add phone`） |
| file | `Note` | `textarea#blocklist-entry-form-note` | `A quick reminder for future-you.` | — |

- 输入框：高 40，底 `rgba(2,43,78,0.06)`，圆角 6px，`autocomplete=off`。
- 主按钮 `Block`：`type=submit`，满宽 342×48，底 `rgb(2,43,78)`，白字 16px / 700，圆角 999px。表单全空时 `disabled=false`。空提交会怎样没有测。【已证实】
- 次按钮 `Cancel`：文字按钮，深蓝 16px / 700。
- 「Emails」「Phones」是复数加 `Add`：一个人可以登记多个邮箱和手机号。【推断】`Add` 会追加一行输入框。

中国版启发：屏蔽靠「姓名 + 邮箱 / 手机号 + 备注」，在服务端按联系方式排除。国内可改成手机号和微信号，并提示仅用于排除匹配。

## 7. 账号设置 `/account` 复查

截图 `40-account.png`。和 `08` 第 12 条一致，补几处原文和图标。【已证实】

- 顶栏：返回（id `account-settings-back`，aria-label `Back`），标题 `Account Settings`。
- 第一组：`Change Email`（lucide mail）、`Change Phone Number`（smartphone）、`Log Out`（log-out）。每行 56px 高，右侧 chevron-right，透明度 80%。
- 组标题 `Leaving Ditto`（h2，14px / 500）。
- 第二组：`Deactivate Account` / 副文 `Hide your profile. Nothing is deleted`（circle-pause），`Delete Account` / 副文 `Permanently delete your data after 30 days`（trash2）。每行 66px。
- `08` 写的是 Deactivate / Delete，实机按钮文字是 `Deactivate Account` / `Delete Account`。
- 四个入口都没点。

## 8. 与 questionnaire.json 的差异

| JSON id | 实机字段 id | 差异 |
|---|---|---|
| hobbies | hobbyRanking | 见 `09` §10：最多 5 枚，最多追问 3 个。 |
| gender | gender | 存储值 `Male`，选项文案 `Man`。JSON 记的是存储值。 |
| expectedAgeRange | ageRange | JSON 写 18 到 35。实机 aria 范围随年龄变化（17–36、17–51）。 |
| heightRange | expectedHeightRange | 存储为字符串；另有 `physicalAttraction` 三元组。 |
| relationshipLooking | intention | 文案一致。 |
| matchingStyle | matchAccuracy | 文案一致。 |
| hearAbout | referralSource | 职场版选项不同：多 Facebook、LinkedIn，少 Poster、Tabling。JSON 是学生版。 |
| dealbreakers | redFlags | 实机有 Private 提示，JSON 没标。 |
| firstMessage | firstImpression | 两条气泡都实证：`this helps your match get to know you better`。纸飞机 + `your match will see this`。 |
| politics | politicalBeliefs | 一致，带 Other。 |
| religion | religious | 一致，多选，带 Other。 |
| religionImportance | religionImportance | 整句已证实：`does your match have to share your religion?` |
| occupation | occupation | 问句、占位一致。JSON 的 `maxLength: 120` 前端没设。无 Private 提示。 |
| firstDateBudget | firstDateBudget | 两条气泡和五个选项一致。实机有 Private 提示，JSON 没标。 |
| educationLevel | educationLevel | 问句、四个选项一致。实机有 Private 提示。 |
| educationImportance | educationImportance | 问句、三档一致。实机有 Private 提示。存储值 `Doesn't matter`，编辑页回填失败，显示为未选。 |
| datingHelp | datingHelp | 资料页没有入口。存储值（`Starting conversations`、`Confidence`）和 `09` 看到的选项文案不同。 |
| faceVerify | （无编辑入口） | `face-verification/status` 是 `mode: shadow`、`required: false`、`retry_capture`。说明视频自拍目前是影子模式，不拦人。 |

## 9. 与 08 / 09 的差异和补充

- `08` 写「单题编辑页右下角 Done 按钮」：实测全部选择题、文本题都是 `Done`，**只有生日是圆形 Send**。文本题的 Done 在输入框里面。
- `08` 写「06 单题编辑（文本）是深色胶囊输入框 + 白色圆形箭头」：这只对 `name` 和 `birthday` 成立。工作、第一眼、雷区是多行 textarea + 框内 Done。
- `08` 的「选中后显示白色对勾」只适用于多选。单选没有对勾。
- `08` 第 4 条资料页字段清单与实机一致，补了字段 id 和行标签原文（第 2.2 节）。
- `08` 第 8 条 Deep Info 的四项标签实为 `Religion`、`Religion importance`、`Political beliefs`、`Politics importance`。
- `09` §28 宗教重要度后半句、§29 firstMessage 的第二条气泡，本次补齐。
- `09` §23 hearAbout 的选项只适用于学生版。
- `09` §15 年龄滑杆的 17–36 只适用于那个 22 岁账号。
- 编辑模式没有进度条、Ditto 头像和锁；入驻模式有。

## 10. 刻意没做的

- 没点任何 `Done`、`Send`、`Block`、邮箱 / 手机的 `Add`、`Other` 胶囊、身高的 `cm`、`I don't care about height` 的取消。原因：可能直接写入资料，或改变单位偏好。
- 没点 Deep Info 的加号格（会弹文件选择器）。
- 没点 Account 的四个入口（改邮箱、改手机、登出、停用 / 删除）。
- 入驻照片页被动打开了三次（重定向），没有做任何操作。照片格里是真人照片，那张截图已删除，只保留 toast 文字截图。

## 11. 仍未覆盖

- 首页、`/where`、`/you`、☰ 菜单：本次被重定向，只能沿用 `08`。
- 编辑页保存后的行为：toast、回跳、资料行回显，以及学历要求「空选点 Done」存什么。
- 编辑页把 isStudent 改成 yes 之后会不会追加 schoolYear / 学校邮箱。
- 身高要求的编辑态双尺规（要先取消 `I don't care about height`）。
- `Other` 胶囊在 referralSource / politics / religion 的编辑态展开。
- 屏蔽名单有条目时的列表样式、`Add` 追加行、空表单提交的校验文案。
- Deep Info 理想型照片上传后的格子和删除。
- 改邮箱 / 改手机 / 停用 / 删除的二级页。
- 路由守卫在 `onboardingStatus` 恢复正常后是否立即放行首页。
