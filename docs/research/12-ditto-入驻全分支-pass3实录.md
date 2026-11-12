# 12 Ditto 入驻全分支 pass3 实录

调研日 2026-11-12。本文只做离线分析：读日志、读截图、ffmpeg 全解码抽帧量像素。没有开浏览器，没有联网，没有重跑录制脚本。

素材都在 `docs/research/`：

- 视频：`assets/videos/pass3/{S1,S2,S3,N1,N2,N3}-*.mp4`（390×844，H.264，25 fps）。原始 webm 和日志是 `{id}-raw.webm`、`{id}-log.json`。
- 截图：`assets/ditto-onboarding/shots/pass3/{id}/NN-a.png`（到页约 2.6 秒后）、`NN-b.png`（操作后约 1 秒、点 Next 之前）。截图是 2 倍像素（780×1688）。
- 本文引用的帧：`assets/videos/pass3/frames/{id}/`。
- 录制脚本：`tools/rec-onboarding.mjs`。

证据标记沿用 09–11：

- 【已证实】：帧上的像素计数、截图上能直接读到的字、日志里的 innerText。
- 【推断】：几条已证实事实拼出来的解释。不能直接写进实现。

账号草稿里回填的姓名、生日、族裔、邮箱都只当控件状态。邮箱在截图里是明文的个人 gmail，本文一律不抄，引用的帧也裁掉了邮箱输入框。

## 0. 素材与时间轴

### 0.1 六条录像

| id | 配置 | mp4 时长 | 加载时落在哪页（日志 `loaded`） | 停在 |
|---|---|---|---|---|
| S1 | 学生，Man → Women，Cooking/Music/Travel | 83.80s | `let's verify that email 📬`（验证码页） | 学校邮箱 |
| S2 | 学生，Woman → Men，Reading/Gym/Gaming | 85.84s | 学校邮箱 | 学校邮箱 |
| S3 | 学生，Nonbinary → Everyone，Movies/TV / Art / Cooking | 98.72s | 学校邮箱 | 学校邮箱 |
| N1 | 非学生，Man → Women，Cooking/Music/Travel | 111.28s | 学校邮箱 | 个人邮箱 |
| N2 | 非学生，Woman → Men，Reading/Gym/Gaming | 112.08s | 学校邮箱 | 个人邮箱 |
| N3 | 非学生，Nonbinary → Everyone，Movies/TV / Art / Cooking | 162.48s | `let's verify that email 📬` | 个人邮箱 |

实际录制顺序不是 S1→N3。按 `*-log.json` 的修改时间，顺序是 **N1 → S1 → S2 → S3 → N2 → N3**（19:39、19:42、19:44、19:46、19:49、19:52）。同一个账号连着跑，上一条留下的草稿就是下一条的预填。下面讲预填和分支时都按这个顺序。【已证实】mtime；【推断】mtime 等于跑完的顺序。

每条加载时都先出过一次提示：`👋` `Welcome back` / `Your progress is saved. Pick up where you left off.` 脚本随后连按 Back 退到 isStudent，再记 `START`。这段不在 mp4 里。【已证实】日志文本。

加载落点有规律：上一条停在个人邮箱（N1、N2），下一条就从验证码页进来（S1、N3），页面文案里有 `sending code…`。上一条停在学校邮箱，下一条就从学校邮箱进来。【已证实】六条日志。【推断】验证码页加载时会自动发码，所以 S1 和 N3 开跑时，账号邮箱可能各收到一封验证码。

### 0.2 日志时间和 mp4 时间对不齐

题目给的换算是 `mp4 时间 = mark.t − START.t`。实测有一个每条录像固定的偏移 Δ。下表的 Δ = 视频里翻页开始的时刻 − 日志里 `next NN` 的时刻（按顺序一一配对）：

| id | Δ 中位 | 范围 |
|---|---|---|
| S1 | +0.87s | +0.41 … +1.07 |
| S2 | −3.38s | −3.78 … −2.66 |
| S3 | +0.80s | +0.35 … +1.59 |
| N1 | +0.65s | −0.04 … +1.19 |
| N2 | −3.84s | −4.66 … −3.73 |
| N3 | −4.50s | −5.31 … −3.81 |

同一条录像里 Δ 不漂，但会在两档之间来回跳，前后两档差约 0.45 秒（例如 S1 是 1.06、0.52、1.07、0.54…）。这和页面有没有点击动作无关。【已证实】。【推断】Playwright 屏幕录制帧的时间戳一阵一阵地落，抖动约半秒。N3 的 `STOP` 记在 165.71s，mp4 却只有 162.48s 长，就是 Δ≈−4.5 造成的。

**本文所有 mp4 时间都从画面上量**，不用日志换算。

### 0.3 截图伪影

脚本每次 `page.screenshot`（2 倍像素），录像里就会出现 0.4–1.9 秒的「放大帧」：画面只剩左上四分之一，放大 2 倍。见 `frames/S1/s1-zoom-artifact-8.80s.png`。这些帧里进度条落在 y≈130，气泡左边缘在 x=32。计算时用规则「第二段 ≤5 且第三段 ≥40，或三段全 0」把它们识别出来，换成前一帧。这不是 App 的界面。【已证实】

pass2（11 号文档）说「绝大多数帧只在左上角约 96×192 画了界面」。【推断】那也是同一类录屏缩放伪影，不是 App 行为。

### 0.4 怎么量的

- 每条 mp4 都按 25 fps 全解码，每帧（40ms）出一行指标：
  - 三段进度条在 y=65 上的亮像素数，1 个计数 = 1px。
  - x=22 那一列上气泡白块的上下沿，以及气泡第一行的右边缘。
  - 气泡里深色字的像素数。
  - 品红像素数。
  - 和上一帧的差值。
- 翻页：旧气泡消失记为「清空」；新气泡第一次出现记为「入场」；气泡上沿回到终点 ±2px 记为「落定」；右边缘每跳一次记为一个词。
- 内容块整体位移：每行横向梯度能量做一维剖面，和落定后的那帧做互相关，得出每帧的纵向偏移（`shift`）。
- 选中态：在胶囊内部取 30×10 的色块求平均，另外量胶囊左边缘的 x。
- 一共统计了 131 次翻页（6 条，扣掉首页），518 个词间隔。

## 1. 六条路径页序

列说明：

- 「入场」是新页内容第一次出现。第 01 页开录时已经在屏上，记 0。
- 「打完」是气泡字像素进入平台。「离场」是旧内容开始上移。
- 「进度」是三段条的填充 px，每段约 117px 满。
- 截图是 `shots/pass3/{id}/NN-a.png` 和 `NN-b.png`；最后一页只有 `-a`。
- 「操作」取自日志：`草稿` 表示到页时 Next 已可点，脚本直接点 Next；`点1` 表示点第一项；`填` 表示往空输入框填 `test`。

### S1 学生 · Man → Women · Cooking/Music/Travel

