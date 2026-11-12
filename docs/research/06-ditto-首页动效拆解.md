# 06 Ditto 首页动效拆解

调研日 2026-11-12。对象是 https://tryditto.com/ 首页 `.austin-hero` 冷启动前 20 秒。只拆动效，不做原型，没有提交任何表单。

证据分三档。

- 【已证实】采样 JSON、关键帧或已下载的样式表里有对应数字。
- 【推断】由已证实的数字推出来，原文没有写死这个常数。
- 【猜测】没有测量，只标风险。

时钟。标题第一次真正露出来的时刻记为 T0。手机视口这次 T0 是 `performance.now()` 3410ms，字母 `g` 的 opacity 从 0 变成 12。下面的「相对 T0」都减去这个数。导航起点另记，用来和肉眼的 1.4 秒对照。桌面那次标题时段有 3318ms 的采样空洞，桌面不报标题时刻。

采样是页内 `setTimeout` 约 50ms 一拍，单次冷启动，缓存关闭，Headless Chrome 150，`deviceScaleFactor` 1。噪声底是 50ms。桌面最大空洞 4059ms 到 7377ms。手机最大空洞 421ms，落在 T0 之后第一拍。

素材在 `docs/research/assets/ditto-hero/`。脚本是 `docs/research/tools/capture-ditto-hero.mjs`。样式来自当时页面的 `12rhh0nu-2cgn.css`。插画和字体文件没有复制进原型目录。

## 概述

首页英雄区是一块绝对定位的舞台。天空、远山、校园、河岸叠在一起向左滚。标题按字母淡入，然后每个字母自己轻轻起伏。玻璃手机从下方滑上来，里面的 iMessage 气泡整条弹入。一只鸽子用 17 帧雪碧图扇翅膀，从左侧画外飞到标题上方，之后还在飞，末段会沉到风景后面。

用户肉眼看到的顺序是对的。标题先出现，手机接着滑上来，再过大约 1.5 秒鸽子进入画面。Headless 冷启动把「从打开页面算起」的绝对时间整体推后了大约 2 秒，相对顺序没有变。

## 关键概念

长度单位 `--u` 是 `clamp(0.45px, 100svh / 1600, 0.8px)`。本次手机视口 390×844，`100svh/1600 = 0.5275px`，落在钳位内。桌面 1440×900 是 `0.5625px`。

英雄区类名实测是 `austin-hero intro scenery-campus`。`intro` 打开入场动画。`scenery-campus` 把校园层高度改成 `501 * --u`，河岸高度改成 `347 * --u`。

技术栈。【已证实】页面脚本在 `/_next/static/chunks/`，其中有 `turbopack-*.js`，字体也从 `/_next/static/media/` 来，这是 Next.js。英雄区没有 Lottie、Rive、canvas 或视频请求。鸽子是 webp 雪碧，风景是 webp。标题、风景、手机位移、气泡入场都是 CSS 动画或 CSS transition。鸽子的 `--x`、`--y`、`--r`、`--s`、`--frame` 在样式表里只被读取，没有对应的 `@keyframes`，数值沿一条路径连续变化，所以是脚本在写自定义属性。【推断】

## 和肉眼时刻对照

用户看到标题大约在打开后 1.4 秒。本次手机冷启动 T0 是导航后 3410ms，比 1.4 秒晚 2010ms。【已证实】

同一趟导航的 `responseStart` 是 1932ms，`domContentLoaded` 是 2744ms，spencer 的 woff2 在 2530ms 返回。【已证实】英雄节点 2177ms 就在了，但当时标题还是 Times 32px、opacity 已经是 1。2614ms 字母 opacity 被打回 0，手机 `translateY` 变成 337.6px，这才是 intro 布局锁上的时刻。从这次锁上到 T0 只有 796ms。【已证实】

所以 1.4 秒对得上「字体已经在本地、JS 很快跑起来」的那次观看。Headless 冷字体把绝对时间推后，没有把标题动画本身拉长到 3.4 秒。【推断】

用户说聊天框滑上来之后再等 1.5 到 2 秒，鸽子从左边画外飞入。手机加上 `.up` 是 T0 之后 1757ms。鸽子盒子左缘进入视口是 T0 之后 3227ms。两者相差 1470ms。【已证实，单次，粒度 50ms】这和 1.5 到 2 秒是同一段间隔。鸽子在更早的 T0+421ms 已经在画外扇翅膀，人还看不见。

