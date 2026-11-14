import { t, type T } from '../i18n/lang';

/** Marketing-site copy. `en` is verbatim tryditto.com (2026-11-12 screenshots); `zh` is 见一面's own copy. */
export const BRAND = t('Ditto', '见一面');

export const HEADER = {
  ghostingMobile: t('Ditto ghosting?', '有疑问？'),
  fixIt: t('Fix it', '看常见问题'),
  ghostingDesktop: t('Ditto not replying?', '有疑问？'),
  newNumber: t('Get a new number', '看看常见问题'),
  logIn: t('Log In', '登录'),
  joinNow: t('Join Now', '立即加入'),
  continue: t('Continue', '继续'),
};

/** Hero title, one entry per visual line (letters are animated one by one). */
export const HERO_TITLE: { en: string[]; zh: string[] } = {
  en: ['get a date next', 'wednesday'],
  zh: ['下周三，', '去见一面'],
};

export const HERO = {
  cta: t('Join Now', '立即加入'),
  continue: t('Continue', '继续'),
  ditto: t('Ditto', '见一面'),
  stamp: t('iMessage', '短信'),
  stampTime: t('Today 6:45 PM', '今天 18:45'),
  firstMsg: t('your match this week 🕊️', '本周为你匹配的人 🕊️'),
  delivered: t('Delivered', '已送达'),
  read: t('Read 6:46 PM', '已读 18:46'),
  posterKicker: t('Your date with', '你的约会对象'),
};

export interface HeroMatch {
  name: T;
  intro: T;
  reply: T;
  plan: T;
  yes: T;
  /** Match card image id: public/landing/cards/{card}-{lang}.jpg (scripts/landing/build-cards.mjs). */
  card: string;
}

/** Rotating matches in the hero phone (Ditto cycles Jason / Alex / Emily). */
export const HERO_MATCHES: HeroMatch[] = [
  {
    name: t('Jason', '子涵'),
    intro: t('Jason · home cooking, long hikes and an orange cat 🐱', '子涵 · 爱做饭、爱徒步，还养了一只橘猫 🐱'),
    reply: t('omg yes', '好耶'),
    plan: t("Friday 8pm, dinner and a deep talk? I'll set it up with Jason", '周五晚 8 点，吃个饭好好聊聊？我来帮你和子涵约好'),
    yes: t('yes!!', '约！！'),
    card: 'zihan',
  },
  {
    name: t('Alex', '嘉怡'),
    intro: t('Alex · EDM nights, polaroids and a corgi 🐶', '嘉怡 · 电音现场、拍立得和一只柯基 🐶'),
    reply: t('wait I love that', '等等我好喜欢'),
    plan: t("Saturday, a late-night sundae run? I'll set it up with Alex", '周六晚上，去吃一份超大号冰淇淋？我来帮你和嘉怡约好'),
    yes: t('yes!!', '成交！！'),
    card: 'jiayi',
  },
  {
    name: t('Emily', '一凡'),
    intro: t('Emily · guitar, a skateboard and a sketchbook 🎸', '一凡 · 吉他、滑板和一本速写本 🎸'),
    reply: t("ok I'm in", '我可以'),
    plan: t("Saturday 3pm, coffee and a walk by the lake? I'll set it up with Emily", '周六下午 3 点，喝杯咖啡再去湖边走走？我来帮你和一凡约好'),
    yes: t("let's go", '冲！'),
    card: 'yifan',
  },
];

export const CHIPS = {
  how: [t('How It ', '怎么'), t('works', '玩')] as const,
  real: [t('Real', '真实'), t(' Dates Delivered', '约会 已送达')] as const,
  matchmaker: [t('Your ', '你的'), t('Personalized', '专属'), t('Matchmaker', '红娘')] as const,
  great: t('Unforgettable Great Times', '难忘的美好时光'),
  tired: t('Tired of swiping?', '刷累了？'),
  forYou: t('Ditto is for you', '试试见一面'),
  safe: [t('Verified. Private. ', '认证。私密。'), t('Safe.', '安全。')] as const,
  faq: t('FAQ', '常见问题'),
};

