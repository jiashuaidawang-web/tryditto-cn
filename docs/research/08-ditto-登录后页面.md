# 08 Ditto 登录后页面（已注册账号只读走查）

- 采集日期：2026-11-12。设备尺寸 390×844，截图在 `assets/ditto-app/logged-in/`。
- 用户手动登录的是一个已注册账号，走的是职场流程（「是否学生」答了 No）。
- 只打开查看，没有保存、提交或改动任何内容。
- Figma 文件：[Ditto 拆解 · 登录后页面](https://www.figma.com/design/7y9XrXJka0G2kWH8gZYDX4)。Starter 计划的 MCP 调用次数已用完，截图还没放进去。

## 视觉共性

- **背景**：蓝色渐变加颗粒噪点，底部偏深蓝，中部有一条像海平线的浅色光带。
- **卡片**：白色半透明毛玻璃，大圆角，细白描边。
- **字体**：页面大标题用手写体（"Ditto is finding you a date!"、"What Ditto knows about you"），正文用无衬线体。
- **颜色**：品红粉是唯一强调色，用在选中的胶囊、"Add block" 文字和地图上的等时圈。
- **图标**：白色线性图标。
- **导航**：底部 3 个 Tab，分别是 Ditto、Where、You。
- **复用**：单题编辑页和入驻问卷是同一套组件，左边 Ditto 气泡提问，右边胶囊选项或底部输入框作答。

## 页面清单

| # | 页面 | 路由 | 截图 |
|---|---|---|---|
| 01 | 首页 | `/` | 01-home.png |
| 02 | 约会范围 | `/where` | 02-where.png |
| 03 | 关于你 | `/you` | 03-you.png |
| 04 | 资料页（整页） | `/edit-profile` | 04-edit-profile.png |
| 05 | 单题编辑（多选） | `/onboarding/edit/intention` | 05-edit-looking-for.png |
| 06 | 单题编辑（文本） | `/onboarding/edit/name` | 06-edit-name.png |
| 07 | 照片编辑（在资料页内） | `/edit-profile`，点 "Edit photos" | 07-edit-photos.png |
| 08 | 深度信息 | `/deep-info` | 09-deep-info.png |
| 09 | 屏蔽名单 | `/blocklist` | 10-blocklist.png |
| 10 | 邀请（底部抽屉） | `/`，点 "Invite" | 11-invite.png |
| 11 | 菜单 | `/`，点 ☰ | 12-menu.png |
| 12 | 账号设置 | `/account` | 13-account.png |

## 逐页说明

**01 首页。** 鸽子衔着信封，下面是手写体标题 "Ditto is finding you a date!"，标题逐个字母出现。底部按钮 "Message Ditto" 跳转 iMessage。右上角是 Invite 和菜单按钮。首页没有匹配进度，也没有倒计时，等待期就是这一屏。

**02 约会范围。** 暗色 Google 地图上叠一个粉色等时圈，标签 "🔥 2493 nearby" 显示圈里有多少人。可以切换步行、公交、驾车，再用滑杆设最长出行时间（当前 2h）。改了之后 "Saved" 按钮才变成可点。中国版可以用高德地图的等时圈接口来做。

**03 关于你。** 标题 "What Ditto knows about you"，下面三张卡片：Profile（带照片缩略图）、Deep Info、Blocklist。

**04 资料页。**
- 分两组。"Tell Ditto about yourself" 包括姓名、生日、性别、族裔、身高、兴趣、是否学生、工作、学历、怎么知道我们的、对方第一眼该知道什么、雷区。"Tell Ditto your type" 包括想找什么、想约谁、年龄范围、身高要求、族裔偏好、匹配节奏、首约预算、学历要求。
- 每行显示问题、答案和一支铅笔，点进去是单题编辑页。
- 页面底部是 "Pics of your vibe" 照片区。

**05 单题编辑（多选）。**
- Ditto 气泡逐字打出问题，关键词加粗（"What are you **looking for**?"）。
- 右侧是品红粉胶囊，选中后显示白色对勾。
- 提示文字 "Private. Only used to match you" 和 "max 5. deselect one to swap"。
- 右下角 Done 按钮。

**06 单题编辑（文本）。** 上面是 Ditto 气泡，底部是深色胶囊输入框，右边一个白色圆形箭头按钮。

**07 照片编辑。** 不跳页，原地变成 3 列网格。每张照片右上角有 × 删除，另有 ＋ 添加和虚线空位，最多 6 张。

**08 深度信息。**
- 宗教、宗教重要度、政治立场、政治重要度。
- "The look you're into" 让用户上传明星或理想型照片，文案是 "Drop a celeb crush or someone whose vibe you love"。
- 中国版已决定删掉宗教和政治，只保留理想型照片。

**09 屏蔽名单。** 空态是盾牌图标加 "No one on your blocklist yet"，下面一个白底粉字按钮 "Add block"。

**10 邀请。**
- 底部抽屉，标题 "Invite friends to secure match"。说明是好友完成入驻后，你下周的匹配就锁定了。
- 下面是专属链接 `tryditto.com/j/home-referral/u_xxx`，以及 copy、share、messages、instagram 四个入口。
- 中国版换成复制链接、微信、小红书、生成海报。

**11 菜单。** 下拉毛玻璃菜单，有 Message Ditto、Save Ditto's Contact、Contact Us、Account Settings。其中 Save Ditto's Contact 是下载 vcf 名片，Contact Us 是 mailto 链接。

**12 账号设置。** Change Email、Change Phone Number、Log Out。另一组 "Leaving Ditto" 里有 Deactivate（隐藏资料，不删除）和 Delete（30 天后永久删除）。

## 对复刻的启发

1. **入驻问卷的样式已确认。** 是「对话气泡 + 胶囊选项」，不是表单页。可以直接按 05 和 06 复刻。
2. **登录后只有三个 Tab。** 页面很少，匹配、约会卡片和确认时间都放在 iMessage 里。中国版没有 iMessage，这部分要搬进 H5，靠短信提醒用户回来。
3. **「邀请好友锁定下周匹配」是增长钩子。** 建议保留。
4. **理想型照片可以作为 AI 匹配的输入**（Deep Info 里的 "The look you're into"）。
5. **这个账号是职场流程。** 学生流程的资料页字段会少几项（工作、学历、预算）。