## 时间轴

下表的起点是相对 T0。手机视口 390×844。变换列是采样读到的计算样式，或样式表写明的关键帧。

| 事件 | 起点 | 时长 | 缓动 | 变换或透明度 | 证据 |
| --- | --- | --- | --- | --- | --- |
| 字母开始露出 | 0 | 每个字母 0.6s | `cubic-bezier(.2,.9,.3,1.25)` | `ah-ch-in` 从 opacity 0、`translateY(.35em)` 到终点 | 【已证实】样式表。T0 时 `g` 的 opacity 是 12 |
| 标题整句不透明 | +882ms | 入场一次 | 同上 | 24 个 `.ch` 的 opacity 都是 100 | 【已证实】采样。中间有一段数值停住，见下节 |
| 字母持续起伏 | 与入场叠在一起 | 1.8s，无限循环 | `ease-in-out` | `ah-title-wave`。25% 时 `translateY(-.071em) rotate(-1deg)`，50% 时 `translateY(.036em) rotate(.8deg)`，75% 时 `translateY(-.018em) rotate(-.4deg)` | 【已证实】样式表。幅度小于 0.1em，静帧里几乎看不出 |
| 远山入场 | 布局锁定后 | 1.3s，一次 | `cubic-bezier(.2,.8,.2,1)` | `ah-in-settle` 从 `translateY(3%)` 回到 0。同时 `ah-pan` 已经在滚 | 【已证实】样式表 |
| 校园入场 | 比远山晚 50ms | 1.2s，一次 | 同上 | `ah-in-rise` 从 opacity 0、`translateY(38%)` | 【已证实】样式表 |
| 河岸入场 | 比远山晚 0.25s | 1.2s，一次 | 同上 | `ah-in-rise-near` 从 opacity 0、`translateY(55%)` | 【已证实】样式表 |
| 手机开始上滑 | +1757ms | 1.8s | `cubic-bezier(.2,.9,.25,1.04)` | `.phone.up` 把 `translateY(640 * --u)` 收到 `none`。本次起点是 337.6px | 【已证实】640×0.5275=337.6，和采样一致 |
| 手机 translateY 第一次过 0 | +2921ms | 过渡仍继续到 1.8s | 同上 | 采样到 0.05px，下一拍到 -0.70px，曲线有轻微过冲 | 【已证实】过 0 的时刻不是时长。时长以样式表 1.8s 为准 |
| 鸽子在画外开始扇翅 | +421ms | 扇翅一圈约 0.7s，见下 | 逐帧切 `--frame` | `--x` 约 -350px，盒子左缘 -232px，视口宽 390，整只在画面左边外面 | 【已证实】 |
| 鸽子开始向右飞 | +2215ms | 飞入大约 1 秒 | 脚本写自定义属性，样式表没有这条关键帧 | `--x` 从 -335px 往右 | 【已证实】位移。【推断】驱动是脚本 |
| 鸽子进入视口 | +3227ms | 之后继续飞 | 同上 | 盒子左缘从 -4px 到 +9px。相对 `.up` 是 +1470ms | 【已证实】 |
| 鸽子停在标题上方 | 大约 +4.2s 到 +10s | 盘旋 | 同上 | `--x` 在 +8px 到 +38px，`--y` 在 -100px 到 -110px，`--s` 约 1.06 | 【已证实】采样，不是样式表里的循环关键帧 |
| 鸽子沉到风景后面 | +15.4s | 到 20s 仍在下方 | 同上 | +15.4s 时 `.behind` 为 1，`--y` 88px，scale 0.92。+16.5s 时 `--y` 162px | 【已证实】 |

`mobile-t3500.png` 能看见很淡的 `ge`。`mobile-t4000.png` 是 `get a date ne`，后面的字母更淡。`mobile-t5000.png` 整句已经清楚，手机顶边从底部探出来。`mobile-t6500.png` 手机到位，风景可见，鸽子还没进画。`mobile-t8000.png` 鸽子在标题上方，嘴里衔着信封。这些帧的文件名是导航后的视频时间，和 `performance.now()` 相差只有几毫秒。

## 标题逐字

标题是 `h1` 里的 24 个 `span.ch`，不是打字机。字符从第一帧就在 DOM 里，靠 opacity 和位移露出来。文案是两行。第 0 到 14 个是 `get a date next`，含三个空格。第 15 到 23 个是 `wednesday`。第二行的 `w` 横坐标回到行首。【已证实】结束时的 `getBoundingClientRect`。

