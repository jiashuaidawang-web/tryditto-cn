# 10 Ditto 入驻学生路径 pass2 实录

采集日期：2026-11-12。视口 390×844。账号保持登录。本轮先开录屏再进 `https://app.tryditto.com/onboarding`，路由在停录前一直是这一条，没有按题目分 URL。【已证实】

视频：`assets/videos/pass2/onboarding-student-full.webm`，ffprobe 时长 **220.32 秒**。录屏在学校邮箱页停住（停录前在该页多留了 2 秒）。停录之后才按返回键退了 3 步，所以视频里的最后一帧是学校邮箱，浏览器后来停在年龄范围页。退 3 步的截图是 `rec-14-backed-3.png`，不在「停录当时」的画面里。【已证实】

本轮截图前缀 `rec-`，目录 `assets/ditto-onboarding/shots/pass2-student/`，共 36 张。同目录里没有 `rec-` 前缀的图是更早一轮中断采集，不要和这条视频对帧。

证据标记沿用 09：【已证实】是当次 DOM 或截图能直接读到的；【推断】是多条已证实事实拼出来的。草稿里回填的姓名、生日、族裔只当控件状态，不当人物资料。

## 1. 这一轮的页序

账号草稿还在。`browser_start_video` 换过浏览器会话，但姓名、性别、生日、族裔、身高单位、身高范围都是带着旧答案进来的，不是空白新号。【已证实】

| 序 | 页 | 本轮进度（三段填充 px） | 截图 |
|---|---|---|---|
| 1 | isStudent | 23, 0, 0 | `rec-01-isStudent-yes.png`、`rec-01-isStudent-nope.png` |
| 2 | name | 未重测 | `rec-02-name-arrival.png`、`rec-02-name-filled.png` |
| 3 | gender | 70, 0, 0 | `rec-03-gender-arrival.png`、`rec-03-gender-man.png` |
| 4 | expectedGender | 93, 0, 0 | `rec-04-expected-arrival.png`、`rec-04-expected-empty.png`、`rec-04-expected-women.png` |
| 5 | birthday | 未重测 | `rec-05-birthday-arrival.png`、`rec-05-birthday-filled.png` |
| 6 | hobbies | 选满 Reading/Gym/Gaming 后 117, 8, 0 | `rec-06-hobbies-*.png` |
| 7 | readingGenres | 117, 17, 0 | `rec-07a-reading-*.png` |
| 8 | readingLastBook | 117, 25, 0 | `rec-07b-book-*.png` |
| 9 | gymTrainingStyle | 117, 33, 0 | `rec-07c-gym-*.png` |
| 10 | gymTrainingFor | 117, 42, 0 | `rec-07d-gymfor-*.png` |
| 11 | gamingGenres | 117, 50, 0 | `rec-07e-games-*.png` |
| 12 | gamingNowPlaying | 117, 58, 0 | `rec-07f-now-*.png` |
| 13 | ethnicity | 117, 67, 0 | `rec-08-ethnicity-*.png`、`rec-08-next-empty.png` |
| 14 | expectedEthnicity | 117, 75, 0 | `rec-09-expected-eth-*.png` |
| 15 | expectedAgeRange | 117, 83, 0 | `rec-10-age.png` |
| 16 | height | 117, 92, 0 | `rec-11-height.png` |
| 17 | heightRange | 117, 100, 0 | `rec-12-height-range.png` |
| 18 | email（学校邮箱） | 117, 108, 0 | `rec-STOP-school-email.png` |

三条爱好时，第二段的 17 / 25 / 33 / 42 / 50 / 58 / 67 / 75 / 83 / 92 / 100 与 09 里烹饪、音乐、旅行那一组逐格相同。09 把 108 记在照片页；这一轮 108 落在学校邮箱，照片页没有出现。【已证实】

爱好页刚从生日进来时，草稿仍勾着 Movies/TV 和 Art，第二段是 10 而不是 8。改成三枚 Reading/Gym/Gaming 之后回到 8。【已证实】

## 2. 逐页原文（本轮录屏）