| NN | 页 | 入场 | 打完 | 离场 | 进度 | 操作 |
|---|---|---|---|---|---|---|
| 01 | isStudent | 0.00 | — | 6.40 | 23/0/0 | 到页是 nope（N1 留下的），点 yes |
| 02 | name | 6.60 | 7.44 | 10.40 | 47/0/0 | 草稿 |
| 03 | gender | 10.60 | 11.12 | 16.20 | 70/0/0 | Man（已选） |
| 04 | expectedGender | 16.36 | 17.12 | 20.32 | 93/0/0 | Women |
| 05 | birthday | 20.48 | 20.96 | 25.28 | 117/0/0 | 草稿 |
| 06 | hobbies | 25.52 | 26.24 | 29.40 | 117/7/0 | 到页已是 C/M/T |
| 07 | cookingStyle | 29.52 | 30.00 | 34.20 | 117/15/0 | 草稿 Italian |
| 08 | cookingSignatureDish | 34.36 | 34.84 | 38.40 | 117/23/0 | 草稿 |
| 09 | musicSound | 38.60 | 39.12 | 43.28 | 117/31/0 | 草稿 Pop |
| 10 | musicNowPlaying | 43.40 | 43.96 | 47.44 | 117/39/0 | 草稿 |
| 11 | travelStyle | 47.68 | 48.16 | 52.40 | 117/46/0 | 草稿 Backpacking |
| 12 | travelNext | 52.56 | 53.12 | 56.60 | 117/54/0 | 草稿 |
| 13 | ethnicity | 56.84 | 57.32 | 61.44 | 117/62/0 | 草稿 |
| 14 | expectedEthnicity | 61.60 | 62.08 | 65.48 | 117/70/0 | 草稿 |
| 15 | expectedAgeRange | 65.60 | 66.20 | 70.44 | 117/77/0 | 草稿 18–32 |
| 16 | height | 70.60 | 71.12 | 74.56 | 117/85/0 | 草稿 183 cm |
| 17 | heightRange | 74.68 | 75.40 | 79.52 | 117/93/0 | 草稿 I don't care |
| 18 | 学校邮箱（停） | 79.68 | 80.28 | — | 117/101/0 | — |

### S2 学生 · Woman → Men · Reading/Gym/Gaming

| NN | 页 | 入场 | 打完 | 离场 | 进度 | 操作 |
|---|---|---|---|---|---|---|
| 01 | isStudent | 0.00 | — | 2.16 | 23/0/0 | yes |
| 02 | name | 2.36 | 3.16 | 6.20 | 47/0/0 | 草稿 |
| 03 | gender | 6.44 | 6.92 | 11.88 | 70/0/0 | Man → Woman |
| 04 | expectedGender | 12.04 | 12.80 | 17.92 | 93/0/0 | Women → Men |
| 05 | birthday | 18.08 | 18.56 | 22.00 | 117/0/0 | 草稿 |
| 06 | hobbies | 22.24 | 22.96 | 31.40 | 117/8/0 | C/M/T 改为 R/G/G |
| 07 | readingGenres | 31.56 | 32.16 | 35.60 | 117/16/0 | 空，点1 Lit fic |
| 08 | readingLastBook | 35.76 | 36.20 | 40.40 | 117/25/0 | 空，填 |
| 09 | gymTrainingStyle | 40.64 | 41.12 | 44.68 | 117/33/0 | 空，点1 Lifting |
| 10 | gymTrainingFor | 44.84 | 45.32 | 49.48 | 117/41/0 | 空，填 |
| 11 | gamingGenres | 49.68 | 50.24 | 53.76 | 117/50/0 | 空，点1 FPS |
| 12 | gamingNowPlaying | 53.92 | 54.48 | 58.64 | 117/58/0 | 空，填 |
| 13 | ethnicity | 58.88 | 59.36 | 62.72 | 117/66/0 | 草稿 |
| 14 | expectedEthnicity | 62.88 | 63.32 | 68.16 | 117/75/0 | 草稿 |
| 15 | expectedAgeRange | 68.28 | 68.84 | 71.68 | 117/83/0 | 草稿 |
| 16 | height | 71.84 | 72.40 | 76.60 | 117/91/0 | 草稿 |
| 17 | heightRange | 76.76 | 77.44 | 80.76 | 117/100/0 | 草稿 |
| 18 | 学校邮箱（停） | 80.92 | 81.52 | — | 117/108/0 | — |

### S3 学生 · Nonbinary → Everyone · Movies/TV / Art / Cooking

| NN | 页 | 入场 | 打完 | 离场 | 进度 | 操作 |
|---|---|---|---|---|---|---|
| 01 | isStudent | 0.00 | — | 6.24 | 23/0/0 | yes |
| 02 | name | 6.44 | 7.28 | 10.24 | 47/0/0 | 草稿 |
| 03 | gender | 10.48 | 10.96 | 15.96 | 70/0/0 | Nonbinary |
| 04 | expectedGender | 16.12 | 16.88 | 21.92 | 93/0/0 | Everyone |
| 05 | birthday | 22.08 | 22.60 | 27.24 | 117/0/0 | 草稿 |
| 06 | hobbies | 27.48 | 28.24 | 35.40 | 117/7/0 | R/G/G 改为 Movies/TV、Art、Cooking（按这个顺序点） |
| 07 | cookingStyle | 35.56 | 36.08 | 39.64 | 117/14/0 | 空，点1 Italian |
| 08 | cookingSignatureDish | 39.76 | 40.28 | 44.40 | 117/22/0 | 空，填 |
| 09 | moviesContent | 44.64 | 45.24 | 48.72 | 117/29/0 | 空，点1 Indie |
| 10 | moviesLastWatch | 48.88 | 49.20 | 53.44 | 117/36/0 | 空，填 |
| 11 | artMediums | 53.68 | 54.28 | 57.76 | 117/43/0 | 空，点1 Drawing |
| 12 | artMake | 57.92 | 58.48 | 62.60 | 117/51/0 | 空，填 |
| 13 | ethnicity | 62.84 | 63.32 | 66.60 | 117/58/0 | 草稿 |
| 14 | expectedEthnicity | 66.76 | 67.24 | 71.44 | 117/65/0 | 草稿 |
| 15 | expectedAgeRange | 71.60 | 72.16 | 75.60 | 117/73/0 | 草稿 |
| 16 | **your ideal match?** | 75.76 | 76.20 | 80.64 | 117/80/0 | 空，点1 top |
| 17 | **how would u describe yourself?** | 80.80 | 81.48 | 84.72 | 117/87/0 | 空，点1 top |
| 18 | height | 84.92 | 85.40 | 89.60 | 117/94/0 | 草稿 |
| 19 | heightRange | 89.76 | 90.44 | 93.88 | 117/102/0 | 草稿 |
| 20 | 学校邮箱（停） | 94.04 | 94.64 | — | 117/109/0 | — |

### N1 非学生 · Man → Women · Cooking/Music/Travel