每个 `.ch` 同时跑两条动画。

- `ah-ch-in`，0.6s，一次，`animation-fill-mode: backwards`。0% 是 opacity 0、向下 0.35em。缓动 `cubic-bezier(.2,.9,.3,1.25)`，终点会轻微过冲。
- `ah-title-wave`，1.8s，`ease-in-out`，无限。变换原点是 `50% 70%`。

样式表没有 `nth-child` 延迟。逐字是每个节点自己的 `animation-delay`。【推断】依据是 T0+473ms 那一拍的 opacity，从左到右递减，不是一起变。

那一拍的 24 个 opacity 是 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 99, 94, 88, 79, 68, 54, 37, 18, 0, 0, 0, 0, 0, 0。第 10 字到第 17 字从 99 掉到 18。单字淡入是 0.6s。按这条曲线的形状，相邻字母的延迟大约 25ms 到 35ms。【推断】采样在 T0 后空了 421ms，中间过程没有拍到，不能把 28ms 写成已证实常数。

T0+524ms 到 T0+831ms，尾部 opacity 停在 22, 1, 0, 0, 0, 0 不动，然后 T0+882ms 全部变成 100。【已证实】这段停顿不像 0.6s 的贝塞尔还在走。更像主线程或合成层卡了一下。【推断】重建时按样式表的 0.6s 和大约 30ms 的字间距做，不要把这 300ms 停顿做进去。

字号。【已证实】`font-size: var(--title-fs)`，`--title-fs` 是 `clamp(52px, min(140 * --u, 17.5vw), 112px)`。手机 17.5vw 是 68.25px，计算样式就是 68.25px。桌面 `140 * 0.5625 = 78.75px`，计算样式就是 78.75px。`line-height: .94`。颜色 `#fff`。阴影两层，`#0e28604d` 和 `#0e286033`。`white-space: nowrap` 作用在每一行上，两行是两个行盒。

## 聊天框

手机默认 `transform: translateY(calc(640 * --u))`。`.up` 把它改成 `none`。过渡是 `transform 1.8s cubic-bezier(.2,.9,.25,1.04)`。【已证实】

手机宽 `min(520 * --u, 86vw)`，高 `1080 * --u`。圆角 `76 * --u`。底边用 mask 从 50% 处渐隐到 80%，所以下半截融进风景。背景是白色半透明渐变，`backdrop-filter: blur(10px) saturate(1.4)`，描边 `#ffffff8c`。

`.phone-slot` 另有一条 opacity 动画，0.3s `ease-out`，延迟 1s，`ah-in-fade`。采样开始时 slot 的 opacity 已经是 100，这条淡入发生在我们能读到手机之前，或者被下面的位移盖住了。【推断】人看见的「滑上来」是 `.up` 的 translate，不是这条 opacity。

气泡不是打字机。一次插入整句，类名变化时整段文字一起出现。灰气泡 `ah-msg-in`，0.34s，`cubic-bezier(.22,1,.36,1)`，从 opacity 0、`scale(.88)` 弹出，原点在左下。蓝气泡 `ah-msg-pop`，0.42s，同一条贝塞尔，多一段 `translateY(34 * --u)`，原点在右下。灰底 `#e9e9eb`。蓝底是 `#1f8fff` 到 `#0a7cff`。正在输入是三个圆点，`ah-dot` 1.2s `ease-in-out` 无限，第二、三个点延迟 0.2s 和 0.4s。

下面是手机视口里线程 DOM 第一次变成该句的时刻，相对 T0。时刻是节点出现，不是用户看见的时刻。手机在 +1757ms 才开始上滑，更早插入的气泡还在屏幕下面。

| 相对 T0 | 新出现的内容 |
| --- | --- |
| +421ms | `your match this week`，带尾巴类名，动画刚开始 |
| +4351ms | 头像图，以及「正在输入」 |
| +5326ms | `Jason · home cooking, long hikes, building things` |
| +7129ms | 蓝色 `omg yes` |
| +7741ms | `Delivered` |
| +8963ms | 再次「正在输入」 |
| +9630ms | `Read 6:46 PM` |
| +10450ms | `Friday 8pm, dinner and a deep talk? I'll set it up with Jason` |
| +12291ms | 蓝色 `yes!!` |
| +12913ms | 再次 `Delivered` |
| +14960ms | 再次 `Read` |

