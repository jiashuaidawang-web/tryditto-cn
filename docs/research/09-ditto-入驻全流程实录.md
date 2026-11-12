# 09 Ditto 入驻全流程实录（学生路径）

采集日期：2026-11-12。视口 390×844。会话只有一个已登录的 Playwright 页，路由在交卷前一直是 `https://app.tryditto.com/onboarding`，没有按题目分 URL。【已证实】

对照底稿是 `assets/ditto-app/questionnaire.json`（从 JS 包抽出，采集前未在真 UI 走过）和 `07-ditto-登录后全流程.md`。本文只记这次真机走过的学生路径。截图在 `assets/ditto-onboarding/shots/`，文件名与采集目录一致。

证据标记：

- **【已证实】**：当次 DOM 文本、计算样式、控制台行，或对应截图里能直接读到的事实。
- **【推断】**：用多条已证实事实拼出的组件或分界判断。
- **【猜测】**：没走到或没量到，不能写进实现。

测试账号上填过的姓名、生日、族裔选择只作为控件状态，不当作人物资料复述。

## 1. 进度条

顶栏下有 3 段，每段约 117px 宽、高 4px（`h-1`），轨道 `bg-white/20`，圆角 9999px。填充子元素 `bg-white/66`，阴影 `0 0 3px white`，宽度过渡 `width 0.3s cubic-bezier(0.4, 0, 0.2, 1)`。【已证实】

界面不印刷 `yourType` / `aboutYou` / `continue` 这几个阶段名。下面的阶段名是按填充落在第几段做的对应，不是界面文案。【推断】

| 落点 | 三段填充宽度（px，约） |
|---|---|
| isStudent | 23, 0, 0 |
| name | 47, 0, 0 |
| hobbies | 117, 8, 0 |
| cookingStyle | 117, 17, 0 |
| cookingSignatureDish | 117, 25, 0 |
| musicSound | 117, 33, 0 |
| musicNowPlaying | 117, 42, 0 |
| travelStyle | 117, 50, 0 |
| travelNext | 117, 58, 0 |
| ethnicity | 117, 67, 0 |
| expectedEthnicity | 117, 75, 0 |
| expectedAgeRange | 117, 83, 0 |
| height | 117, 92, 0 |
| heightRange | 117, 100, 0 |
| photos | 117, 108, 0 |
| location | 117, 117, 0 |
| matchingStyle | 117, 117, 29 |
| schoolYear | 117, 117, 39 |
| hearAbout | 117, 117, 49 |
| dealbreakers | 117, 117, 58 |
| politics | 117, 117, 68 |
| politicsImportance | 117, 117, 78 |
| religion | 117, 117, 87 |
| religionImportance | 117, 117, 97 |
| firstMessage | 117, 117, 107 |
| datingHelp | 117, 117, 117 |

gender、expectedGender、birthday、relationshipLooking 的像素没有逐题复测。第一段在 hobbies 之前已经走满。第三段从 location 起算，relationshipLooking 夹在 location（第三段为 0）和 matchingStyle（29）之间。【已证实】有数的行；【推断】没记像素的两题仍在相邻两行之间。

JSON 把 location 放在 aboutYou，把 preferencesIntro 放在 continue 的开头。实机第二段在照片页仍是 108，地点页起第二段已满、第三段从 0 开始，而且没有出现 preferencesIntro 那一页过场。【已证实】

## 2. 页序索引