| NN | 页 | 入场 | 打完 | 离场 | 进度 | 操作 |
|---|---|---|---|---|---|---|
| 01 | isStudent | 0.00 | — | 6.28 | 23/0/0 | 点 nope |
| 02 | name | 6.48 | 7.32 | 10.36 | 47/0/0 | 草稿 |
| 03 | gender | 10.60 | 11.08 | 16.12 | 70/0/0 | Man |
| 04 | expectedGender | 16.24 | 17.00 | 20.24 | 93/0/0 | Women |
| 05 | birthday | 20.40 | 20.92 | 25.20 | 117/0/0 | 草稿 |
| 06 | expectedAgeRange | 25.44 | 26.00 | 29.40 | 117/9/0 | 草稿 |
| 07 | ethnicity | 29.56 | 30.04 | 34.28 | 117/18/0 | 草稿 |
| 08 | expectedEthnicity | 34.44 | 34.88 | 38.28 | 117/27/0 | 草稿 |
| 09 | height | 38.44 | 38.96 | 43.20 | 117/36/0 | 草稿 |
| 10 | heightRange | 43.32 | 44.04 | 47.32 | 117/45/0 | 草稿 |
| 11 | hobbies | 47.48 | 48.24 | 52.20 | 117/54/0 | C/M/T |
| 12 | cookingStyle | 52.36 | 52.84 | 56.24 | 117/62/0 | 草稿 |
| 13 | cookingSignatureDish | 56.40 | 56.88 | 61.20 | 117/71/0 | 草稿 |
| 14 | musicSound | 61.40 | 61.92 | 65.28 | 117/80/0 | 草稿 |
| 15 | musicNowPlaying | 65.40 | 65.92 | 70.12 | 117/89/0 | 草稿 |
| 16 | travelStyle | 70.36 | 70.84 | 74.24 | 117/98/0 | 草稿 |
| 17 | travelNext | 74.36 | 74.96 | 79.52 | 117/107/0 | 草稿 |
| — | 转圈 | 79.56 | — | ≈80.5 | 117/116/0 | 约 0.9s，无气泡 |
| 18 | preferencesIntro | 80.48 | 81.40 | 87.52 | 117/116/16 | 草稿（Next 可点） |
| 19 | occupation | 87.72 | 88.48 | 92.92 | 117/116/33 | 草稿 `test role` |
| 20 | firstDateBudget | 93.16 | 93.92 | 97.00 | 117/116/49 | 草稿 |
| 21 | educationLevel | 97.16 | 98.04 | 101.96 | 117/116/66 | 草稿 |
| 22 | educationImportance | 102.12 | 103.12 | 106.08 | 117/116/83 | 草稿 |
| 23 | 个人邮箱（停） | 106.24 | 106.72 | — | 117/116/99 | — |

### N2 非学生 · Woman → Men · Reading/Gym/Gaming

| NN | 页 | 入场 | 打完 | 离场 | 进度 | 操作 |
|---|---|---|---|---|---|---|
| 01 | isStudent | 0.00 | — | 1.60 | 23/0/0 | nope |
| 02 | name | 1.80 | 2.64 | 5.64 | 47/0/0 | 草稿 |
| 03 | gender | 5.88 | 6.36 | 11.40 | 70/0/0 | Woman |
| 04 | expectedGender | 11.56 | 12.32 | 17.44 | 93/0/0 | Men |
| 05 | birthday | 17.60 | 18.08 | 21.52 | 117/0/0 | 草稿 |
| 06 | expectedAgeRange | 21.76 | 22.32 | 26.56 | 117/9/0 | 草稿 |
| 07 | ethnicity | 26.72 | 27.20 | 30.60 | 117/18/0 | 草稿 |
| 08 | expectedEthnicity | 30.72 | 31.20 | 35.44 | 117/27/0 | 草稿 |
| 09 | height | 35.56 | 36.12 | 39.48 | 117/36/0 | 草稿 |
| 10 | heightRange | 39.64 | 40.36 | 44.48 | 117/45/0 | 草稿 |
| 11 | hobbies | 44.64 | 45.36 | 53.36 | 117/54/0 | 改为 R/G/G |
| 12 | readingGenres | 53.52 | 54.12 | 57.60 | 117/62/0 | 空，点1 |
| 13 | readingLastBook | 57.76 | 58.20 | 62.40 | 117/71/0 | 空，填 |
| 14 | gymTrainingStyle | 62.64 | 63.12 | 66.72 | 117/80/0 | 空，点1 |
| 15 | gymTrainingFor | 66.88 | 67.36 | 71.48 | 117/89/0 | 空，填 |
| 16 | gamingGenres | 71.72 | 72.24 | 75.80 | 117/98/0 | 空，点1 |
| 17 | gamingNowPlaying | 75.96 | 76.44 | 80.68 | 117/107/0 | 空，填 |
| — | 转圈 | 80.76 | — | ≈81.7 | 117/116/0 | 约 0.9s |
| 18 | preferencesIntro | 81.68 | 82.84 | 89.04 | 117/116/16 | 草稿 |
| 19 | occupation | 89.24 | 89.96 | 94.40 | 117/116/33 | 草稿 |
| 20 | firstDateBudget | 94.64 | 95.40 | 98.48 | 117/116/50 | 草稿 |
| 21 | educationLevel | 98.64 | 99.52 | 103.44 | 117/116/66 | 草稿 |
| 22 | educationImportance | 103.60 | 104.64 | 107.56 | 117/116/83 | 草稿 |
| 23 | 个人邮箱（停） | 107.72 | 108.28 | — | 117/116/99 | — |

### N3 非学生 · Nonbinary → Everyone · Movies/TV / Art / Cooking

| NN | 页 | 入场 | 打完 | 离场 | 进度 | 操作 |
|---|---|---|---|---|---|---|
| 01 | isStudent | 0.00 | — | 1.00 | 23/0/0 | nope |
| 02 | name | 1.24 | 2.04 | 5.04 | 47/0/0 | 草稿 |
| 03 | gender | 5.28 | 5.76 | 10.76 | 70/0/0 | Nonbinary |
| 04 | expectedGender | 10.92 | 11.68 | 16.76 | 93/0/0 | Everyone |
| 05 | birthday | 16.88 | 17.40 | 20.84 | 117/0/0 | 草稿 |
| 06 | expectedAgeRange | 21.04 | 21.64 | 25.88 | 117/7/0 | 草稿 |
| 07 | ethnicity | 26.04 | 26.56 | 29.92 | 117/15/0 | 草稿 |
| 08 | expectedEthnicity | 30.08 | 30.56 | 34.76 | 117/23/0 | 草稿 |
| 09 | **your ideal match?** | 34.92 | 35.36 | 38.84 | 117/31/0 | 草稿（S3 留下） |
| 10 | **how would u describe yourself?** | 39.04 | 39.68 | 43.80 | 117/39/0 | 草稿 |
| 11 | height | 43.96 | 44.48 | 47.88 | 117/46/0 | 草稿 |
| 12 | heightRange | 48.04 | 48.72 | 52.88 | 117/54/0 | 草稿 |
| 13 | hobbies | 53.04 | 53.76 | 61.80 | 117/62/0 | R/G/G 改为 Movies/TV、Art、Cooking |
| 14 | cookingStyle | 61.96 | 62.44 | 66.04 | 117/70/0 | 空，点1 |
| 15 | cookingSignatureDish | 66.20 | 66.68 | 70.80 | 117/77/0 | 空，填 |
| 16 | moviesContent | 71.00 | 71.64 | 75.08 | 117/85/0 | 空，点1 |
| 17 | moviesLastWatch | 75.24 | 75.56 | 79.88 | 117/93/0 | 空，填 |
| 18 | artMediums | 80.12 | 80.72 | 84.20 | 117/101/0 | 空，点1 |
| 19 | artMake | 84.36 | 84.96 | 89.32 | 117/109/0 | 空，填 |
| 20 | **photos** | 89.64 | 90.64 | 94.36 | 117/116/0 | 草稿（已有 2 张） |
| 21 | preferencesIntro | 94.52 | 95.24 | 97.56 | 117/116/7 | 草稿 |
| 22 | **relationshipLooking** | 97.72 | 98.84 | 103.76 | 117/116/14 | 草稿 |
| 23 | **matchingStyle** | 103.92 | 104.80 | 107.88 | 117/116/21 | 草稿 |
| 24 | occupation | 108.04 | 108.80 | 112.80 | 117/116/28 | 草稿 |
| 25 | firstDateBudget | 113.04 | 113.84 | 117.04 | 117/116/36 | 草稿 |
| 26 | **dealbreakers** | 117.24 | 118.56 | 122.00 | 117/116/43 | 草稿 `test note` |
| 27 | educationLevel | 122.12 | 123.08 | 126.12 | 117/116/50 | 草稿 |
| 28 | educationImportance | 126.28 | 127.36 | 131.12 | 117/116/58 | 草稿 |
| 29 | **politics** | 131.28 | 131.76 | 135.20 | 117/116/65 | 草稿 |
| 30 | **politicsImportance** | 135.36 | 136.28 | 140.20 | 117/116/72 | 草稿 |
| 31 | **religion** | 140.36 | 141.08 | 144.28 | 117/116/80 | 草稿 |
| 32 | **religionImportance** | 144.44 | 145.32 | 149.20 | 117/116/87 | 草稿 |
| 33 | **firstMessage** | 149.44 | 150.60 | 153.28 | 117/116/94 | 草稿 `a short sample` |
| 34 | **datingHelp** | 153.44 | 154.76 | 158.20 | 117/116/101 | 草稿，**只点了 Next**（见第 6 节） |
| 35 | 个人邮箱（停） | 158.36 | 158.88 | — | 117/116/109 | — |

