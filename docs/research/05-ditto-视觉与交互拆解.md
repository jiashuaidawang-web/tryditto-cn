# 05 Ditto 视觉与交互拆解

调研日期：2026-11-12。给中国版 H5 对照用。产品事实在 `01-ditto-产品拆解.md`。登录后页面在 `08-ditto-登录后页面.md`。入驻逐题原文在 `09-ditto-入驻全流程实录.md`。

证据标记与 01 相同：【已证实】来自当次截图、计算样式或控制台；【推断】由多条已证实事实拼出；【猜测】只标缺口。

图在 `assets/ditto/`（营销站与报道）和 `assets/ditto-onboarding/shots/`（入驻）。登录后截图在 `assets/ditto-app/logged-in/`。

## 1. 三套皮肤，不要合成一个色板

| 表面 | 底 | 强调色 | 标题 |
|---|---|---|---|
| 营销首页 tryditto.com | 首屏fallback `rgb(117,157,167)`，页脚 `#081721` | 滚动态 Join Now 字色 `#FF2CAB` | spencer，移动端 H1 68.25px，桌面 78.75px，色 `#F7F7F2` |
| 学院 SEO | 近黑底、白衬线 | 只在 wednesday 上用粉脚本 | 和首页不是同一套 |
| 产品 app.tryditto.com | `#233039`，上有颗粒和海平线光带 | 入驻选中胶囊 `#E40089` | 气泡用 Arial 16/500，登录后大标题用手写体 |

三套都在 2026-11-12 实机见过。【已证实】`assets/ditto/tokens-mobile.json` 是首页计算样式。入驻色值是 onboarding 页的 `getComputedStyle`。不要把 `#FF2CAB`、`#E40089`、`#F748B1` 写成同一个品牌粉。

## 2. 营销首页

视口测过 390×844 和 1440×900。截图：`site-mobile-hero.png`、`site-mobile-how-it-works.png`、`site-mobile-photos.png`、`site-mobile-comparison.png`、`site-mobile-safety.png`、`site-mobile-faq.png`、`site-mobile-faq-open.png`、`site-mobile-footer.png`，以及 `site-desktop-*`。

- 首屏标题视觉上是两行 “get a date next” 和 “wednesday”，next 与 wednesday 之间没有空格。每个字母是 inline-block。letter-spacing 为 normal。【已证实】
- 字母动画：`ah-title-wave` 1.8s ease-in-out infinite，错开约 60ms。入场 `ah-ch-in` 0.6s `cubic-bezier(0.2, 0.9, 0.3, 1.25)`，延迟从 600ms 起、每字加 28ms。【已证实】
- 首屏主按钮高 48px、最宽 352px，Helvetica 700 16px，字距 -0.32px，黑字在白 95% 底上。transform 0.42s `cubic-bezier(0.3, 1.7, 0.5, 1)`。【已证实】
- 顶栏高度：宽度小于 481px 时 72px，以上 96px。滚动到顶时 Log In 和 Join Now 都是白字、白 10% 底。滚下去之后 Join Now 字色变为 `#FF2CAB`，底接近白 95%。【已证实】
- 步骤标题 spencer 28px、`#F7F7F2`、行高 117%。桌面步骤说明 Arial 15px 白字居中。【已证实】
- FAQ 卡片最大宽 800px，圆角 24px，底 `#011E2C` 的 40%。展开用 grid-template-rows 0.3s ease-out。【已证实】`site-mobile-faq-open.png`。
- 手机样机里的假 iMessage：对方气泡 `rgb(233,233,235)`，自己的气泡从 `rgb(31,143,255)` 到 `rgb(10,124,255)`。样机是缩放的，圆角量出来约 12.6px。姓名在大约 12 秒内轮换 Emily、Alex、Raymond、Jason。【已证实】`site-mobile-hero-phone.png`。
- 页脚 `#081721`。外链是 instagram.com/ditto、tiktok.com/@ditto.date、x.com/Ditto_dates。【已证实】
- 页面上能找到的字体是 spencer 400 和 Arial/Helvetica。argentPixel 未真正用上。bitterSour 加载了但没对上元素。shadcn 那套奶油色变量在 CSS 里，不是首页正在用的颜色。【已证实】