这些句子是对方产品的演示文案，重建时换成自己的句子，只保留弹出节奏。【已证实】采样里的 `sig` 字段。

## 鸽子

鸽子不是 SVG，也不是 Lottie。节点 `.dove` 用一张横条雪碧做背景。

- 文件路径是 `/images/austin/dove-flap-2.webp`。另有阴影 `/images/austin/dove-shadow.webp`。
- 样式表写明 17 帧，单帧 358×364。
- 显示缩放 `--dove-px: calc(.82 * var(--u))`。手机上单帧宽 `358 * 0.82 * 0.5275 = 154.85px`。采样的 `background-position-x` 步进是 -154.853px。【已证实】
- 帧索引是自定义属性 `--frame`。`background-position-x` 等于 `--frame * -1 * 358 * --dove-px`。
- 轨道 `.dove-orbit` 以舞台中心为原点，`translate(-50%, -50%)` 再叠 `--x`、`--y`、`--r`、`--s`。`.behind` 把 `z-index` 从 3 改成 1，飞到标题和手机后面。

扇翅周期。【已证实】稳定扇翅时，`--frame` 回到 0 的间隔有 663ms、716ms、753ms、769ms 几拍。17 帧走完大约 0.7s，一帧大约 41ms。【推断】间隔不完全相等，采样会漏帧，不要把 41ms 当成样式表里的常数。样式表没有翼拍的 `@keyframes`。

信封没有单独的 DOM。嘴里的信封画在同一张雪碧里。【已证实】选择器只碰到 `.dove`，`mobile-t8000.png` 里信封和鸽子是同一只绘画。

飞行路径不是 CSS 关键帧。下面是手机视口采样，相对 T0。`--x` `--y` 是相对舞台中心的平移。重建时用这些点做关键帧，中间用 `ease-in-out` 插值。这是测量折线，不是他们脚本里的原始曲线。【推断】

| 相对 T0 | `--x` | `--y` | 旋转 | 缩放 | 盒子左上角 | 说明 |
| --- | --- | --- | --- | --- | --- | --- |
| +421ms | -350px | -193px | 0deg | 1.00 | -232, -8 | 画外，开始扇翅 |
| +2215ms | -335px | -190px | 0.7deg | 1.00 | -219, -6 | 开始向右 |
| +2628ms | -261px | -173px | 2.5deg | 1.00 | -147, 8 | 仍在画外 |
| +3024ms | -158px | -148px | 3.3deg | 1.00 | -45, 32 | 即将入画 |
| +3227ms | -103px | -135px | 3.2deg | 1.02 | 9, 43 | 左缘进视口 |
| +3432ms | -54px | -124px | 2.8deg | 1.04 | 56, 53 | 标题左上方 |
| +3842ms | +4px | -110px | 1.4deg | 1.06 | 119, 69 | 接近停留点 |
| +4249ms | +11px | -108px | 0.2deg | 1.06 | 124, 72 | 停在标题上方 |
| +6668ms | +38px | -101px | 0deg | 1.06 | 151, 79 | 在停留区内慢慢漂 |
| +10143ms | +8px | -109px | -0.4deg | 1.06 | 120, 71 | 仍在标题附近 |
| +13121ms | -16px | -138px | -7.0deg | 1.02 | 91, 36 | 开始下沉 |
| +15422ms | +9px | +88px | 11.7deg | 0.92 | 119, 266 | `.behind` 为 1 |
| +16490ms | +21px | +162px | 0.1deg | 1.00 | 138, 347 | 仍在飞，位置更低 |

20 秒结束时它还在动，没有停在某一帧上。【已证实】

## 三层视差

滚动是 `@keyframes ah-pan { to { transform: translate(-25%) } }`，`linear`，`infinite`。每层是一排 4 张 `.tile`，相邻 tile `margin-right: -1px` 藏缝。-25% 正好是一张 tile 的宽度，所以循环无跳变。【已证实】

时长公式也在样式表里，和计算样式对得上。

- 远山 `.mountains`，图 `/images/austin/sky.webp`，原图 1536×1637。时长 `300s * 1.6 * (1536/1784) = 413.274s`。
- 校园 `.campus`，图 `/images/home/painted-campus-2.webp`，原图 1920×501。时长 `64s * 1.6 * (1920/2171) = 90.561s`。
- 河岸 `.river`，图 `/images/home/painted-river-soft.webp`，原图 1920×347。时长 `36s * 1.6 * (1920/2171) = 50.941s`。