export const STEPS: { title: T; desc: T; img: string; w: number; color: string }[] = [
  {
    title: t('Tell Ditto Your Type', '告诉见一面你的理想型'),
    desc: t('Submit your preferences by Tuesday 11:59 PM.', '每周二 23:59 前提交你的偏好。'),
    img: '/landing/how-1.webp',
    w: 163,
    color: '#f5b400',
  },
  {
    title: t('The Wednesday Drop', '周三匹配日'),
    desc: t(
      'Check your iMessage at 7pm. We will send you one personalized match and curate your date for you!',
      '周三晚 7 点查收短信。我们只为你推荐一位专属匹配，并帮你安排好这次约会！',
    ),
    img: '/landing/how-2.webp',
    w: 212,
    color: '#ff2cab',
  },
  {
    title: t('Schedule the Date', '约个时间'),
    desc: t('Find a time that works for both of you to meet up.', '找一个你们俩都方便的时间见面。'),
    img: '/landing/how-3.webp',
    w: 105,
    color: '#f5d000',
  },
  {
    title: t('Have fun!', '好好享受吧！'),
    desc: t('Enjoy a good time with your personalized date!', '和为你挑选的 TA 度过一段美好时光！'),
    img: '/landing/how-4.webp',
    w: 119,
    color: '#2fb8ff',
  },
];

export const STATS = [
  { big: t('80,000+', '80,000+'), small: t('Dates arranged', '场约会已安排'), tone: 'pink' },
  { big: t('65%', '65%'), small: t('Success Rate', '成功率'), tone: 'yellow' },
  { big: t('92%', '92%'), small: t('Want a 2nd Date', '想再约一次'), tone: 'blue' },
] as const;

export const STATS_NOTE = t('Sample data', '示例数据');

export const MATCHMAKER = [
  { title: t('Backed by best research', '有扎实的研究撑腰'), img: '/landing/match-1.webp', w: 258, mw: 180 },
  { title: t('Ditto learns your preferences', '见一面会学习你的偏好'), img: '/landing/match-2.webp', w: 262, mw: 200 },
  { title: t('Scans the entire pool to find the one', '翻遍整个池子，只为找到那个人'), img: '/landing/match-3.webp', w: 222, mw: 190 },
];

export const TESTIMONIALS: { who: T; text: T; img: string; icon: string }[] = [
  {
    who: t('Sophia @Cal', '思琪 @复旦'),
    text: t("I had been matched a few weeks ago, and I'm in an exclusive relationship with my date now!", '几周前被匹配上，现在我们已经正式在一起啦！'),
    img: '/landing/gt-leo.jpg',
    icon: '💗',
  },
  {
    who: t('Justin @UCSD', '一凡 @浙大'),
    text: t('way more efficient than juggling 10 chats on other apps.', '比在别的 App 上同时聊 10 个人高效太多了。'),
    img: '/landing/gt-justin.jpg',
    icon: '👍',
  },
  {
    who: t('Angelina @UCLA', '雨桐 @北大'),
    text: t('Date was AMAZING, I feel like the AI made good hit!! We are currently going to meet again.', '约会超棒！感觉 AI 这次真的配准了！！我们已经约好下次再见。'),
    img: '/landing/gt-angelina.jpg',
    icon: '❤️',
  },
  {
    who: t('Ali @USC', '子轩 @中大'),
    text: t('ditto makes it super easy to meet ppl i actually wanna see irl', '见一面让我轻松见到真正想线下见的人'),
    img: '/landing/gt-ali.jpg',
    icon: '💗',
  },
  {
    who: t('Leo @Cal', '浩然 @清华'),
    text: t("I literally don't need to do anything and just wait for the AI match.", '我啥都不用做，等着 AI 匹配就行。'),
    img: '/landing/gt-leo.jpg',
    icon: '‼️',
  },
  {
    who: t('Mani @Cal', '嘉怡 @南大'),
    text: t("Because it's tied to your school, the quality feels guaranteed, and it just feels safer.", '因为绑定了本校，质量有保障，也更安心。'),
    img: '/landing/gt-angelina.jpg',
    icon: '💬',
  },
];