| 序 | id | 题型 | 截图 |
|---|---|---|---|
| 1 | isStudent | 单选 | `assets/ditto-onboarding/shots/01-isStudent-yes.png`（`01-isStudent-empty.png` 实际已预选 “nope”） |
| 2 | name | 文本，Send | `02-name-after-next.png`、`02-name-focus-empty.png`、`02-name-filled.png` |
| 3 | gender | 单选 | `03-gender-empty.png`、`03-gender-man.png` |
| 4 | expectedGender | 多选，Everyone 独占 | `04-expectedGender-empty.png`、`04-expectedGender-multi.png`、`04-expectedGender-everyone.png`、`04-expectedGender-women.png` |
| 5 | birthday | 日期，Send | `05-birthday.png`、`05-birthday-error.png`、`05-birthday-valid.png`、`05-birthday-revisit.png` |
| 6 | hobbies | 多选加自定义 | `06-hobbies-empty.png`、`06-hobbies-three.png`、`06-hobbies-fourth.png`、`06-hobbies-custom-open.png`、`06-hobbies-custom-filled.png`、`06-hobbies-custom-added.png` |
| 7 | cookingStyle | 多选 | `07-branch-1.png` |
| 8 | cookingSignatureDish | 文本 | `07-branch-2.png` |
| 9 | musicSound | 多选 | `07-branch-3.png` |
| 10 | musicNowPlaying | 文本 | `07-branch-4.png` |
| 11 | travelStyle | 多选 | `07-branch-5.png` |
| 12 | travelNext | 文本 | `07-branch-6.png`、`07-branch-7.png` |
| 13 | ethnicity | 多选 | `back-1.png`、`fwd-stop-8.png` |
| 14 | expectedEthnicity | 多选，No Preference 独占 | `10-expectedEthnicity-empty.png`、`11-expectedEth-one.png`、`11-expectedEth-multi.png`、`11-expectedEth-nopref.png`、`11-expectedEth-after-exclusive.png` |
| 15 | expectedAgeRange | 双滑杆 | `12-after-ethnicity.png`、`12-age-nudged.png`、`12-age-dragged.png` |
| 16 | height | 尺规 | `13-height.png`、`13-height-cm.png`、`13-height-dragged.png` |
| 17 | heightRange | 双尺规加一枚跳过胶囊 | `14-step-1.png`、`14-height-range-any.png`、`14-height-range-next-enabled.png` |
| 18 | photos | 照片格 | `16-after-choosers.png`、`16-photos-two.png`、`16-photos-ready.png`、`16-photos-two-distinct.png`、`17-flow-1.png` |
| 19 | location | 地图 | `18-after-photos.png`、`18-location-why.png`、`18-location-search-open.png`、`18-location-search-now.png`、`18-location-ready.png`、`19-flow-0.png` |
| 20 | relationshipLooking | 多选 | `19-flow-3.png`、`19-flow-4.png` |
| 21 | matchingStyle | 单选 | `19-flow-5.png`、`20-flow-0.png` |
| 22 | schoolYear | 单选 | `20-flow-3.png` |
| 23 | hearAbout | 单选 | `20-flow-5.png` |
| 24 | dealbreakers | 长文本，可空着前进 | `20-flow-7.png` |
| 25 | politics | 单选 | `20-flow-9.png` |
| 26 | politicsImportance | 单选 | `20-flow-10.png` |
| 27 | religion | 多选 | `21-flow-0.png` |
| 28 | religionImportance | 单选 | `21-flow-1.png` |
| 29 | firstMessage | 长文本 | `21-flow-2.png` |
| 30 | datingHelp | 多选，点第一项即交卷 | `21-flow-3.png`、`21-flow-4.png` |

分支页的 `07-branch-*` 有几张是气泡还在打字、列表还在滚动时截的，完整文案以下面 DOM 摘录为准。

## 3. 逐页原文

顶栏每页都有：返回（aria-label `Back`）、Ditto 头像加字标、锁（aria-label `What the lock means`）。锁的说明层这次没打开。【已证实】

关键词在 DOM 里是逐字母 span。身高页量过：关键词字母 `font-weight: 700`，周围词 500。其他页只证实了同一套拆字，加粗按同一组件推断。【已证实】身高页；【推断】其余拆字词。

### 1 isStudent

- 气泡：`are you currently a student?`，`student` 拆字。
- 选项：`yes, i'm a student`（学士帽）、`nope, not a student`（公文包）。
- 提示：`Private. Only used to match you`。
- 按钮：可见文案 Next。单选。选中为品红胶囊。
- 从 name 返回时，`nope, not a student` 已经处于选中。再点 `yes, i'm a student` 后，nope 取消。【已证实】
- 启用：未选时不能当作已完成；选中后 Next 可点。这次是在已有预选的状态下点的 Next。

### 2 name

- 先打出 `Hi, I'm Ditto`，再换成 `what's your name?`，`name` 拆字。打字日志是按词块替换，间隔大约 80 到 100ms，不是逐字母的 Web Animations。【已证实】
- 占位：`your name`。
- 按钮 aria-label `Send`。空值时 `aria-disabled=true`（DOM `disabled` 仍是 false），底 `rgba(255,255,255,0.3)`，字色 `rgba(14,28,38,0.45)`。有字后 `aria-disabled=false`，白底，字色 `rgb(26,26,26)`。【已证实】

### 3 gender

- 气泡：`what's your gender?`，`gender` 拆字。
- 选项：`Man`、`Woman`、`Nonbinary`、`Other`。单选。
- JSON 写的是 Male / Female。实机是 Man / Woman。【已证实】

### 4 expectedGender

- 气泡：`Who are you looking to date?`
- 提示：`Private. Only used to match you`。
- 选项：`Men`、`Women`、`Nonbinary`、`Everyone`。多选。
- Everyone 独占：先选 Men 和 Women，再点 Everyone，只剩 Everyone。再点掉 Everyone、只留 Women，供后面的体位页判断。【已证实】
- 右下前进是圆形箭头，aria-label `Next`。

### 5 birthday

- 气泡：`when's your birthday?`
- 副文案：`Only your age is shown to others.`
- 占位：`MM / DD / YYYY`。按钮 `Send`。
- 填 `01/01/2015` 后不是字段红错，而是确认层：`You're 11 years old. Your birthday is January 1, 2015. You can't change it later, so make sure it's right.` 按钮 `Yes, that's right` 和 `Edit birthday`。该确认没有点。【已证实】截图 `05-birthday-error.png`。
- 改成成年日期后 Send 成功。返回时输入框显示 `01 / 15 / 2004`。【已证实】

### 6 hobbies