`--pace` 是 1.6。三个 `k` 是原图宽除以一个更大的参考宽。z-index 远山 0，校园 3，河岸 5。标题在 2 和 7 一带，鸽子默认在 3，`.behind` 时降到 1。

`playbackRate` 在样式表里是 1。采样里它从 1 漂到大约 1.16 到 1.24。【已证实】谁在改速率，这次没有抓到脚本。【猜测】可能是脚本在追帧。下面的像素每秒按速率 1 算，这是样式表的合同。屏幕上的实际速度要再乘当时的 `playbackRate`。

速度等于一张 tile 的显示宽度除以时长。手机 tile 宽取采样的图片布局宽，远山 810px，校园和河岸 1013px。桌面是 864px 和 1080px。时长两边相同，所以像素速度随宽度变。

| 层 | 手机 px/s | 桌面 px/s | 循环 |
| --- | --- | --- | --- |
| 远山 | 1.96 | 2.09 | 4 张 tile，每圈移一张，413.274s |
| 校园 | 11.19 | 11.93 | 同样 4 张，90.561s |
| 河岸 | 19.89 | 21.20 | 同样 4 张，50.941s |

近处大约是远处的 10 倍。校园大约是远处的 5.7 倍。方向一直向左。

入场和滚动是同一条 `animation` 上的两条名字。滚动无限，入场一次。入场缓动是 `cubic-bezier(.2,.8,.2,1)`。

天空底色 `--sky-top` 是 `#557c86`。手机宽度下，背景是这个颜色，底部 40px 换成 `#50655d`。阳光是两层 `conic-gradient` 光线，颜色 `rgb(255, 246, 224)` 和 `rgb(255, 236, 206)`，`mix-blend-mode: screen`，再加一层径向高光。光线自己有 1.6s `ease-out` 的淡入。

## prefers-reduced-motion

样式表在 `reduce` 下做了这些事。【已证实】

- `.strip`、`h1 .ch`、光线伪元素 `animation-play-state: paused`。
- `.phone` 的 `transition` 设成 `none`。
- `.msg`、`.receipt`、输入圆点 `animation: none`。
- `.glint` 不显示。

桌面 1440×900、`reducedMotion: 'reduce'` 的 6 秒采样对得上。

- 字母 opacity 没有从 0 拉开，一直是 100。逐字和波浪都停着。
- 远山、校园、河岸的 `ah-pan` 是 `paused`，`currentTime` 停在 0。入场动画也在这条暂停里，0% 关键帧把校园和河岸留在 opacity 0。【推断】所以减动效时中景和近景可以一直不出现。`reduced-desktop-t4500.png` 仍是一片平的灰蓝，没有校园。
- 手机在 3690ms 直接变成 `.up`，`transform` 是 `none`，中间没有 337px 那一串插值。
- 鸽子的 `--x` 没有被写上。它留在默认位置，也就是舞台中心再向上 `270 * --u`。不扇翅，`--frame` 空着。静帧里它已经在标题上方，衔着信封。
- 6 秒内气泡一次插完，没有逐条等待。采样在 5420ms 看到整段对话已经在 DOM 里。

减动效是「停在可读的终态」，不是把同一条时间轴放慢。

## 字体

两套都是 `font-display: swap` 的 woff2，字重 400。【已证实】`document.fonts` 里状态是 loaded。

- `spencer` 用于英雄区 `h1`，回退链是 `"spencer Fallback", Georgia, serif`。Fallback 用 `local(Arial)`，`size-adjust: 70.67%`，`ascent-override: 117.74%`，`descent-override: 35.31%`。文件名 `spencer_regular_webfont-s.p.3a5g2h7kjbggl.woff2`。
- `bitterSour` 用于 `.at-school`，字号是 `title-fs * .6 + 4px`，回退先到 spencer 再 Georgia。Fallback 也是 `local(Arial)`，`size-adjust: 98.99%`。文件名 `BitterSour-s.p.2_1e9-4yj5hox.woff2`。

`.at-school` 的入场是 0.6s、延迟 1.1s 的 `ah-ch-in`。这次采样没有记录这个节点的文案，静帧描述里标题下方也没有第二行小字。重建小字时用自己的句子。【已证实】规则在样式表里。【缺口】小字的可见文案这次没记下来。