粗体是 N1、N2 没出现的页。

## 2. 分支规则

### 2.1 学生和非学生的题序

| 位置 | 学生（S） | 非学生（N） |
|---|---|---|
| 第一段（5 格） | isStudent, name, gender, expectedGender, birthday | 同左 |
| 第二段开头 | hobbies → 3 条追问 | expectedAgeRange → ethnicity → expectedEthnicity |
| 第二段中部 | ethnicity → expectedEthnicity → expectedAgeRange | [ideal match → describe] → height → heightRange |
| 第二段后部 | [ideal match → describe] → height → heightRange | hobbies → 3 条追问 → [photos] |
| 之后 | 学校邮箱（停） | [转圈] → preferencesIntro → 第三段 → 个人邮箱 |

【已证实】六条全部如此。差别在于：学生先问爱好；非学生把「年龄 / 族裔 / 身高」这组「你的类型」提到前面，把爱好挪到第二段末尾。年龄题也换了位置：学生在 expectedEthnicity 之后，非学生紧跟 birthday。

### 2.2 Nonbinary / Everyone 多两页

S3 和 N3（gender = Nonbinary，expectedGender = Everyone）多出 `your ideal match?` 和 `how would u describe yourself?`。其余四条（Man→Women、Woman→Men）都没有。【已证实】

两页总是紧贴在 height 前面：S3 是 age → ideal → describe → height，N3 是 expectedEthnicity → ideal → describe → height。【已证实】

只有一个性别维度变了，四种组合没有拆开。所以「是 Nonbinary 触发，还是 Everyone 触发」分不出来。【推断】它们就是 09 里提到的 JSON 隐藏题 expectedPosition / position。

### 2.3 爱好追问的顺序 = 题库顺序，不是勾选顺序

S3 和 N3 都是先清掉 Reading/Gym/Gaming，再按 Movies/TV → Art → Cooking 的顺序点。追问却是 **Cooking → Movies/TV → Art**。【已证实】`S3/06-a.png`、`06-b.png`，以及第 1 节页序。

题库顺序是 Cooking、Music、Travel、Reading、Gym、Gaming、Movies/TV、Art。09 和 10 说「追问顺序等于勾选顺序」，那两轮的勾选顺序刚好就是题库顺序，属于巧合。本轮推翻了这一条。

09 第 10 节在资料编辑页勾 5 枚，只追问了 Gym、Gaming、Art。那是编辑流，选 3 条的规则另算，本轮没有覆盖。

### 2.4 追问的预填

按真实录制顺序（N1→S1→S2→S3→N2→N3）看：

| 本条 | 上一条的爱好 | 本条追问到页时 |
|---|---|---|
| N1 C/M/T | （录制前）C/M/T | 全部有草稿 |
| S1 C/M/T | N1 C/M/T | 全部有草稿 |
| S2 R/G/G | S1 C/M/T | 全空 |
| S3 Mo/Art/C | S2 R/G/G | 全空，连 Cooking 也空 |
| N2 R/G/G | S3 Mo/Art/C | 全空（S2 答过也空） |
| N3 Mo/Art/C | N2 R/G/G | 全空 |

【已证实】六条的到页状态。【推断】只要某个爱好在一次提交里被去掉，它的追问答案就会被清空；爱好一直保留，答案就跟着留。六条都符合。ideal / describe 不在这个规则里：S3 答完，N2（Woman→Men）不出这两页，N3 进来时仍是草稿。

### 2.5 preferencesIntro（`now let's talk about your preferences`）

- 只在非学生路径出现，是第三段的第 1 格（进度 x/116/16 或 x/116/7）。【已证实】
- **不会自动前进。** 这一页有可点的白色 Next。N1 停了 7.0s，N2 停了 7.4s，N3 停了 3.0s，每次离场都对上脚本点 Next 的时刻。【已证实】停留时长和 Next 按钮。
- 日志里 N1、N2 的 `auto-advanced` 是脚本误判。到页 2.6 秒时第一次读字，只读到 `now let's` / `now let's talk about your`，1 秒后再读，句子已经打完。前后文本不同，脚本就当成「自己翻页了」，又等了一轮才点 Next。之所以 2.6 秒还没打完，是因为这一页前面有约 0.9 秒的转圈（见下一条）。【已证实】日志里两次读到的文本，加上画面上的打字时间。
- N1、N2 从最后一条追问进 intro 之前，屏幕中央有一个小圆环转圈，约 0.9–1.0 秒（N1 79.56→≈80.5，N2 80.76→≈81.7），没有气泡。新内容滑入时它还在，重叠约 2 帧。N3 从 photos 进 intro 时没有转圈（空窗 120ms）。【已证实】`frames/N1/n1-spinner-25fps-79.48s.png`、`frames/N2/n2-interstitial-2.5fps-80.60s.png`。
- 气泡下方有一张约 145×145 的圆角插图：黄底，白鸽抱本子写字。它在加载完成的那一帧直接出现，没有淡入：N1 在入场后 3.0s，N2 在 1.6s，N3 在 0.4s。【已证实】出现是单帧跳变，三次时间不同。【推断】时间取决于图片加载。N2 有一帧只露出顶边一条黄线，像是渐进加载。`frames/N1/n1-interstitial-illustration-83.52s.png`
- 和普通页相比，intro 的离场比「点击 → 离场」的常规延迟早约 0.45s（三条都早约 0.45s，见 0.2 的 Δ 列：N1 −0.04 对 +0.4 档，N2 −4.66 对 −4.2 档，N3 −5.31 对 −4.87 档）。【已证实】三次都偏早。【推断】普通页点 Next 后要等一次保存（约半秒）才开始离场；intro 没有答案要存，所以立刻离场。没有网络抓包，不能证实。