## 3. 登录、注册、学院页

- `/login` 占位 “email or phone number”。`/signup` 占位 “school email”。Continue 高 48px，字色 `rgb(2,43,78)`，白 95% 底。输入框圆角 6px，白 8% 底。页面底 `#233039`。【已证实】`page-login.png`、`page-signup.png`、`page-signup-mobile.png`。
- 桌面登录是一张模糊风景上的手机形玻璃卡。390 宽则是满屏蓝。【已证实】
- 有 Continue with Google 和 Continue with Microsoft。入驻走查没有点它们。
- 学院 SEO 是另一套：近黑、白衬线，粉脚本只出现在 wednesday。UPenn 页当日写的是还没开通，去排队。Careers 没有职位列表。【已证实】`page-college-upenn.png`、`page-careers.png`。Colleges、Cities 只截了首屏。

## 4. 报道里的聊天和海报

- `press-imessage-onboarding-and-type-check.jpg` 是一张拼图。左半是真 iMessage 入驻：灰气泡、一问一句、用户蓝气泡、全小写。右半是 Safari 里的 “Is he your type?”，带照片、竖拇指，以及选中的粉色胶囊。图里有一个注册码，不要抄进文案或仓库说明。【已证实】画面结构；码本身不摘录。
- 海报：`press-match-poster-grid-12.jpg`、`press-match-poster-grid-wide.jpg`、`press-match-poster-isaiah-partial.jpg`。结构是散落重叠的照片，偏左一张白圆角卡，上面是 “Your date with” 加大名字，加粗一句合适的理由，两三小段，再加贴纸。浅蓝色是报道版面的底，不是海报自己的底。【已证实】
- Reddit 原图没下下来。IG、TikTok、X、小红书的原文件不在 `assets/ditto/`。

## 5. 登录后的产品壳

详见 08。这里只留和视觉系统有关的结论。

- 底与入驻相同：蓝颗粒渐变，中部一条浅色光带。大标题用手写体，例如 “Ditto is finding you a date!”。【已证实】08 的首页截图。
- 三个 Tab：Ditto、Where、You。编辑页是返回加居中标题，没有 Tab。
- 资料行是玻璃卡加一支铅笔。选中胶囊、地图等时圈用同一枚品红。
- 匹配结果不在这三个 Tab 里，08 记录的是 “Message Ditto” 去 iMessage。【已证实】08。

## 6. 入驻组件（本次量到的）

全题表和分支在 09。这里是可直接照着做的零件。视口 390×844，路由 `/onboarding`。

**气泡。** 白 90%，字色 `rgb(2,43,78)`，圆角 28px，内边距 12px 16px，Arial 16/500。关键词拆成逐字母 span，身高页实测字重 700。姓名页打字是按词块替换，间隔大约 80 到 100ms。打完后 `getAnimations()` 为空。【已证实】

**胶囊，未选。** 底 `rgba(8,23,33,0.25)`，白字 14/400，最小高 48px，圆角 24px，内边距 8px 16px。模糊 20px，亮度 0.75，饱和 1.4。内阴影 `inset 0 1px 3px rgba(255,255,255,0.12)`。颜色过渡 0.15s `cubic-bezier(0.4, 0, 0.2, 1)`。

**胶囊，选中。** `#E40089`，字重 700，模糊 10px，内阴影 `inset 0 0 8px rgba(255,255,255,0.15)`。关系题选中项右侧有白色对勾。【已证实】`19-flow-4.png`。

**底部输入条。** 深色圆角条。姓名占位 `your name`。空 Send 看 `aria-disabled`，底白 30%，图标 `rgba(14,28,38,0.45)`。有字后白底、深色图标。

**圆形前进。** 约 72×48，右下，aria-label `Next` 或 `Send`。启用后底白 95%，图标 `rgb(2,43,78)`。多数题是选中或填完才放开。雷区例外：空 textarea 时 Next 已经可点。末题例外见下。