- 气泡：`what are your hobbies & interests?`，`hobbies` 和 `interests` 都拆字。
- 选项：`👨‍🍳 Cooking`、`🎵 Music`、`✈️ Travel`、`📚 Reading`、`🏋️ Gym`、`🎮 Gaming`、`🎬 Movies/TV`、`🎨 Art`，外加 `Type your hobby`。
- 自定义：点 `Type your hobby` 后出现同文占位的输入。填入后变成一枚额外胶囊。这枚自定义胶囊在之后的 DOM 里没有 `ob-selected` 类。【已证实】
- 第四枚预设（Reading）可以同时选中，文案里没有 “max” 提示。【已证实】提交前取消了 Reading，所以没有测过「四枚预设一起 Next」会展开几条。
- 实际提交的是 Cooking、Music、Travel，加自定义 Climbing。后面只按这个顺序展开三条，自定义没有自己的追问页。【已证实】

### 7 cookingStyle

- 气泡：`what's your cooking style?`，`cooking style` 拆字。
- 选项：`🍝 Italian`、`🍜 Asian`、`🌮 Mexican`、`🍲 Comfort food`、`🧁 Baking`、`🔥 Grilling`、`🥗 Healthy bowls`、`🥐 Brunch`，以及带铅笔的 `Other`。
- 多选。前进前 Italian 为选中。

### 8 cookingSignatureDish

- 气泡：`what's your signature dish?`，`signature dish` 拆字。
- 占位：`the one u brag about`。文本，Send / 圆形箭头。

### 9 musicSound

- 气泡：`what's your music sound?`，`music sound` 拆字。
- 选项：`🎤 Pop`、`🎧 Hip-hop`、`🎸 Indie`、`🎹 Electronic`、`💞 R&B`、`🤘 Rock`、`🤠 Country`、`🎷 Jazz`、`🎻 Classical`、`🩷 K-pop`、`🪇 Latin`，以及 `Other`。
- 多选。Pop 被选中后前进。

### 10 musicNowPlaying

- 气泡：`who u streaming rn?`，`streaming` 拆字。
- 占位：`artist on repeat rn`。

### 11 travelStyle

- 气泡：`what's your travel style?`，`travel style` 拆字。
- 选项：`🎒 Backpacking`、`🍾 Luxury`、`🥘 Foodie tours`、`⛰️ Adventure`、`🏖️ Beach + sun`、`🏙️ City-hopping`、`✈️ Solo travel`、`🚗 Road trips`，以及 `Other`。
- Backpacking 选中后前进。

### 12 travelNext

- 气泡：`what's your next travel destination?`，`travel destination` 拆字。
- 占位：`where u manifesting`。

### 13 ethnicity

- 气泡：`What's your ethnicity?`，`ethnicity` 拆字。
- 提示：`Private. Only used to match you`。
- 选项，无 No Preference：`American Indian`、`Black/African Descent`、`East Asian`、`Hispanic/Latino`、`Middle Eastern`、`Pacific Islander`、`South Asian`、`South East Asian`、`White`。
- 多选。为了离开这一页，第一项曾处于选中。这是控件操作，不是人物属性。

### 14 expectedEthnicity

- 气泡：`Any ethnicity preferences?`，`ethnicity` 拆字。
- 同一句 Private 提示。
- 选项在上一题的列表前多 `No Preference`。
- 独占已测：East Asian 单选；再加 White 成为双选；点 No Preference 后只剩 No Preference；再点 East Asian，No Preference 被清掉，只剩 East Asian。最后以 No Preference 前进。【已证实】

### 15 expectedAgeRange

- 气泡：`What's your preferred age range?`，`age range` 拆字。
- Private 提示。
- 两个滑杆，aria-label `Minimum age`、`Maximum age`。aria 最小值 17、最大值 36。默认显示 18 和 24。把最大端拖到 30 后界面变成 18 和 30。【已证实】
- 对最小端按一次方向键左，显示数字没有变。【已证实】这次按键没推动滑杆，不能据此说键盘永远无效。
- 这一页的前进按钮就是圆形 Next。

### 16 height

- 气泡：`How tall are you?`，只有 `tall` 拆字且为 700 字重。
- 打开时中央读数 `5'8"`，尺规刻度 `4'` 到 `8'`。此时 Next 为 `aria-disabled=true`。拖动尺规后读数变为 `6'0"`，Next 变为可点，底色 `rgba(255,255,255,0.95)`，图标色 `rgb(2,43,78)`。【已证实】
- 单位按钮 `ft / in` 与 `cm`。选中的 `ft / in` 是 11px、字重 700、色 `rgb(2,43,78)`。未选的 `cm` 是 11px、字重 400、色 `rgba(255,255,255,0.7)`。
- 切到 cm：读数 `173 cm`，刻度从 130 到 240。【已证实】

### 17 heightRange