聊天正文不是这两套字体。它用 `-apple-system, "SF Pro Text", system-ui, sans-serif`。

不要把这两个 woff2 放进我们的站点。spencer 的观感是高对比的展示衬线，笔画反差大，白色，字距正常，行高 0.94，带两层偏蓝的投影。bitterSour 用在较小的一行上，名字和 Fallback 的尺寸修正说明它比 Arial 更窄、更接近正文衬线。替换时找可授权的展示衬线加一款稍窄的衬线，用同样的 `size-adjust` 思路做 fallback，避免 swap 时标题跳动。

## 重画要点

这些文件只作内部对照，不进原型目录。

鸽子。水粉或色粉的白鸽，暖灰阴影，翅膀分开画在 17 帧横条里，单帧比例 358 比 364。信封是浅色矩形，画在喙上，不要单独做 DOM。显示宽度大约是视口短边的五分之二量级。手机上大约 155×157。飞行时整体缩放只在 0.92 到 1.06 之间，不要做大幅度扑近。

风景。三张横幅，接缝要能左右循环。远山是蓝灰山体加云，原图偏高，高度按 `1637 * --u` 铺满中上景。校园是秋色建筑和树，高度 `501 * --u`。河岸更矮，高度 `347 * --u`，颜色更暗。天空顶色 `#557c86`。光线从左上角散开，颜色偏象牙，`screen` 混合。

手机。白玻璃，上圆角大，下缘渐隐。头像是一只小鸽子，路径 `/images/austin/ditto-avatar.webp`，也要重画。气泡灰 `#e9e9eb`，蓝 `#0a7cff` 一带。

## 悬停、滚动、缩放

这次采集没有做悬停、滚动和窗口缩放。【缺口】

样式表里能确定的只有底部按钮。`.austin-hero-cta` 悬停 `scale(1.02)`，过渡 0.42s，缓动 `cubic-bezier(.3,1.7,.5,1)`。按下 `scale(.96)`，90ms。按钮入场是 0.7s、延迟 2s 的上移 18px。这是 CSS，本次没有去点它。【已证实】规则存在。【缺口】没有点击验证。

没有看到英雄区监听滚动来改视差的规则。视差是无限 CSS 动画。窗口尺寸会改变 `--u` 和 `17.5vw`，从而改变字号、鸽子尺寸和每层的像素速度。时长公式不随视口变，所以更宽的画面每秒滚过更多像素。这一点由手机和桌面的 tile 宽度差推出来。【推断】

## 重建

用 CSS 做标题、风景、手机和气泡。用一段很小的脚本写鸽子的自定义属性。不要引入 Lottie 或视频。

单位和速度照抄公式，插画换成自己的。