**进度。** 3 段，每段约 117×4px，轨道白 20%，填充白 66%，阴影 `0 0 3px white`，宽度 0.3s。没有阶段文字。学生路径上，照片还在第二段（108/117），地点页起第三段从 0 开始，datingHelp 三格都满。【已证实】

**顶栏。** 返回、Ditto 头像加字标、锁。锁的 aria-label 是 `What the lock means`。说明层没打开。

**末题。** datingHelp 三格已满时，彩纸先落，气泡还在打 “dating coach”，选项已经可点，圆形 Next 仍是灰的。【已证实】`21-flow-3.png`。点第一项之后不需要 Next，地址变为 `/`。下一帧是心形点阵：蓝颗粒底，圆点排成心，中心几点发粉光。【已证实】`21-flow-4.png`。

**地图。** 地点题和登录后的 Where 是同一类零件：暗色 Google 地图、粉色等时圈、步行/公交/驾车、时长滑杆、搜索。入驻多一个 “为什么要问” 的说明层，文案见 09。【已证实】

**照片格。** 6 格，至少 2 张。已填格可删除。上传中有百分比和 “Hold and drag a photo to reorder.” 失败条是 “Some photos didn't upload / Please try again.” 控制台要求 2 张脸，纯色图 `passed=false` 后流程仍进了地图。【已证实】

## 7. 三列

### 可以照结构做

- 首页的竖向节奏：大标题、步骤、对照、安全、FAQ、页脚。字号和圆角用第 2 节的数。
- 入驻的一问一屏：左气泡、右胶囊或底栏输入、右下圆形箭头、顶上三段进度。
- 选中态只有一个强调色。未选是暗玻璃，选中是实色加对勾。
- 关键词加粗，其余句子保持 500。打字按词块，大约 80 到 100ms。
- 多选里的独占项：Everyone、No Preference 会清掉其他项。
- 末题用彩纸标出「这是最后一问」，点选项即交卷，然后用心形点阵过渡到等待首页。
- 地图用等时圈表达「我想在这一带约会」，而不是一个城市下拉框。

### 必须换成中国场景

- 英文手写标题换成中文展示字体。spencer 不能直接拿来排中文。
- 学校邮箱、`.edu`、毕业年份、Fraternity 一类校园词，换成国内学校和年级的说法。这次学生路径甚至没出现学校邮箱页，不能把 JSON 里的邮箱正则当成实机必经步骤。
- iMessage、蓝气泡、Message Ditto，换成短信或 H5 内的会话。匹配结果在美国产品里不在三个 Tab 中。
- 族裔、政治、宗教整组题。08 已决定中国版去掉政治和宗教。
- 体位题只在特定性别组合出现。中文产品要不要保留，是产品决定，不是这次视觉测量能定的。
- 等时圈若人在美国和加拿大以外，接口直接 400。中国版地图要换成本地服务。
- 照片的人脸门槛在控制台里是 2。纯色图没被界面挡住，不代表正式策略允许无脸照片。【已证实】当次行为；【猜测】正式环境是否另有拦截，没测。

### 不能抄

- 商标、Ditto 字标、鸽子头像、报道和海报里的真人照片。
- 营销文案、气泡原文、海报上的人名和理由句，逐字照搬。09 里的英文是测量记录，不是中文文案。
- TechCrunch 拼图里的注册码、任何邀请链接和推荐码。
- 把 `#FF2CAB` 和 `#E40089` 当成一个值。首页按钮粉和入驻选中粉是两次测量的两个数。

## 8. 缺口

- 入驻录像和按页 motion JSON 没有落盘。打字间隔只有姓名页一次。彩纸和心形点阵只有截图，没有时长和缓动曲线。
- 爱好八条里只展开了烹饪、音乐、旅行。职场路径、体位页、邮箱验证、好友邀请、iMessage 末页都没走到。见 09 第 9 节。
- Colleges、Cities 长页只截了开头。IG、TikTok、小红书原图不在资产目录里。