- 气泡：`how tall should your match be?`，`tall` 拆字。
- Private 提示。
- 打开时显示 `5'6"` 到 `6'2"`，刻度 `5'` `6'` `7'`，Next 禁用。
- 另有一枚 JSON 里没有的胶囊：`🤷 I don't care about height`。点中后变品红，尺规从正文消失。刚点完时 Next 仍是 `aria-disabled=true`。从照片页返回后，这枚胶囊仍选中，Next 已是可点。【已证实】两种状态都出现过。启用时机没有拆开复测，不能写成「这枚胶囊永远不能单独放行」。

### 18 photos

- 两条气泡：`Let's add at least 2 photos 📷`，以及 `Choose photos that show your vibe`。
- 说明：`Photos from different moments help us find better matches. You can swap them anytime.`
- 6 格。已填格有 aria-label `Remove photo`。空位是加号和虚线框。
- 文件输入 `accept` 为 `image/jpeg,image/png,image/webp,image/bmp,image/heic,image/heif,.heic,.heif`，`multiple=true`，class 含 `_fileInputOverlay_tu233_51`。【已证实】
- 上传中出现百分比和 `Hold and drag a photo to reorder.`
- 只有 1 张就点 Next：顶上错误条 `Some photos didn't upload` / `Please try again.`，控制台 `submit_start photos=1`。Next 重新禁用。【已证实】`17-flow-1.png`。
- 两张不同的纯色占位图上传完成后 Next 可点。控制台：`submit_start photos=2`，随后 `required=2 required_faces=2 with_face=0 passed=false`。界面仍然进入地点页。【已证实】人脸校验失败没有挡住这一次前进。

### 19 location

- 气泡在截图里是 `Where do you want to date?` 加定位针。DOM 里整句每个字母都是独立 span，和只拆关键词的题不同。【已证实】
- 问号按钮 aria-label `Why we ask where you'd date`。打开后的文案：`Your dating area` / `Set your area so we match you with people and spots near you.` / 按钮 `Got it`。【已证实】
- 地图上先是 `Updating area…`。搜索占位 `Search a location`。旁边是 aria-label `Use my current location`。这次没有点定位。
- 出行：`Walk`、`Transit`、`Drive`，文案曾出现 `1h max transit`，切到驾车后是 `1h max driving`。粉色刻度滑杆。【已证实】
- 搜索 `Boston` 的建议：`Boston MA`、`Boston UK`、`Boston Bar BC, Canada`。选中后标签变成 `Boston, MA`，等时圈罩住波士顿一带，Next 在圈算完后可点。【已证实】`19-flow-0.png`。
- 控制台有一条与默认定位相关的失败：`POST https://api.ditto.ai/location/isochrone` 返回 400，正文 `Ditto is only in the US and Canada for now.`【已证实】发生在选中 Boston 之前的那次预览。

### 20 relationshipLooking

- 气泡：`What are you looking for?`
- Private 提示。
- 选项：`Life partner`、`Serious relationship`、`Casual dates`、`New friends`、`Not sure yet`。
- 选中态除了品红底和加粗，还有白色对勾。【已证实】`19-flow-4.png`。
- 这一页和上一页之间没有 `now let's talk about your preferences` 过场。【已证实】采集到的两帧里没有它。它是否曾在转场的几百毫秒里闪过，没有单独截到，不能断言永不存在。【猜测】

### 21 matchingStyle

- 气泡：`how do u want ditto to match u rn?`，`match u` 拆字。
- Private 提示。
- 选项：`⚡️ More dates, faster`、`⚖️ A steady mix`、`🎯 Fewer, better matches`、`💎 Wait for the one`。
- 单选。未选时 Next 禁用。

### 22 schoolYear

- 气泡：`what year are u going into this fall?`，`year` 拆字。
- 选项：`Freshman`、`Sophomore`、`Junior`、`Senior`、`Master`、`PhD`、`Other`。

### 23 hearAbout

- 气泡：`how did u hear about us?`，`hear about us` 拆字。
- 选项：`Friend`、`Poster`、`Tabling`、`Instagram`、`TikTok`、`X (Twitter)`、`RedNote (Xiaohongshu)`、`Google`、`Other`。

### 24 dealbreakers

- 气泡：`dealbreakers: what should Ditto avoid?`，`dealbreakers` 拆字。
- 副文案：`include anything you care about: physical traits, behavior, personality, etc.`
- 同样有 Private 提示。JSON 这一题没有标 Private。【已证实】实机有这句。
- 占位：`anything that's a hard no…`。textarea。
- 空着的时候 Next 已经是 `aria-disabled=false`。按钮列表里没有 Skip。空着点 Next 就进了下一题。【已证实】

### 25 politics

- 气泡：`what's your political vibe?`，`political vibe` 拆字。
- Private 提示。
- 选项：`Very left`、`Left-leaning`、`Center`、`Right-leaning`、`Very right`、`Apolitical`、`Other`。

### 26 politicsImportance

- 气泡：`does your match have to share your politics?`，`politics` 拆字。
- Private 提示。
- 选项：`doesn't matter`、`kinda matters`、`super important`。

### 27 religion