顶栏：第一题 isStudent 只有 Ditto 字标和锁，没有返回。从 name 起有返回（aria-label `Back`）。锁没有再打开；锁里的句子见第 7 节前一轮。【已证实】

### isStudent

- 气泡：`are you currently a student?`，`student` 拆字。
- 提示：`Private. Only used to match you`。
- 选项：`yes, i'm a student`（学士帽）、`nope, not a student`（公文包）。
- 进来时 yes 已经是选中。点 nope 会换成 nope；再点 yes 回到 yes。然后 Next。【已证实】
- 第一题没有 Back。

### name

- 两只气泡同时在：`Hi, I'm Ditto` 和 `what's your name?`。09 记的是开场被问句换掉。这一轮两句都还在。【已证实】
- 占位：`your name`。输入框里已有草稿 `Alex`，Send 可点。空态这一轮没再清出来。

### gender

- 气泡：`what's your gender?`
- 选项：`Man`、`Woman`、`Nonbinary`、`Other`。进来已选 Man。JSON 写的是 Male / Female。【已证实】

### expectedGender

- 气泡：`Who are you looking to date?`
- 提示：`Private. Only used to match you`。
- 选项：`Men`、`Women`、`Nonbinary`、`Everyone`。进来已选 Women。点掉 Women 后一项都不选，Next 禁用；再点 Women，只留 Women，然后 Next。【已证实】

### birthday

- 气泡：`when's your birthday?`
- 副文案：`Only your age is shown to others.`
- 占位：`MM / DD / YYYY`。草稿已是 `01 / 15 / 2004`，Send 可点。这一轮没有再走未成年确认层。

### hobbies

- 气泡：`what are your hobbies & interests?`
- 八枚：Cooking、Music、Travel、Reading、Gym、Gaming、Movies/TV、Art，外加 `Type your hobby`。
- 进来时勾着 Movies/TV 和 Art（前一轮留下的）。点掉之后出现空选，再按 Reading、Gym、Gaming 勾上，只留这三枚，然后 Next。【已证实】
- 这一轮没有再加第四枚，也没有再开自定义输入。

### Reading / Gym / Gaming

勾选顺序就是追问顺序：Reading，然后 Gym，然后 Gaming。每条都是多选再一题短文本。选项和占位与 `questionnaire.json` 一致，多选末尾多一枚 JSON 选项数组里没有的 `Other`。【已证实】

| 题 | 气泡 | 选项或占位 | 本轮操作 |
|---|---|---|---|
| readingGenres | what genres u reading rn? | Lit fic、Fantasy、Sci-fi、Mystery、Romance、Memoir、Self-help、Poetry、Classics、Smut idk、Other | 选 Lit fic |
| readingLastBook | last book u finished? | 占位 `name + author` | 填入短句后 Send |
| gymTrainingStyle | what's your training style? | Lifting、Running、Yoga、Pilates、Crossfit、Boxing、Dance、Cycling、Climbing、Other | 选 Lifting |
| gymTrainingFor | what u training for? | 占位 `aesthetic, marathon, principle...` | 填入短句后 Send |
| gamingGenres | what games u into? | FPS、RPG、Sports games、MOBA、Strategy、Indie、Sims/cozy、Fighting、MMO、Other | 选 FPS |
| gamingNowPlaying | what u playing rn? | 占位 `game on repeat rn` | 填入短句后 Send |

Other 这一轮没有再点开。前一轮点开时，输入占位就是 `Other`。

### ethnicity

- 气泡：`What's your ethnicity?`，`ethnicity` 拆字。
- 提示：`Private. Only used to match you`。
- 九项，没有 No Preference。进来已选 East Asian（控件回填）。Next 可点。

### expectedEthnicity

- 气泡：`Any ethnicity preferences?`
- 同一句 Private。
- 列表在上一题前面多 `No Preference`。进来已选 No Preference。Next 可点。这一轮没有再做独占切换；独占行为在前一轮测过，见第 7 节。

### expectedAgeRange