### 2.6 N3 为什么多出一长串

N3 比 N1、N2 多了 photos、relationshipLooking、matchingStyle、dealbreakers、politics、politicsImportance、religion、religionImportance、firstMessage、datingHelp。顺序和进度见第 1 节。

进度条的分母能说明这些页是「根本不在步骤表里」，还是「在表里但被跳过」：

| 条 | 第二段格数（每格 px） | 第二段实际出现的页 | 第三段格数 | 第三段实际出现的页（到个人邮箱） |
|---|---|---|---|---|
| N1 / N2 | 13（9.0） | 12，最后一格跳过，原位置是转圈 | 7（16.7） | 6 |
| N3 | 15（7.8） | 15（最后一格是 photos） | 16（7.3） | 15 |
| S1 | 15（7.8） | 到学校邮箱是 13 | — | — |
| S2 | 14（8.36） | 到学校邮箱是 13 | — | — |
| S3 | 16（7.31） | 到学校邮箱是 15 | — | — |

【已证实】每页的填充值都等于 `格序 × 117 / 格数`，取整误差在 1px 以内。

由此能读出：

1. N1、N2 的第三段只有 7 格（intro、work、budget、edu、eduImp、email，加上邮箱之后的 1 格），N3 有 16 格。所以 lookingFor 到 datingHelp 这一串**不在 N1、N2 的步骤表里**，不是渲染时才藏的。【已证实】分母。
2. N1、N2 的第二段比实际页数多 1 格，那一格正好排在追问之后，也就是 N3 放 photos 的位置。N1、N2 走到那里时没有 photos 页，而是转圈 0.9 秒。【已证实】格数和转圈的位置。【推断】这一格是 photos：客户端检查过已有照片，然后跳过。但 N3 同样已有 2 张照片，照片页却照常显示，这和「有照片就跳过」矛盾。
3. S1 的第二段比 S2 多 1 格。两条到学校邮箱的页数一样，配置只差性别方向和爱好。【已证实】

几种假设：

- **H1 性别组合**：Nonbinary / Everyone 才问整套偏好题。能解释 N3。解释不了 S1 比 S2 多 1 格（S1 是 Man→Women）。
- **H2 恢复入口**：从验证码页恢复的那两条（S1、N3）步骤表更长；从学校邮箱恢复的四条（S2、S3、N1、N2）更短。六条都符合。【推断】最贴合，但 N3 同时换了性别，混在一起没拆开。
- **H3 服务端「已答」状态**：N3 的偏好题到页时全是草稿（Next 已可点）。这说明答案就在账号上，并没有因为「已答」而被跳过。H3 解释不了 N1、N2 的分母为什么本来就短。

要拆开这几种假设，下一轮可以做：(a) 从学校邮箱页恢复，再跑 N3 的配置；(b) 从验证码页恢复，再跑 N1 的配置。两个都要在 photos、datingHelp 之前停住。

## 3. 逐页原文

关键词指气泡里加粗、带字母波浪动画的词。emoji 在左，句子保持原有大小写。选项末尾的 `Other` 是带铅笔的输入胶囊，点开后占位就是 `Other`（10 号已记录）。

勾选标记：多选题选中后右侧有白色对勾圆，单选题没有。【已证实】截图。

- 多选：hobbies、各追问的风格题、expectedGender、ethnicity、expectedEthnicity、ideal match、relationshipLooking、religion、datingHelp。
- 单选：isStudent、gender、describe yourself、budget、educationLevel、educationImportance、matchingStyle、politics、politicsImportance、religionImportance。

### 3.1 公共页（六条都有）

| 页 | 气泡（关键词加粗） | 副文案 / 提示 | 选项或占位 |
|---|---|---|---|
| isStudent | are you currently a **student**? | `Private. Only used to match you`（带锁图标） | 🎓 yes, i'm a student / 💼 nope, not a student |
| name | 两只气泡：`Hi, I'm Ditto`、`what's your **name**?` | — | 输入条，草稿 `Alex` |
| gender | what's your **gender**? | — | Man / Woman / Nonbinary / Other |
| expectedGender | Who are you looking to **date**? | Private | Men / Women / Nonbinary / Everyone |
| birthday | when's your **birthday**? | 输入条上方：`Only your age is shown to others.` | 草稿 `01 / 15 / 2004` |
| hobbies | what are your **hobbies** & **interests**? | — | 👨‍🍳 Cooking / 🎵 Music / ✈️ Travel / 📚 Reading / 🏋️ Gym / 🎮 Gaming / 🎬 Movies/TV / 🎨 Art / `Type your hobby`（带铅笔） |
| ethnicity | What's your **ethnicity**? | Private | American Indian / Black/African Descent / East Asian / Hispanic/Latino / Middle Eastern / Pacific Islander / South Asian / South East Asian / White |
| expectedEthnicity | Any **ethnicity** preferences? | Private | No Preference + 上面 9 项 |
| expectedAgeRange | What's your preferred **age range**? | Private | 双滑杆，草稿 18 和 32，刻度为粉色竖线 |
| height | How **tall** are you? | — | 读数 `183 cm`，尺规 130…240，单位 `ft / in` / `cm` |
| heightRange | how **tall** should your match be? | Private | 草稿 `🤷 I don't care about height`（品红） |

### 3.2 八个爱好的追问（全部本轮实拍）