- 气泡：`what's your religion?`，`religion` 拆字。
- Private 提示。
- 选项：`Atheist`、`Agnostic`、`Christian`、`Catholic`、`Jewish`、`Muslim`、`Hindu`、`Buddhist`、`Spiritual`、`Other`。

### 28 religionImportance

- 到达当帧时，气泡已打出的是 `does your match have to`，后面的关键词还没进入摘录。选项已经是 `doesn't matter`、`kinda matters`、`super important`，并带 Private 提示。【已证实】截到的半句；【推断】后半与政治重要度同一句式，关键词是 religion。整句没有在稳定帧里抄全。

### 29 firstMessage

- 气泡拆字词是 `first thing`，已打出 `first thing your match should know?`
- 副文案只抄到 `your match will see this`。JSON 的另一句 `this helps your match get to know you better` 不在这次摘录里。
- 占位：`could be a fun fact, a vibe check…`。空着时 Next 禁用。填入短句后 Next 可点。【已证实】

### 30 datingHelp

- 截图停在打字中途：气泡已出现 `if you had a dating coach`，`dating coach` 还在进入气泡。选项已经铺开。彩纸方块同时在落。【已证实】`21-flow-3.png`。JSON 的后半句 `what would you want help with?` 这一帧没打完，不能把整句标成已抄全。
- 选项原文（emoji 在左，句子小写）：
  - `👋 approaching girls`
  - `💬 keeping a convo going`
  - `👀 telling if they’re into me`（弯引号）
  - `💪 being more confident`
  - `🧭 figuring out what i want`
  - `☕ the date itself`
  - `💘 just get me a date`
- `approaching girls` 与当时「只选了 Women」一致。没有改过预期性别，所以 guys / people 两种措辞没看到。【已证实】
- 三段进度都已是 117。圆形 Next 仍是禁用的灰色，截图里还在右下角。
- 点下 `👋 approaching girls` 之后，脚本只在等 Next 变成可点，没有再点 Next、Back 或任何完成按钮。DOM 随即变空，地址变成 `https://app.tryditto.com/`。【已证实】
- 空档上的一帧是蓝色颗粒底上的心形点阵，中心几点是粉色，外圈是浅色点，没有顶栏。【已证实】`21-flow-4.png`。之后操作者在同一地址看到首页文案 “Ditto is finding you a date!”。那句首页标题不在这次入驻截图里，登录后首页见 `08`。【推断】点阵是进入该首页前的加载帧。

## 4. 和 questionnaire.json 的逐项差异

只列实机对得上、或实机明确没出现的项。选项文案与 JSON 一致、且上面已经原文照录的，不再重复。

| id | 实机 |
|---|---|
| isStudent | 选项、提示、问句与 JSON 一致。从下一题返回时，否学生可以是预选状态。 |
| name | 开场 `Hi, I'm Ditto` 与占位 `your name` 一致。发送钮看 `aria-disabled`，不看 `disabled`。 |
| gender | 实机 `Man` / `Woman`。JSON 写 Male / Female。 |
| expectedGender | 问句和四项一致。Everyone 独占已在实机复现。 |
| birthday | 问句、副文案、占位一致。JSON 没有「年龄确认层」。未成年日期走出的是确认层，不是字段错误。 |
| hobbies | 八项和自定义占位一致。实机可同时选中 4 枚预设，画面没有上限文案。`branchLimit: 3` 对得上的是「提交后只展开 3 条追问」，不是「第 4 枚胶囊选不中」。自定义芯片不另开追问。 |
| cooking / music / travel 的多选 | JSON 的 `allowCustom` 在实机是一枚 `Other` 按钮（烹饪那题带铅笔）。选项数组里没有 “Other” 这个词。追问文案和占位与 JSON 一致。 |
| Reading、Gym、Gaming、Movies/TV、Art 的 10 个追问 | 这次没展开，不能用实机证实 JSON 里的文案。 |
| ethnicity / expectedEthnicity | 问句和选项一致。No Preference 独占已复现。两题都有 Private 提示。 |
| expectedAgeRange | 默认 18 到 24 与 JSON 一致。JSON 写范围 18 到 35。实机滑杆 aria 是 17 到 36，拖动后最大端可以到 30。 |
| expectedPosition / position | 本次性别 Man、期望只有 Women。年龄题的下一题直接是身高，两道体位题都没出现。与 JSON 的隐藏条件方向一致。其他性别组合没走。 |
| height | JSON 只写了类型 height。实机是尺规，默认显示 `5'8"` 但 Next 要拖过才启用，并有 `ft / in` 与 `cm`。 |
| heightRange | JSON 没有 `🤷 I don't care about height`。实机有，选中后尺规收起。 |
| email / emailVerify | JSON 放在身高范围和照片之间。实机从身高范围 Next 直接进入照片，返回也回到身高范围。学生路径这一段没有学校邮箱页，也没有验证码页。 |
| photos | 两条气泡、说明、最少 2 张、最多 6 格与 JSON 一致。另有拖拽排序文案、上传失败条，以及控制台人脸门槛。 |
| location | JSON 只写 `where(map area)`。实机是 Google 地图、等时圈、搜索、当前位置、步行/公交/驾车、1 小时滑杆，以及 “为什么要问” 的说明层。 |
| preferencesIntro | 实机在地点和 “What are you looking for?” 之间没有这句过场。 |
| relationshipLooking / matchingStyle / schoolYear | 问句和选项与 JSON 一致。选中胶囊带对勾。 |
| hearAbout / politics / religion | 与 JSON 一致，并且都有一枚 `Other`。JSON 用 `allowCustom` 表示，没有把 Other 写进选项数组。 |
| dealbreakers | 问句、副文案、占位一致。实机多了 Private 提示。没有 Skip 按钮。空 textarea 时 Next 可点，空提交等于跳过。 |
| politicsImportance / religionImportance | 三档文案一致。宗教这题的后半句没有在稳定帧抄全。 |
| firstMessage | 占位和 `your match will see this` 一致。JSON 的另一句帮助说明这次没抄到。 |
| datingHelp | 实机 emoji 在左，句子全小写，`Confidence` 实机是 `being more confident`。期望为 Women 时第一项是 `approaching girls`。点第一项即离开问卷，不经过 Next。 |
| friendInvites / imessageUpsell / phone / phoneVerification | JSON 标在 join 流。这次学生路径交卷前都没出现。 |
| occupation / firstDateBudget / educationLevel / educationImportance / faceVerify | JSON 标在职场流。这次没有把 isStudent 改回否，这些页没出现。控制台在照片步加载了人脸模型，那是照片校验，不是 faceVerify 那一页。 |