- 气泡：`What's your preferred age range?`，`age range` 拆字（两词之间是不断行空格）。
- Private。
- 滑杆 aria：`Minimum age`、`Maximum age`，最小 17、最大 36。界面数字是 18 和 32。Next 可点。32 是前一轮把最大端从 30 按了两次右方向键留下的，不是 JSON 默认的 24。【已证实】

### height

- 气泡：`How tall are you?`，`tall` 拆字。
- 进来就是厘米：读数 `183 cm`，刻度 130 到 240，单位按钮 `ft / in` 和 `cm`。Next 已可点。09 的空白到达是 `5'8"` 且 Next 要拖过才启用。这一轮是草稿停在厘米，没有再拖尺规。【已证实】

### heightRange

- 气泡：`how tall should your match be?`
- Private。
- 进来时 `🤷 I don't care about height` 已选中，尺规不在，Next 可点。点 Next 之后进入学校邮箱，中间没有照片页、没有地点页。【已证实】

### email（学校邮箱，停在这里）

- 气泡：`what's your school email? 📧`，`email` 拆字。
- 占位：`you@school.edu`。输入框是空的。
- 提示：`we need a school email (.edu) to verify you're a student`。
- 另一枚按钮，不是链接：`not a student? continue without a school email`。
- Send 的 `aria-disabled=true`。
- 没有点这枚「不是学生」按钮，没有往输入框里打字，没有点 Send。【已证实】
- 进度 117, 108, 0。第三段仍是 0。

## 3. 和 questionnaire.json、09 的差异（本轮）

| 点 | 本轮 |
|---|---|
| email | JSON 把学校邮箱放在身高范围和照片之间。09 的学生路径从身高范围直接进了照片，记成这页没出现。本轮 yes 学生、身高范围 Next 之后就是这页，照片还没出现。文案、占位、逃生按钮与 JSON 一致。逃生控件是 button，不是 `<a>`。 |
| 进度 108 | 09 的 108 在照片页。本轮 108 在学校邮箱。不能据此说照片被删掉，只是这一轮在邮箱页停了，后面没看。 |
| name | 09：`Hi, I'm Ditto` 打完后被问句换掉。本轮两只气泡同时留着。 |
| gender | 仍是 Man / Woman，不是 JSON 的 Male / Female。 |
| 爱好三条 | Reading、Gym、Gaming 的问句、选项、占位与 JSON 一致。Other 仍是多出来的胶囊。追问顺序等于勾选顺序。 |
| 三条爱好的进度 | 与 09 的烹饪/音乐/旅行槽位相同。前一轮只勾 Movies/TV + Art 时，对应槽位是 19 / 29 / 39 / 49，不是这组数字。填充宽度跟着这一路有几道追问变，不是每道爱好写死的像素。【已证实】两次对比。 |
| expectedPosition / position | Man 且只选 Women 时，年龄下一题仍是身高，体位题没出现。与 09 一致。 |
| preferencesIntro / photos / location / datingHelp | 本轮都没走到。 |

## 4. 停点与硬禁

录屏停在学校邮箱。没有提交入驻，没有登出，没有 deactivate，没有填邮箱或手机号，没有点 datingHelp（那页没出现）。

停录后的回退，只点了返回：

1. 学校邮箱 → `how tall should your match be?`
2. 身高范围 → `How tall are you?`
3. 身高 → `What's your preferred age range?`

浏览器现停在年龄范围。`rec-14-backed-3.png`。【已证实】

## 5. 本轮没覆盖

- 学校邮箱之后：照片、地点、关系、匹配方式、学年、从哪听说、雷区、政治、宗教、第一句话、datingHelp。
- 点「not a student? continue without a school email」之后的职场题。那是前一轮点过的，不在这条视频里。见第 7 节。
- 验证码、摄像头、短信。
- Cooking / Music / Travel / Movies/TV / Art 的追问。Movies 和 Art 的入驻页文案在前一轮截过；烹饪、音乐、旅行在 09。
- 身高从英尺空白态拖动才启用 Next。本轮到达时已经是厘米并且 Next 可点。
- 锁的说明层。本轮没开。