| 爱好 | 风格题气泡 | 选项（后面都还有一枚 Other） | 短文本气泡 | 占位 | 证据 |
|---|---|---|---|---|---|
| Cooking | what's your **cooking style**? | 🍝 Italian / 🍜 Asian / 🌮 Mexican / 🍲 Comfort food / 🧁 Baking / 🔥 Grilling / 🥗 Healthy bowls / 🥐 Brunch | what's your **signature dish**? | `the one u brag about` | S1/07、S3/07、`S3/08-a.png` |
| Music | what's your **music sound**? | 🎤 Pop / 🎧 Hip-hop / 🎸 Indie / 🎹 Electronic / 💞 R&B / 🤘 Rock / 🤠 Country / 🎷 Jazz / 🎻 Classical / 🩷 K-pop / 🪇 Latin | who u **streaming** rn? | 本轮都是草稿 `test`，占位没露出；09 记的是 `artist on repeat rn` | S1/09、S1/10 |
| Travel | what's your **travel style**? | 🎒 Backpacking / 🍾 Luxury / 🥘 Foodie tours / ⛰️ Adventure / 🏖️ Beach + sun / 🏙️ City-hopping / ✈️ Solo travel / 🚗 Road trips | what's your next **travel destination**? | 同上，09 记的是 `where u manifesting` | S1/11、S1/12 |
| Reading | what **genres** u reading rn? | 📖 Lit fic / 🐉 Fantasy / 🚀 Sci-fi / 🔍 Mystery / 💕 Romance / 🪞 Memoir / 🧠 Self-help / 🌹 Poetry / 📚 Classics / 😏 Smut idk | **last book** u finished? | `name + author` | `S2/07-a.png`、`S2/08-a.png` |
| Gym | what's your **training style**? | 🏋️ Lifting / 🏃 Running / 🧘 Yoga / 🩰 Pilates / 💪 Crossfit / 🥊 Boxing / 💃 Dance / 🚴 Cycling / 🧗 Climbing | what u **training for**? | `aesthetic, marathon, principle...` | `S2/09-a.png`、`S2/10-a.png` |
| Gaming | what **games** u into? | 🔫 FPS / ⚔️ RPG / ⚽ Sports games / 🎮 MOBA / 🧠 Strategy / 🎨 Indie / 🌱 Sims/cozy / 👊 Fighting / 🌐 MMO | what u **playing** rn? | `game on repeat rn` | S2/11、`S2/12-a.png` |
| Movies/TV | what kinda **content** u like? | 🎞️ Indie / 👻 Horror / 💞 Rom-com / 💥 Action / 🛸 Sci-fi / 📹 Documentary / 🎬 A24 stuff / 🌍 Foreign / 🎌 Anime / 📺 Reality TV | **last great watch**? | `name it` | S3/09、`S3/10-a.png` |
| Art | what **mediums** u work in? | ✏️ Drawing / 🎨 Painting / 💻 Digital / 🏺 Pottery / 🗿 Sculpture / ✂️ Collage / 🧵 Crafts / 🌈 Mixed media | what do u **make**? | `describe your work` | S3/11、`S3/12-a.png` |

关键词加粗读自截图。风格题都是多选：到页为空时 Next 是灰的，点一项后变白。短文本为空时 Send / Next 是灰的。【已证实】S2、S3 的 `-a` 和 `-b`。

### 3.3 Nonbinary / Everyone 专属两页

| 页 | 气泡 | 提示 | 选项 | 题型 |
|---|---|---|---|---|
| expectedPosition | your **ideal match**? | Private | 🔝 They're more of a top / 🔽 They're more of a bottom / 💪 They're more masc / 🌸 They're more femme / 🔄 They're flexible / 🤐 Prefer not to say | 多选（选中有对勾） |
| position | how would u describe **yourself**? | Private | 🔝 I'm more of a top / 🔽 I'm more of a bottom / 💪 I'm more masc / 🌸 I'm more femme / 🔄 I'm flexible / 🤐 Prefer not to say | 单选（无对勾） |

这两页的胶囊是固定宽度，长句会折行（`They're more of a` / `bottom`）。到页为空时 Next 是灰的。【已证实】`frames/S3/s3-ideal-match-77.60s.png`、`S3/16-b.png`、`S3/17-b.png`。

### 3.4 学生尾：学校邮箱（停点）

- 气泡：`what's your school **email**? 📧`
- 气泡下方有一条带下划线的文字按钮：`not a student? continue without a school email`
- 输入条上方：`we need a school email (.edu) to verify you're a student`
- 三条 S 的输入框都预填了账号的个人 gmail（不是 .edu），Send 是灰色禁用。没有点逃生按钮，没有点 Send。【已证实】`S1/18-a.png`

### 3.5 非学生尾（N1、N2 是短版，N3 是长版）