export const COMPARISON = {
  dittoLabel: t('Ditto', '见一面'),
  dittoTitle: t('One Ready-to-go Date Invite', '一份说走就走的约会邀请'),
  notifTitle: t('Ditto', '见一面'),
  notifBody: t('Your date is set for Friday. View details.', '你的约会定在周五，点击查看详情。'),
  now: t('now', '现在'),
  otherLabel: t('Other Dating Apps', '其他交友 App'),
  otherTitle: [t('Endless Swiping ', '无尽滑动 '), t('&', '&'), t(' Small Talk', ' 尬聊')] as const,
  app1: t('Dating Apps#1', '交友App#1'),
  app2: t('Dating Apps#2', '交友App#2'),
};

export const SAFETY = [
  { kicker: t('Safe #1', '安全 #1'), title: t('Verified students at your school only', '仅限本校认证学生'), kind: 'stamp' as const },
  { kicker: t('Safe #2', '安全 #2'), title: t('Only your date sees you', '只有你的约会对象能看到你'), kind: 'photo' as const },
  { kicker: t('Safe #3', '安全 #3'), title: t('Coffee dates on campus', '校园里的咖啡约会'), kind: 'campus' as const },
];

export const STAMP = { ring: t('STUDENT VERIFIED', '学生认证 · 学生认证') };

/** First answer is verbatim; the rest restate the official FAQ facts recorded in docs/research/01 §3–6. */
export const FAQ: { q: T; a: T }[] = [
  {
    q: t('How does Ditto pair people?', '见一面是怎么配对的？'),
    a: t(
      "Fill out a questionnaire about your values and what matters to you. After submitting your information, Ditto will text you a date plan that includes the time, place, and details of your match. The date will take place around the campus you're currently near, and we only match up verified college students.",
      '填一份关于你的价值观和在意之事的问卷。提交后，见一面会把约会安排发给你，包括时间、地点和对方的信息。约会就在你所在的校园附近进行，我们只匹配通过认证的在校大学生。',
    ),
  },
  {
    q: t('What will I know about my match before the date?', '约会前我能知道对方哪些信息？'),
    a: t(
      "You'll get a date card with your match's name, photos, the basics, and why we think you two will click. Contact info is only shared after you both confirm your availability.",
      '你会收到一张专属介绍卡：对方的名字、照片、基本信息，以及我们觉得你们合拍的理由。约会前不交换联系方式；见面后双方都想再见，才会互相显示微信。',
    ),
  },
  {
    q: t("What if I don't like my match/date?", '如果我不喜欢这次的匹配/约会怎么办？'),
    a: t(
      "Tell Ditto why, along with any other feedback, and we'll use it to arrange your next date. You can also update your preferences and profile anytime.",
      '直接告诉见一面原因和其他反馈，我们会据此安排下一次约会。你也可以随时修改自己的偏好和资料。',
    ),
  },
  {
    q: t("Who's participating?", '都有谁在参与？'),
    a: t('Verified college students, 18 and older.', '年满 18 岁、通过学校认证的在校大学生。'),
  },
  {
    q: t('Does it cost anything?', '收费吗？'),
    a: t('No. Ditto is completely free right now.', '目前完全免费。'),
  },
  {
    q: t("What if I can't make it last minute?", '临时去不了怎么办？'),
    a: t(
      'Text your date as soon as possible. Otherwise, you may be banned from future dates.',
      '请尽快告诉对方。无故爽约可能会被暂停之后的匹配。',
    ),
  },
  {
    q: t('How long does it usually take?', '一般要等多久？'),
    a: t(
      "We're still opening school by school, so it can take 1–2 weeks to guarantee a coffee date on campus. Since our latest upgrade, 70% of users get their first date within 2 days of signing up.",
      '我们还在逐校开放，通常 1–2 周内能安排上一次校园咖啡约会；系统升级后，70% 的同学注册后 2 天内就拿到了第一次约会。',
    ),
  },
  {
    q: t("What if I'm not getting Ditto's replies?", '没看到本周的匹配怎么办？'),
    a: t(
      'Our line can get busy. Get a new Ditto number to restart the conversation — your profile, preferences and matches stay the same.',
      '见一面目前不发短信，匹配结果只在网站上显示：每周三 19:00 以后登录打开首页就能看到。如果没看到，先确认问卷已经填完；也可能这周暂时没有合适的人，下周三会再为你匹配。',
    ),
  },
  {
    q: t('Where do the dates happen?', '约会在哪里进行？'),
    a: t(
      'At carefully selected spots on campus, chosen for your safety and a great experience.',
      '在为安全和体验精心挑选的校园地点，比如校内或学校附近的咖啡馆。',
    ),
  },
];