## 5. 实机分支

- 爱好追问顺序等于预设的勾选顺序。本次是烹饪，然后音乐，然后旅行。每条都是「多选风格，再一题短文本」。【已证实】
- 自定义爱好变成胶囊，不增加追问。【已证实】
- 第 4 枚预设可以选中。没测「带着 4 枚预设按 Next」。【已证实】能选中；【猜测】第 4 条会不会被静默丢掉，没有证据。
- Man 且期望只有 Women：体位两题不出现，年龄之后是身高。【已证实】
- Everyone 清掉其他性别。No Preference 清掉其他族裔。反过来再点具体项，独占项也会被清掉。【已证实】
- 返回保持答案：name 回到 isStudent 时否学生仍选中；照片回到身高范围时 “I don't care about height” 仍选中，并且 Next 可点。【已证实】
- 身高单位 cm 把 `5'8"` 显示成 `173 cm`，刻度 130 到 240。【已证实】
- 年龄滑杆默认 18 到 24，aria 边界 17 和 36，拖动能改最大端。【已证实】

## 6. 交卷（关键）

datingHelp 是进度条三格都满的那一页，也是这次学生路径的最后一题。彩纸在选项被点中之前就已经出现，圆形 Next 仍禁用。第一次点选项（`👋 approaching girls`）之后没有 Next、没有完成按钮、没有返回。地址从 `/onboarding` 变成 `https://app.tryditto.com/`，中间一帧是心形点阵。【已证实】

这和「末页必须人按 Next 才结束」不一致。末页的第一下选项就是提交。

同时记下两条控制台事实，它们都没有把流程停住：

- 照片提交：`required_faces=2`，两张纯色图 `with_face=0`，`passed=false`，下一屏仍是地点页。【已证实】
- 等时圈：`POST https://api.ditto.ai/location/isochrone` 400，`Ditto is only in the US and Canada for now.` 随后用搜索选了 Boston, MA，圈可以画出来，Next 可以点。【已证实】

没有录到 `/user/onboarding/enroll` 或任何 enroll 请求。这一段没有开网络监听，不能据此说接口不存在。【已证实】只限于「这次没看到」。

## 7. 组件规格

数值来自 `getComputedStyle`，视口 390×844。

**页面底。** `rgb(35, 48, 57)`，也就是 `#233039`。字色白，字体 Arial / Helvetica Neue / Helvetica。颗粒和海平线光带是纹理，不是 body 的 background-image。【已证实】底色；颗粒的图层选择器这次没单独拆。

**气泡。** 白底 `rgba(255,255,255,0.9)`，字色 `rgb(2, 43, 78)`，圆角 28px，内边距 12px 16px，16px、字重 500。关键词字母 700。没有 backdrop-filter。【已证实】年龄页那只气泡。

**未选胶囊。** class `ob-glass`。底 `rgba(8, 23, 33, 0.25)`，白字，14px / 400，行高 20px，最小高度 48px，圆角 24px，内边距 8px 16px。`backdrop-filter: blur(20px) brightness(0.75) saturate(1.4)`。内阴影 `inset 0 1px 3px rgba(255,255,255,0.12)`。颜色过渡 0.15s，曲线 `cubic-bezier(0.4, 0, 0.2, 1)`。【已证实】

**选中胶囊。** class `ob-selected`。底 `rgb(228, 0, 137)`，也就是 `#E40089`，白字，14px / 700。`backdrop-filter: blur(10px)`。内阴影 `inset 0 0 8px rgba(255,255,255,0.15)`。关系题的选中项右侧有白色对勾。【已证实】