| 页 | 气泡 | 副文案 / 提示 | 选项或占位 | 出现在 |
|---|---|---|---|---|
| photos | `Let's add at least 2 photos 📷`、`Choose photos that show your vibe` | `Photos from different moments help us find better matches. You can swap them anytime.`；DOM 里另有 `Hold and drag a photo to reorder.` | 3×2 格：2 张已有（右上 ×）、1 个 + 格、3 个虚线空格 | N3 |
| preferencesIntro | `now let's talk about your preferences` | 下方黄底鸽子插图 | Next 可点 | N1 N2 N3 |
| relationshipLooking | What are you **looking for**? | Private | Life partner / Serious relationship / Casual dates / New friends / Not sure yet（多选） | N3 |
| matchingStyle | how do u want ditto to **match u** rn? | Private | ⚡️ More dates, faster / ⚖️ A steady mix / 🎯 Fewer, better matches / 💎 Wait for the one | N3 |
| occupation | what do you do for **work**? | — | 大文本框，草稿 `test role` | 全部 N |
| firstDateBudget | what's ur **budget** for a first date?（弯撇号 ’） | 第二只气泡 `per person, roughly`；Private | ☕ under $20 / 🥂 $20–50 / 🍝 $50–100 / ✨ $100+ / 🤷 no preference | 全部 N |
| dealbreakers | **dealbreakers**: what should Ditto avoid? | 第二只气泡 `include anything you care about: physical traits, behavior, personality, etc.`；Private | 大文本框，草稿 `test note` | N3 |
| educationLevel | what's the highest level of **education** you've completed? | Private | high school / college degree / graduate degree / prefer not to say | 全部 N |
| educationImportance | does your match's **education** have to be at least yours? | Private | doesn't matter / kinda matters / super important | 全部 N |
| politics | what's your **political vibe**? | Private | Very left / Left-leaning / Center / Right-leaning / Very right / Apolitical / Other | N3 |
| politicsImportance | does your match have to share your **politics**? | Private | doesn't matter / kinda matters / super important | N3 |
| religion | what's your **religion**? | Private | Atheist / Agnostic / Christian / Catholic / Jewish / Muslim / Hindu / Buddhist / Spiritual / Other（多选） | N3 |
| religionImportance | does your match have to share your **religion**?（`?` 折到第二行） | Private | doesn't matter / kinda matters / super important | N3 |
| firstMessage | `**first thing** your match should know?` 和第二只气泡 `this helps your match get to know you better` | 输入框上方带纸飞机图标：`your match will see this` | 大文本框，草稿 `a short sample` | N3 |
| datingHelp | if you had a **dating coach**, what would you want help with? | — | 👋 approaching people / 💬 keeping a convo going / 👀 telling if they’re into me / 💪 being more confident / 🧭 figuring out what i want / ☕ the date itself / 💘 just get me a date | N3 |
| 个人邮箱（停） | what's your **email**? 📧 | `don't lose access to your account, verify your email` | 预填了个人 gmail，Send 是白色可点。没点 | 全部 N |

相对 09 新补全的文案：

- religionImportance 的整句。
- firstMessage 的第二只气泡（09 只抄到 `your match will see this`）。
- datingHelp 的整句问句。
- expectedGender = Everyone 时，datingHelp 第一项是 `approaching people`。09 里只选 Women 时是 `approaching girls`。【已证实】

## 4. 动效参数（pass3 实测，对照 pass2）

除注明外，都是对 131 次翻页做 25 fps 全解码统计得出的（精度 40ms，1px）。

### 4.1 翻页

示意帧：`frames/S1/s1-name-to-gender-25fps-10.36s.png`、`frames/S1/s1-hobbies-exit-25fps-29.32s.png`、`frames/S1/s1-expEth-entry-25fps-61.56s.png`

1. **退场**：旧页整列（Ditto 署名、气泡、Private、选项）一起向上飞走并淡出。第 1 帧上移 24–45px，第 2 帧上移 93–137px 且明显变淡，第 3 帧完全消失。能看到的退场时长是 40–80ms（中位 40ms，最长 120ms），先慢后快（ease-in）。进度条那一行会被飞过的白气泡扫过一帧，读数会瞬间跳成 118/118/xx。【已证实】
2. **空窗**：旧内容消失到新内容第一次可见，80–160ms，中位 120ms。转圈那两次是 920 和 960ms。【已证实】
3. **入场**：新页整列从下方滑上来并淡入。第一可见帧还在终点下方约 370–430px（N3 datingHelp 那次是 428px），之后每 40ms 剩下约 55%：
    - 一条完整的偏移序列：426 → 228 → 145 → 90 → 43 → 25 → 12 → 7 → 4 → 2 → 1 → 0。
    - 没有回弹。等效时间常数约 70ms。
    - 从第一可见帧算起，到终点 ±2px 用 200–440ms（中位 280ms），完全落定用 320–400ms（中位 360ms）。
    - 透明度在前 3–4 帧（约 120–160ms）从淡到实。
    - 【已证实】曲线。【推断】是强 ease-out，或接近临界阻尼的弹簧。40ms 采样分不出是哪一种。
4. **选项不错峰**：选项、Private 提示、输入框跟着气泡同一个位移，没有逐条 stagger。列表底部有一道常驻的渐隐遮罩，所以最下面一两项永远偏淡，这不是在淡入。【已证实】`s1-expEth-entry`
5. **Next / Send**：离场那一帧随旧页一起消失，不滑动，在原地淡入并略放大。主要变化在 120ms 内，颜色到 300ms 稳定。【已证实】
6. **掉帧**：个别转场里同一帧重复 2–3 次（例如 N3 intro → relationshipLooking）。这是无头录制的性能问题，不是设计。上面的统计都含这些样本，所以最大值偏大。

### 4.2 气泡打字

示意帧：`frames/S1/s1-gender-bubble-typing-25fps-10.72s.png`、`frames/S1/s1-name-two-bubbles-25fps-6.56s.png`

1. 气泡先以空的小药丸出现（约 32px 宽，没有三个点），跟着整列一起滑入。
2. 第一个词在第一可见帧之后 80–200ms 出现（中位 160ms）。从旧页开始离场到新页第一个词，中位 320ms（280–400ms）。
3. **逐词**：词间隔平均 92ms，回归斜率 93ms/词。因为 40ms 量化，帧上看是 80 和 120 交替。新词先以约 30% 的淡灰出现，1–2 帧内变实。气泡宽度按词一次跳到位，没有逐帧伸展。整句时长约等于 93ms ×（词数 − 1），本轮是 160–880ms，中位 280ms。【已证实】
4. **关键词波浪**：加粗关键词先整词出现（淡灰 → 深色加粗，1 帧），然后字母从左到右依次放大再回落，像一道波。每个字母比前一个晚约 60ms，单个字母的鼓起持续约 120–160ms。6 个字母（gender）一共约 400–450ms。这期间后面的字（例如 `?`）照常继续出现。【已证实】
5. **第二只气泡**：name 页 `Hi, I'm Ditto` 打完后的下一帧，下面冒出第二只空药丸。它空着停约 200–240ms，再开始打 `what's your name?`。两只气泡最终都留着。【已证实】
6. 打完之后气泡没有持续的形变（和 09 的 `getAnimations()` 为空一致）。

### 4.3 选中态

示意帧：`frames/S2/s2-gender-switch-25fps-8.76s.png`、`frames/S2/s2-litfic-select-25fps-33.50s.png`

| 项 | 实测 |
|---|---|
| 底色 玻璃 → `#E40089` | 例：(41,77,131) → 70,96,152 → 95,83,151 → 140,54,145 → 199,15,138 → 222,2,137 → 228,0,137。可见过渡 160–200ms，和 CSS 0.15s 同一档。【已证实】 |
| 单选切换 | 旧项变暗、新项变红同时进行，中点同一帧（8.92s）。【已证实】 |
| **宽度** | 选中的胶囊向左变宽 52px（208 → 260，右边缘不动），取消选中的胶囊收回。用时约 280–320ms，S 形曲线（每 40ms 的进度：0.04、0.15、0.25、0.48、0.69、0.83、0.94、0.98、1）。比颜色慢一拍。【已证实】两次点击。 |
| 对勾 | 多选项选中后右侧白色对勾圆淡入，约 200ms，比底色略晚。【已证实】 |
| 悬停 | 点击前一两帧，玻璃底先变亮（41,77,131 → 57,95,153）。这是 Playwright 移动鼠标造成的悬停态，手机上没有。【已证实】 |

### 4.4 进度条

示意帧：`frames/S1/s1-progress-47to70-25fps-10.40s.png`

- 3 段，每段约 117px，各段独立填充。【已证实】
- 填充在新一页挂载的同一帧开始（相对旧内容清空，中位 0ms）。肉眼可见的增长持续 160–280ms（中位 200ms）。【已证实】
- 曲线拿 47→70 那次对照：起步后 60/100/140/180/220/260ms 时，填充到 0.17/0.43/0.78/0.87/0.96/1.0。这和 09 样式表的 `width 0.3s cubic-bezier(0.4,0,0.2,1)` 逐点吻合（同一时刻的理论值约 0.13/0.40/0.70/0.85/0.94/0.99）。最后约 40ms 不到 1px，所以看起来像 260ms。【已证实】
- 分段规则：
    - 第 1 段固定 5 格：isStudent 23、name 47、gender 70、expectedGender 93、birthday 117。
    - 第 2 段：学生从 hobbies 起算（第 1 格 = hobbies），非学生从 expectedAgeRange 起算，hobbies 是第 6 格。
    - 第 3 段（非学生）从 preferencesIntro 起算。
    - 每格宽度 = 117 / 该段总格数，总格数随路径变化（见 2.6）。
    - 【已证实】
- 「进入爱好」「进入追问」不会另开一段。追问只是第 2 段里的普通格子。【已证实】

### 4.5 和 pass2（11 号文档）的差异

| 项 | pass2 写法 | pass3 实测 | 原因 |
|---|---|---|---|
| 翻页 | 「旧内容在一帧内离开，160–240ms 后新气泡起笔，不是滑入」 | 旧页上飞并淡出（40–80ms），空 80–160ms，新页整列从下方约 400px 滑上并淡入，约 360ms 落定。第一个词在离场开始后约 320ms | pass2 只有 0.22 倍缩略图，只量气泡区的白像素，看不到位移 |
| 打字 | 「词块 80ms 为主，短句 240ms，长句 480–640ms」 | 93ms/词（80/120 交替），新词淡入。整句 160–880ms，中位 280ms | 一致，本轮样本多；新增「新词淡灰 → 实」 |
| 关键词 | 未提及 | 字母依次放大的波浪，约 60ms/字，约 400–450ms | 缩略图读不出 |
| 气泡初态 | 未提及 | 空药丸先出现，之后才开始出字；第二只气泡空等约 220ms | 新增 |
| 选中色 | 陡段 80–160ms | 可见过渡 160–200ms（含首尾） | 计量方式不同（pass2 只数纯品红像素） |
| 选中宽度 | 未提及 | 向左变宽 52px，约 300ms，S 形 | 新增 |
| 进度条 | 可分辨 120–200ms，样式表写 300ms | 1px 精度下 160–280ms，曲线吻合 300ms 标准曲线 | 精度从 4.6px 提到 1px |
| 选项进场 | 未测 | 不错峰，随整列一起移动；底部常驻渐隐 | 新增 |
| 过场页 | 未录到 | 不会自动前进；之前可能有约 0.9s 转圈；插图加载即出现 | 新增 |
| 画面 | 「满幅只有三帧」 | 全程满幅，只有截图时出现 2 倍放大伪影 | 录屏方式不同 |