export const MANIFESTO = {
  line: t('Date Without Swiping', '约会，不必滑动'),
  script: t('Date without Swiping', '约会，不必滑动'),
  cta: t('Our Manifesto', '我们的宣言'),
};

export const FOOTER = {
  resources: t('Resources', '更多'),
  manifesto: t('Manifesto', '宣言'),
  byCity: t('Dating by City', '按城市约会'),
  byCollege: t('Dating by College', '按高校约会'),
  bubble: t('A friend that texts you ready-to-go dates.', '一个会给你发来现成约会的朋友。'),
  copyright: t('© Ditto 2026', '© 见一面 2026'),
  terms: t('Terms', '条款'),
  privacy: t('Privacy', '隐私'),
  cookies: t('Cookies', 'Cookie'),
};

/** Placeholder lists behind "Dating by City / College" until the SEO pages exist. */
export const CITIES: T[] = [
  t('Beijing', '北京'),
  t('Shanghai', '上海'),
  t('Guangzhou', '广州'),
  t('Shenzhen', '深圳'),
  t('Hangzhou', '杭州'),
  t('Nanjing', '南京'),
  t('Wuhan', '武汉'),
  t('Chengdu', '成都'),
  t("Xi'an", '西安'),
];

export const COLLEGES: T[] = [
  t('Tsinghua University', '清华大学'),
  t('Peking University', '北京大学'),
  t('Fudan University', '复旦大学'),
  t('Shanghai Jiao Tong University', '上海交通大学'),
  t('Zhejiang University', '浙江大学'),
  t('Nanjing University', '南京大学'),
  t('Sun Yat-sen University', '中山大学'),
  t('Wuhan University', '武汉大学'),
  t('Sichuan University', '四川大学'),
  t("Xi'an Jiaotong University", '西安交通大学'),
];

export const LOGIN = {
  back: t('Back', '返回'),
  title: t('Log in', '登录 / 注册'),
  phone: t('phone number', '手机号'),
  sendCode: t('Send code', '获取验证码'),
  resend: t('Resend', '重新获取'),
  code: t('6-digit code', '6 位验证码'),
  agreePrefix: t('I agree to the ', '我已阅读并同意'),
  terms: t('Terms of Service', '《用户协议》'),
  and: t(' and ', '和'),
  privacy: t('Privacy Policy', '《隐私政策》'),
  submit: t('Continue', '登录'),
  demo: t('Demo code: ', '演示验证码：'),
  footer: t('New here? Logging in with your phone creates an account.', '还没有账号？验证码登录即自动注册'),
  errPhone: t('Enter an 11-digit mainland phone number', '请输入 11 位手机号'),
  errCode: t('Enter the 6-digit code', '请输入 6 位验证码'),
  errWrong: t("That code doesn't match. Try again.", '验证码不正确，请重新输入'),
  errAgree: t('Please agree to the Terms and Privacy Policy first', '请先阅读并同意用户协议和隐私政策'),
  sent: t('Code sent', '验证码已发送'),
  sending: t('Sending…', '发送中…'),
  verifying: t('Logging in…', '登录中…'),
};