**圆形 Next。** 约 72×48，贴右下。禁用时 `aria-disabled=true`。启用后底 `rgba(255,255,255,0.95)`，图标 `rgb(2, 43, 78)`。姓名页的 Send 是同一位置的圆形按钮，空态更透：底 `rgba(255,255,255,0.3)`，图标 `rgba(14,28,38,0.45)`，模糊 10px，圆角 9999px。【已证实】

**文本条。** 深色圆角条，占位浅色。姓名、生日、爱好自定义、追问短句、雷区、第一句话都用它。雷区是 textarea，占位 `anything that's a hard no…`。

**进度条。** 见第 1 节。三段，不标文字。

**顶栏。** 返回、中间 Ditto 头像 webp 加字标、右侧锁。锁的 aria-label 是 `What the lock means`。说明层没打开，锁里面的句子没有抄到。

**末页彩纸。** `21-flow-3.png` 里，气泡还在打 “dating coach” 时，品红、绿、黄、白的小方块已经散布在选项周围。没有对这层跑 `getAnimations()`，持续时间未知。【已证实】画面；【猜测】实现是 canvas 或绝对定位粒子。

**交卷后的心形点阵。** `21-flow-4.png`。同一张蓝颗粒底，中央一颗由圆点排成的心，心中几点发粉光。没有顶栏和按钮。这一帧的 DOM 文本是空的，地址已经是 `/`。【已证实】

**打字。** 姓名页从 isStudent 进入时，观察者记下的块是：`Hi,` 1273ms，`Hi, I'm` 1357ms，`Hi, I'm Ditto` 1457ms，旧气泡消失 1492ms，然后 `what's` 1673ms，`what's your` 1756ms，`what's your name` 1858ms，问号 1939ms。间隔大约 80 到 100ms 一块。打完之后 `document.getAnimations()` 为空。【已证实】这一次过渡。其他题没有逐题存 JSON。

Playwright 的视频没有录成。启动录像时报本机没有 Playwright 自带的 ffmpeg，按当时的限制没有改权限去装。所以没有 `pass1` 视频，也没有按页写下的 motion JSON。【已证实】缺口。

## 8. 实机学生路径

```mermaid
flowchart TD
  isStudent[isStudent 单选] --> name[name 文本]
  name --> gender[gender 单选 Man Woman]
  gender --> expected[expectedGender 多选]
  expected --> birthday[birthday 日期]
  birthday --> hobbies[hobbies 最多展开 3 条]
  hobbies --> cook[cookingStyle]
  cook --> dish[cookingSignatureDish]
  dish --> music[musicSound]
  music --> now[musicNowPlaying]
  now --> travel[travelStyle]
  travel --> dest[travelNext]
  dest --> eth[ethnicity]
  eth --> expEth[expectedEthnicity]
  expEth --> age[expectedAgeRange]
  age --> height[height 尺规]
  height --> hRange[heightRange]
  hRange --> photos[photos 至少 2 张]
  photos --> loc[location 地图]
  loc --> look[relationshipLooking]
  look --> style[matchingStyle]
  style --> year[schoolYear]
  year --> hear[hearAbout]
  hear --> deal[dealbreakers 可空]
  deal --> pol[politics]
  pol --> polImp[politicsImportance]
  polImp --> rel[religion]
  rel --> relImp[religionImportance]
  relImp --> first[firstMessage]
  first --> help[datingHelp 进度条已满]
  help -->|点第一项 不点 Next| home["/ 心形点阵之后的首页"]
```

这条链是本次勾选 Cooking、Music、Travel，且性别为 Man、期望只有 Women 时的顺序。年龄和身高之间没有体位题。身高范围和照片之间没有邮箱题。地点和关系题之间没有过场页。

## 9. 没覆盖的部分

- ~~爱好追问没走：Reading、Gym、Gaming、Movies/TV、Art~~ 已在第 10 节补齐（资料编辑页）。
- isStudent 选否之后的职场题：occupation、firstDateBudget、educationLevel、educationImportance、faceVerify 那一页。照片步的人脸模型不等于这页视频自拍。
- 会把体位题放出来的性别组合。只证实了 Man 加仅 Women 时两题隐藏。
- 学校邮箱和 6 位验证码。这条学生路径里该页没有出现，所以也没有走到「不是学生？不用学校邮箱继续」。
- friendInvites、imessage 末页、手机号和手机验证码。JSON 把它们放在 join 流。这次交卷没有经过它们。
- 锁按钮里的说明。
- 末页除 `approaching girls` 以外的选项会不会同样一触即交卷。只点了第一项。
- 带着 4 枚爱好预设按 Next。
- “I don't care about height” 在不离开该页的情况下，要多久才把 Next 放开。
- motion JSON、计算样式 JSON、以及 pass1 / pass2 视频。都没有落盘。样式数字在第 7 节，打字间隔只有姓名页这一次。
- 05 视觉稿里的营销站数值不在这次入驻会话里重测。