```css
.hero {
  --u: clamp(0.45px, calc(100svh / 1600), 0.8px);
  --pace: 1.6;
  --far-k: calc(1536 / 1784);
  --mid-k: calc(1920 / 2171);
  --near-k: calc(1920 / 2171);
  --sky: #557c86;
  background: var(--sky);
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.strip {
  display: flex;
  width: max-content;
  animation: pan linear infinite;
}
.mountains { animation-duration: calc(300s * var(--pace) * var(--far-k)); }
.campus    { animation-duration: calc(64s * var(--pace) * var(--mid-k)); }
.river     { animation-duration: calc(36s * var(--pace) * var(--near-k)); }

@keyframes pan { to { transform: translate(-25%); } }

.hero.intro .mountains {
  animation-name: pan, settle;
  animation-duration: calc(300s * var(--pace) * var(--far-k)), 1.3s;
  animation-timing-function: linear, cubic-bezier(.2, .8, .2, 1);
}
.hero.intro .campus {
  animation-name: pan, rise;
  animation-duration: calc(64s * var(--pace) * var(--mid-k)), 1.2s;
  animation-delay: 0s, 50ms;
  animation-timing-function: linear, cubic-bezier(.2, .8, .2, 1);
}
.hero.intro .river {
  animation-name: pan, rise-near;
  animation-duration: calc(36s * var(--pace) * var(--near-k)), 1.2s;
  animation-delay: 0s, 0.25s;
  animation-timing-function: linear, cubic-bezier(.2, .8, .2, 1);
}

@keyframes settle    { from { translate: 0 3%; } }
@keyframes rise      { from { opacity: 0; translate: 0 38%; } }
@keyframes rise-near { from { opacity: 0; translate: 0 55%; } }

.ch {
  display: inline-block;
  transform-origin: 50% 70%;
  animation:
    wave 1.8s ease-in-out infinite,
    letter 0.6s cubic-bezier(.2, .9, .3, 1.25) backwards;
}
@keyframes letter { from { opacity: 0; translate: 0 0.35em; } }
@keyframes wave {
  0%, 100% { transform: none; }
  25%  { transform: translateY(-0.071em) rotate(-1deg); }
  50%  { transform: translateY(0.036em) rotate(0.8deg); }
  75%  { transform: translateY(-0.018em) rotate(-0.4deg); }
}

.phone {
  transform: translateY(calc(640 * var(--u)));
  transition: transform 1.8s cubic-bezier(.2, .9, .25, 1.04);
}
.phone.up { transform: none; }

.msg.in  { animation: msg-in 0.34s cubic-bezier(.22, 1, .36, 1) both; }
.msg.out { animation: msg-pop 0.42s cubic-bezier(.22, 1, .36, 1) both; }
@keyframes msg-in  { from { opacity: 0; transform: scale(.88); } }
@keyframes msg-pop { from { opacity: 0; transform: translateY(calc(34 * var(--u))) scale(.92); } }

.dove-orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  transform:
    translate(-50%, -50%)
    translate(var(--x, 0px), var(--y, calc(-270 * var(--u))))
    rotate(var(--r, 0deg))
    scale(var(--s, 1));
}
.dove {
  aspect-ratio: 358 / 364;
  width: calc(358 * 0.82 * var(--u));
  background: var(--dove-sheet) 0 0 / calc(17 * 100%) 100% no-repeat;
  background-position-x: calc(var(--frame, 0) * -100%);
}

@media (prefers-reduced-motion: reduce) {
  .strip, .ch { animation-play-state: paused; }
  .phone { transition: none; }
  .msg { animation: none; }
}
```

逐字延迟不写进 CSS。脚本给每个 `.ch` 设 `animationDelay = index * 30` 毫秒。30ms 是推断，做出后用一帧对照 `mobile-t4000.png` 的疏密，再在 25ms 到 35ms 里改。

脚本再做两件事。

1. 布局锁定之后大约 2.55s 给手机加 `.up`。这次测量里，布局锁定在 T0 前 796ms，`.up` 在 T0 后 1757ms，合起来是 2553ms。字体缓存好时，用户会在打开后大约 1.4 秒看到标题，再过大约 1.8 秒看到手机开始动。
2. 用 `requestAnimationFrame` 写鸽子的 `--x`、`--y`、`--r`、`--s`、`--frame`。翼拍用 17 帧、每帧 41ms 循环。位移用上一节的折线，时间相对标题首字。飞入视口安排在手机开始上滑之后 1.47s。20 秒内不要让它停住。

气泡用定时器插入整句，间隔照上一张表，从手机开始上滑算起再整体平移。第一句可以在手机还在下面时就插入。

减动效时不要跑折线。鸽子放在默认的 `--y: -270 * --u`，手机直接加 `.up`。原站会把入场动画停在 opacity 0，校园可以一直不出现。我们的页面应该让风景停在已经升起的位置。

## 文件在哪

- 采样脚本 `docs/research/tools/capture-ditto-hero.mjs`
- 手机、桌面、减动效三份 JSON，同目录 `assets/ditto-hero/*-samples.json`
- 视频 `mobile.webm`、`desktop.webm`、`reduced-desktop.webm`
- 对照帧 `mobile-t3500.png`、`mobile-t4000.png`、`mobile-t5000.png`、`mobile-t6500.png`、`mobile-t8000.png`、`reduced-desktop-t4500.png`
- 样式表是页面上的 `/_next/static/chunks/12rhh0nu-2cgn.css`，构建号会变，以类名 `.austin-hero` 为准

## 坑

绝对时间不要拿去对用户的 1.4 秒。对 T0。桌面 JSON 在标题入场那几秒没有样本。`playbackRate` 不是 1，像素速度以样式表时长为准，再乘实测速率才是那一帧的屏幕速度。逐字延迟不在 CSS 里。信封不在 DOM 里。减动效会把入场动画停在 0%，风景可能不出现。

throughput checkpoint: n/a, read-only investigation