## 6. 录屏

`assets/videos/pass2/onboarding-student-full.webm`。390×844，220.32 秒。文件是从仓库根目录的 `onboarding-student-full.webm` 挪过来的，那是 Playwright MCP 的当前工作目录。

每题进入后等打字稳定再操作，选中后约 1 秒再点 Next。视频在学校邮箱页结束，不包含之后的三次返回。

## 7. 前一轮（中断，不在这条视频里）

同目录下没有 `rec-` 前缀的图，是录屏开始之前的另一轮。那一轮也从 isStudent 选了 yes，爱好先走了 Reading/Gym/Gaming，又退回爱好页改成 Movies/TV + Art，然后继续往下。它**点了**学校邮箱上的 `not a student? continue without a school email`，所以后半段不再是「留在学生、停在邮箱」这条路径。没有交卷。用户叫停时，浏览器停在个人邮箱页，输入框已被清空。

那一轮点逃生之后实际出现过的页：

| 顺序 | 气泡 | 备注 |
|---|---|---|
| 学校邮箱 | what's your school email? 📧 | 当时输入框里有一个非 .edu 的 gmail 草稿，Send 仍禁用。随后点了逃生按钮。 |
| 过场 | now let's talk about your preferences | 09 没见到这句。进度到了第三段（约 117, 117, 15）。 |
| occupation | what do you do for work? | 占位 `e.g. software engineer at a tech company`。空 textarea 时 Next 禁用。 |
| firstDateBudget | what’s ur budget for a first date? | 副文案 `per person, roughly`。Private。选项 `under $20`、`$20–50`、`$50–100`、`$100+`、`no preference`，前面带 JSON 里的那些 emoji。 |
| dealbreakers | dealbreakers: what should Ditto avoid? | 副文案和占位与 09 一致，并有 Private。 |
| educationLevel | what's the highest level of education you've completed? | Private。`high school`、`college degree`、`graduate degree`、`prefer not to say`。 |
| educationImportance | does your match's education have to be at least yours? | Private。`doesn't matter`、`kinda matters`、`super important`。 |
| 个人邮箱 | what's your email? 📧 | 占位 `you@email.com`。副文案 `don't lose access to your account, verify your email`。没有逃生按钮。空值和 `not-an-email` 时 Send 都禁用。没有提交。 |

这一轮还打开过锁：标题 `Ditto Privacy`，正文 `Preferences marked with a 🔒 lock icon are private, they're not included in ur profile shown to other users, and are only used to match u with the best person.` 按钮 `Got it`。09 没抄到这层。【已证实】

点逃生之后，关系、匹配方式、学年、从哪听说、政治、宗教、datingHelp、照片、地点都没有再出现在那一轮里。从「你做什么工作」起，题序已经变成职场插入，不是 09 的学生后半段。个人邮箱页 JSON 里没有同文案的一题；JSON 的 email 仍是学校邮箱 `you@school.edu`。【已证实】

那一轮还看到：expectedGender 可以选空，Everyone 独占；expectedEthnicity 的 No Preference 独占；年龄滑杆右方向键能把最大端从 30 推到 32；身高 6 英尺切到厘米是 183 cm，不是 09 里 5'8" 的 173 cm；「I don't care about height」刚点上时 Next 可以仍是禁用，过一会儿流程自己到了学校邮箱（中间没有再点 Next）。最后这条是两次观察之间页面已经换了，不能写成「胶囊一定自动提交」。【已证实】页面换了；【推断】自动前进。

Movies/TV 与 Art 的入驻文案（前一轮，进度不是三条爱好那组）：

- `what kinda content u like?`：Indie、Horror、Rom-com、Action、Sci-fi、Documentary、A24 stuff、Foreign、Anime、Reality TV、Other。短文本 `last great watch?`，占位 `name it`。
- `what mediums u work in?`：Drawing、Painting、Digital、Pottery、Sculpture、Collage、Crafts、Mixed media、Other。短文本 `what do u make?`，占位 `describe your work`。

与 JSON 一致，同样多一枚 Other。