## 10. 资料编辑页补走兴趣分支（11-12 下午）

账号：用户自己的已入驻测试账号（非学生）。改完已还原，整页资料文本与改前逐字一致。截图在 `assets/ditto-onboarding/shots/profile-edit/`。

### 10.1 入口

- 首页先弹「Be first to know when you match 💌」，正文 `Just send a pre-filled text to verify your number. We'll let you know when your next match is ready.`，按钮 `Verify with iMessage`，右上 Close。没点验证。`00-home-imessage-dialog.png`【已证实】
- 底部导航 `Ditto / Where / You`。`/you` 标题 `What Ditto knows about you`，三张卡：Profile（带照片缩略）、Deep Info（`Ideal look, religion, politics, and what matters to you.`）、Blocklist（`People you never want Ditto to match you with.`）。`02-you.png`【已证实】
- Profile 进 `/edit-profile`，分三组：`Tell Ditto about yourself`、`Tell Ditto your type`、`Pics of your vibe`。每行是一个 `Edit <问句>` 按钮，显示当前答案。`03-profile-full.png`【已证实】
- 非学生账号这里能看到职场字段的问句：`What do you do for work?`、`Highest level of education`（值 Graduate degree）、`Budget for a first date (per person)`（No preference）、`Does your match's education have to be at least yours?`（Doesn't matter）、`How do you want Ditto to match you rn?`（More dates, faster）。只读，没点进去。【已证实】

### 10.2 爱好编辑

- 点爱好进 `/onboarding/edit/hobbyRanking`，复用入驻的同一题。顶栏换成返回箭头 + `Edit Profile`。`10-hobbies-edit.png`【已证实】
- 原值是自定义 `sport`，进来后 8 枚预设都没选中，自定义胶囊 `sport` 在列表末尾。【已证实】
- **最多 5 枚**。选满 5 枚后其余变 disabled，出现提示 `max 5. deselect one to swap`。`13-hobbies-five-max.png`【已证实】
- **按钮随选择变化**：只有自定义 / 无分支时是 `Done`（直接保存回资料页）；勾了带分支的预设后变成圆形 `Next`，进入分支追问，走完自动回 `/edit-profile` 并保存。【已证实】

### 10.3 一次最多追问 3 个爱好

- 勾选顺序 Reading → Gym → Gaming → Movies/TV → Art（5 枚），实际只追问了 **Gym、Gaming、Art**，Reading 和 Movies/TV 跳过。【已证实】
- 第二次只勾 Reading + Movies/TV，两条都追问了。【已证实】
- 结论：一次最多追问 3 个；挑哪 3 个**不是**勾选顺序，也不是题库顺序（题库是 Cooking/Music/Travel/Reading/Gym/Gaming/Movies/Art）。规则未确认，可能随机。【推测】
- 资料页回显格式：`Gym — Lifting, Running; training for: test`，没追问的只显示名字 `Reading`。`26-profile-after-5.png`【已证实】

### 10.4 五个分支（与 questionnaire.json 逐字一致）

每个分支 = 一道多选（选项 + 末尾 `Other` 胶囊，点开是占位 `Other` 的输入框）+ 一道短文本。多选选中变品红胶囊带勾，至少选 1 个 Next 才可点。

| 爱好 | 多选问句 | 选项 | 短文本问句 | 占位 | 截图 |
|---|---|---|---|---|---|
| Reading | what genres u reading rn? | 📖 Lit fic／🐉 Fantasy／🚀 Sci-fi／🔍 Mystery／💕 Romance／🪞 Memoir／🧠 Self-help／🌹 Poetry／📚 Classics／😏 Smut idk | last book u finished? | name + author | 32、33 |
| Gym | what's your training style? | 🏋️ Lifting／🏃 Running／🧘 Yoga／🩰 Pilates／💪 Crossfit／🥊 Boxing／💃 Dance／🚴 Cycling／🧗 Climbing | what u training for? | aesthetic, marathon, principle... | 20、21 |
| Gaming | what games u into? | 🔫 FPS／⚔️ RPG／⚽ Sports games／🎮 MOBA／🧠 Strategy／🎨 Indie／🌱 Sims/cozy／👊 Fighting／🌐 MMO | what u playing rn? | game on repeat rn | 22、23 |
| Movies/TV | what kinda content u like? | 🎞️ Indie／👻 Horror／💞 Rom-com／💥 Action／🛸 Sci-fi／📹 Documentary／🎬 A24 stuff／🌍 Foreign／🎌 Anime／📺 Reality TV | last great watch? | name it | 34、35 |
| Art | what mediums u work in? | ✏️ Drawing／🎨 Painting／💻 Digital／🏺 Pottery／🗿 Sculpture／✂️ Collage／🧵 Crafts／🌈 Mixed media | what do u make? | describe your work | 24、25 |

### 10.5 仍未覆盖

职场流程各页的编辑态（只读了值）、Deep Info、Blocklist、学校邮箱验证、/join（手机号、邀请好友、iMessage 末页）、性别组合触发的两页、全部动效录屏。