## 5. 和 09 / 10 的其他差异

| 点 | 09 / 10 | pass3 |
|---|---|---|
| 追问顺序 | 等于勾选顺序 | **等于题库顺序**（S3、N3 反例） |
| 学生尾 | 09 没有学校邮箱，heightRange 之后直接是 photos；10 有学校邮箱 | 三条 S 都在 heightRange 之后进学校邮箱 |
| 非学生的位置 | 10 前一轮是在学校邮箱点逃生才进职场题 | isStudent 直接选 nope，题序整体重排（2.1），不再经过学校邮箱 |
| preferencesIntro | 09 没出现；10 前一轮出现过 | N 三条都有；只走前进路径，不会自动前进 |
| datingHelp | 09：最后一页，进度满格，有彩纸；点第一项就交卷 | N3：第 3 段 101/117，不是满格，**没有彩纸**（帧差和静态页一样）；草稿已选 `approaching people`，点 Next 进了个人邮箱 |
| dealbreakers 的位置 | 09：hearAbout 之后；10 前一轮：budget 之后 | N3：budget 之后、educationLevel 之前 |
| schoolYear / hearAbout / location | 09 学生路径有 | 六条都没出现 |

## 6. 安全记录：datingHelp

- N3 第 34 页是 datingHelp。到页时 `👋 approaching people` 已经是选中态（草稿），Next 可点。日志操作是 `prefilled`。【已证实】`N3/34-a.png`、`34-b.png`、日志 `next 34 prefilled`
- 脚本**没有点任何选项**，只点了 Next。下一页是个人邮箱 `what's your email? 📧`，脚本在那里 STOP。没有出现心形点阵，没有跳到 `/`。【已证实】`frames/N3/n3-datingHelp-to-email-top-25fps-158.12s.png`（已裁掉邮箱输入框）
- 09 里在 datingHelp 点第一个选项就直接交卷了，所以这一页本来就该避开点选项。本轮避开了。
- 跑完后主控用 `tools/peek-onboarding.mjs` 复核过：打开 `/` 会重定向到 `/onboarding` 的验证邮箱页，账号仍在入驻中，没有完成入驻。【已证实】主控复核，本文没有重跑。
- 这一页没被 STOP 拦下的原因：日志里的文本是 `dating\u00a0coach`（不断行空格）。现在 `rec-onboarding.mjs` 的 STOP 已经包含 `dating\s*coach|you want help with|just get me a date`，并且会先把 `\u00a0` 换成空格，但这个文件的修改时间（19:57:18）晚于 N3 日志（19:52:42）。【已证实】mtime。【推断】N3 跑的时候这几条规则还没加，是事后补的。
- 副作用：S1、N3 加载时都落在验证码页，文案是 `sending code…`。【推断】可能各触发了一封验证码邮件。

## 7. 未解决

1. N3 为什么有完整的偏好尾巴，以及 S1 为什么比 S2 多 1 格。H2「从验证码页恢复」和六条都吻合，但和性别组合混在一起。拆分实验见 2.6。
2. N1、N2 第二段末尾那一格：转圈 0.9 秒之后被跳过。具体判了什么？N3 有照片却照样显示 photos 页，和「有照片就跳过」矛盾。
3. 学生路径学校邮箱后面的 1–2 格是什么（photos？验证码？）。要越过学校邮箱才能看到，本轮按规定不碰。
4. 普通页「点 Next → 离场」比 intro 多约 0.45 秒。是不是在等保存接口？需要抓网络，离线做不了。
5. Nonbinary 和 Everyone 哪个触发了 ideal / describe？需要 Nonbinary → Women、Man → Everyone 各跑一次。
6. 入场曲线到底是 ease-out 还是弹簧。需要 60fps 以上的录屏，或者直接读源码。
7. preferencesIntro 插图的原图（静态还是动图）没有落盘。本轮 0.5 秒窗口里像素不变，看起来是静态的。
8. datingHelp 在 Men 时第一项的措辞（`approaching guys`？）。N2 没走到 datingHelp。
9. 新手引导里勾 4–5 个爱好时选哪 3 条追问。本轮只测了正好 3 个；09 的编辑流勾 5 个，选出的是 Gym / Gaming / Art，不是题库前三。

## 8. 本文引用的帧

| 文件 | 内容 |
|---|---|
| `frames/S1/s1-name-to-gender-25fps-10.36s.png` | 退场上飞 → 空窗 → 整列从下滑入，25 fps |
| `frames/S1/s1-hobbies-exit-25fps-29.32s.png` | 长列表的退场：整列上飞并淡出 |
| `frames/S1/s1-expEth-entry-25fps-61.56s.png` | 入场时选项不错峰，底部渐隐 |
| `frames/S1/s1-gender-bubble-typing-25fps-10.72s.png` | 逐词淡入、关键词字母波浪 |
| `frames/S1/s1-name-two-bubbles-25fps-6.56s.png` | 两只气泡依次出现，第二只先空着 |
| `frames/S1/s1-progress-47to70-25fps-10.40s.png` | 进度条一格的填充 |
| `frames/S1/s1-zoom-artifact-8.80s.png` | 截图造成的 2 倍放大伪影 |
| `frames/S2/s2-gender-switch-25fps-8.76s.png` | 单选切换：颜色交叉淡变 + 宽度变化 |
| `frames/S2/s2-litfic-select-25fps-33.50s.png` | 多选点上：悬停变亮 → 品红 → 对勾 |
| `frames/S3/s3-ideal-match-77.60s.png`、`s3-describe-yourself-82.60s.png` | Nonbinary / Everyone 专属两页 |
| `frames/N1/n1-spinner-25fps-79.48s.png` | 追问结束后约 0.9s 的转圈 |
| `frames/N1/n1-interstitial-2.5fps-79.40s.png`、`n1-interstitial-illustration-83.52s.png` | intro 全程和插图 |
| `frames/N2/n2-interstitial-2.5fps-80.60s.png` | N2 的转圈和 intro |
| `frames/N3/n3-photos-91.20s.png` | photos 页（N3 独有） |
| `frames/N3/n3-interstitial-to-lookingFor-25fps-97.52s.png` | intro → relationshipLooking，含掉帧 |
| `frames/N3/n3-datingHelp-155.60s.png` | datingHelp 草稿态（不是满格，无彩纸） |
| `frames/N3/n3-datingHelp-to-email-top-25fps-158.12s.png` | datingHelp → 个人邮箱（裁掉了输入框） |
