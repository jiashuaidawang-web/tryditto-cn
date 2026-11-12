import { t } from '../i18n/lang';
import type { FlowContext, Option, Question } from './types';

/**
 * Question bank. `en` is Ditto's copy verbatim (docs/research/09, 10, 12 + questionnaire.json);
 * `zh` is the localized campus-tone copy. Keywords are wrapped in **…**.
 */

const o = (id: string, emoji: string | undefined, en: string, zh: string, emojiZh?: string): Option => ({
  id,
  emoji,
  label: t(en, zh),
  ...(emojiZh ? { emojiZh } : {}),
});

const OTHER = t('Other', '其他');
const IMPORTANCE: Option[] = [
  o('none', undefined, "doesn't matter", '无所谓'),
  o('kinda', undefined, 'kinda matters', '有点在意'),
  o('super', undefined, 'super important', '非常重要'),
];

export const HOBBIES: Option[] = [
  o('cooking', '👨‍🍳', 'Cooking', '下厨'),
  o('music', '🎵', 'Music', '音乐'),
  o('travel', '✈️', 'Travel', '旅行'),
  o('reading', '📚', 'Reading', '阅读'),
  o('gym', '🏋️', 'Gym', '健身'),
  o('gaming', '🎮', 'Gaming', '游戏'),
  o('movies', '🎬', 'Movies/TV', '影视'),
  o('art', '🎨', 'Art', '艺术'),
];

const ETHNICITIES: Option[] = [
  o('americanIndian', undefined, 'American Indian', '美洲原住民'),
  o('black', undefined, 'Black/African Descent', '非裔'),
  o('eastAsian', undefined, 'East Asian', '东亚裔'),
  o('hispanic', undefined, 'Hispanic/Latino', '拉丁裔'),
  o('middleEastern', undefined, 'Middle Eastern', '中东裔'),
  o('pacificIslander', undefined, 'Pacific Islander', '太平洋岛民'),
  o('southAsian', undefined, 'South Asian', '南亚裔'),
  o('southEastAsian', undefined, 'South East Asian', '东南亚裔'),
  o('white', undefined, 'White', '白人'),
];

const POS = {
  top: (they: boolean) => o('top', '🔝', they ? "They're more of a top" : "I'm more of a top", they ? 'ta 偏 1' : '我偏 1'),
  bottom: (they: boolean) =>
    o('bottom', '🔽', they ? "They're more of a bottom" : "I'm more of a bottom", they ? 'ta 偏 0' : '我偏 0'),
  masc: (they: boolean) => o('masc', '💪', they ? "They're more masc" : "I'm more masc", they ? 'ta 偏阳刚' : '我偏阳刚'),
  femme: (they: boolean) => o('femme', '🌸', they ? "They're more femme" : "I'm more femme", they ? 'ta 偏阴柔' : '我偏阴柔'),
  flexible: (they: boolean) => o('flexible', '🔄', they ? "They're flexible" : "I'm flexible", they ? 'ta 都可以' : '我都可以'),
  none: () => o('preferNot', '🤐', 'Prefer not to say', '不想说'),
};

function expectedGenders(ctx: FlowContext): string[] {
  const v = ctx.answers.expectedGender;
  return Array.isArray(v) ? (v as string[]) : [];
}

/** questionnaire.json `expectedPosition.optionsBy`. */
function idealMatchOptions(ctx: FlowContext): Option[] {
  const eg = expectedGenders(ctx);
  const only = (g: string) => eg.length === 1 && eg[0] === g;
  if (only('men')) return [POS.top(true), POS.bottom(true), POS.flexible(true), POS.none()];
  if (only('women')) return [POS.masc(true), POS.femme(true), POS.flexible(true), POS.none()];
  return [POS.top(true), POS.bottom(true), POS.masc(true), POS.femme(true), POS.flexible(true), POS.none()];
}

/** questionnaire.json `position.optionsBy` keyed by own gender. */
function describeYourselfOptions(ctx: FlowContext): Option[] {
  const g = ctx.answers.gender;
  if (g === 'man') return [POS.top(false), POS.bottom(false), POS.flexible(false), POS.none()];
  if (g === 'woman') return [POS.masc(false), POS.femme(false), POS.flexible(false), POS.none()];
  return [POS.top(false), POS.bottom(false), POS.masc(false), POS.femme(false), POS.flexible(false), POS.none()];
}

/** First datingHelp option depends on who you date (09: Women → girls; 12: Everyone → people). */
function datingHelpOptions(ctx: FlowContext): Option[] {
  const eg = expectedGenders(ctx);
  const only = (g: string) => eg.length === 1 && eg[0] === g;
  const first = only('women')
    ? o('approaching', '👋', 'approaching girls', '主动认识女生')
    : only('men')
      ? o('approaching', '👋', 'approaching guys', '主动认识男生')
      : o('approaching', '👋', 'approaching people', '主动认识新的人');
  return [
    first,
    o('convo', '💬', 'keeping a convo going', '把天聊下去'),
    o('intoMe', '👀', 'telling if they’re into me', '看出 ta 对我有没有意思'),
    o('confident', '💪', 'being more confident', '更自信一点'),
    o('whatIWant', '🧭', 'figuring out what i want', '想清楚自己要什么'),
    o('theDate', '☕', 'the date itself', '约会本身'),
    o('justDate', '💘', 'just get me a date', '直接帮我约一个'),
  ];
}

/** Student list per 09 §23; professional list per 13 §06 (variant switches Poster/Tabling for Facebook/LinkedIn). */
function hearAboutOptions(ctx: FlowContext): Option[] {
  const student = ctx.answers.isStudent === 'yes';
  if (ctx.lang === 'zh') {
    return [
      o('friend', undefined, 'Friend', '朋友推荐'),
      ...(student ? [o('poster', undefined, 'Poster', '校园海报'), o('tabling', undefined, 'Tabling', '校园摆摊')] : []),
      o('rednote', undefined, 'RedNote (Xiaohongshu)', '小红书'),
      o('douyin', undefined, 'TikTok', '抖音'),
      o('wechat', undefined, 'WeChat', '微信 / 朋友圈'),
      o('bilibili', undefined, 'Bilibili', 'B 站'),
      o('weibo', undefined, 'Weibo', '微博'),
    ];
  }
  return student
    ? [
        o('friend', undefined, 'Friend', '朋友推荐'),
        o('poster', undefined, 'Poster', '校园海报'),
        o('tabling', undefined, 'Tabling', '校园摆摊'),
        o('instagram', undefined, 'Instagram', 'Instagram'),
        o('douyin', undefined, 'TikTok', '抖音'),
        o('x', undefined, 'X (Twitter)', 'X'),
        o('rednote', undefined, 'RedNote (Xiaohongshu)', '小红书'),
        o('google', undefined, 'Google', 'Google'),
      ]
    : [
        o('friend', undefined, 'Friend', '朋友推荐'),
        o('instagram', undefined, 'Instagram', 'Instagram'),
        o('facebook', undefined, 'Facebook', 'Facebook'),
        o('douyin', undefined, 'TikTok', '抖音'),
        o('x', undefined, 'X (Twitter)', 'X'),
        o('linkedin', undefined, 'LinkedIn', '领英'),
        o('google', undefined, 'Google', 'Google'),
        o('rednote', undefined, 'RedNote (Xiaohongshu)', '小红书'),
      ];
}

const q = (question: Question) => question;

export const QUESTIONS = {
  isStudent: q({
    id: 'isStudent',
    type: 'single',
    bubbles: [t('are you currently a **student**?', '你现在还是**学生**吗？')],
    private: true,
    options: [
      o('yes', '🎓', "yes, i'm a student", '是的，我是学生'),
      o('no', '💼', 'nope, not a student', '不是，已经工作啦'),
    ],
  }),
  name: q({
    id: 'name',
    type: 'text',
    bubbles: [t("Hi, I'm Ditto", '嗨，我是见一面'), t("what's your **name**?", '你叫什么**名字**？')],
    placeholder: t('your name', '你的名字'),
    maxLength: 40,
  }),
  gender: q({
    id: 'gender',
    type: 'single',
    bubbles: [t("what's your **gender**?", '你的**性别**是？')],
    options: [
      o('man', undefined, 'Man', '男生'),
      o('woman', undefined, 'Woman', '女生'),
      o('nonbinary', undefined, 'Nonbinary', '非二元'),
      o('other', undefined, 'Other', '其他'),
    ],
  }),
  expectedGender: q({
    id: 'expectedGender',
    type: 'multi',
    bubbles: [t('Who are you looking to **date**?', '你想和谁**约会**？')],
    private: true,
    exclusive: ['everyone'],
    options: [
      o('men', undefined, 'Men', '男生'),
      o('women', undefined, 'Women', '女生'),
      o('nonbinary', undefined, 'Nonbinary', '非二元'),
      o('everyone', undefined, 'Everyone', '都可以'),
    ],
  }),
  birthday: q({
    id: 'birthday',
    type: 'date',
    bubbles: [t("when's your **birthday**?", '你的**生日**是哪天？')],
    helper: t('Only your age is shown to others.', '别人只会看到你的年龄。'),
    placeholder: t('MM / DD / YYYY', 'YYYY / MM / DD'),
  }),
  hobbies: q({
    id: 'hobbies',
    type: 'multi',
    bubbles: [t('what are your **hobbies** & **interests**?', '你平时有什么**爱好**和**兴趣**？')],
    options: HOBBIES,
    maxSelect: 5,
    maxHint: t('max 5. deselect one to swap', '最多 5 个，取消一个再换'),
    allowCustom: t('Type your hobby', '写下你的爱好'),
  }),

  // ── Hobby branches (bank order: Cooking, Music, Travel, Reading, Gym, Gaming, Movies/TV, Art) ──
  cookingStyle: q({
    id: 'cookingStyle',
    hobby: 'cooking',
    type: 'multi',
    bubbles: [t("what's your **cooking style**?", '你的**做饭风格**是？')],
    allowCustom: OTHER,
    options: [
      o('italian', '🍝', 'Italian', '意面西餐'),
      o('asian', '🍜', 'Asian', '中式家常'),
      o('mexican', '🌮', 'Mexican', '异国料理'),
      o('comfort', '🍲', 'Comfort food', '火锅炖煮'),
      o('baking', '🧁', 'Baking', '烘焙甜点'),
      o('grilling', '🔥', 'Grilling', '烧烤撸串'),
      o('healthy', '🥗', 'Healthy bowls', '减脂轻食'),
      o('brunch', '🥐', 'Brunch', '早午餐'),
    ],
  }),
  cookingSignatureDish: q({
    id: 'cookingSignatureDish',
    hobby: 'cooking',
    type: 'text',
    bubbles: [t("what's your **signature dish**?", '你的**拿手菜**是？')],
    placeholder: t('the one u brag about', '能拿出来炫的那道'),
  }),
  musicSound: q({
    id: 'musicSound',
    hobby: 'music',
    type: 'multi',
    bubbles: [t("what's your **music sound**?", '你平时听什么**音乐**？')],
    allowCustom: OTHER,
    options: [
      o('pop', '🎤', 'Pop', '流行'),
      o('hiphop', '🎧', 'Hip-hop', '说唱'),
      o('indie', '🎸', 'Indie', '独立'),
      o('electronic', '🎹', 'Electronic', '电子'),
      o('rnb', '💞', 'R&B', 'R&B'),
      o('rock', '🤘', 'Rock', '摇滚'),
      o('country', '🤠', 'Country', '民谣', '🪕'),
      o('jazz', '🎷', 'Jazz', '爵士'),
      o('classical', '🎻', 'Classical', '古典'),
      o('kpop', '🩷', 'K-pop', 'K-pop'),
      o('latin', '🪇', 'Latin', '国风', '🏮'),
    ],
  }),
  musicNowPlaying: q({
    id: 'musicNowPlaying',
    hobby: 'music',
    type: 'text',
    bubbles: [t('who u **streaming** rn?', '最近在**单曲循环**谁？')],
    placeholder: t('artist on repeat rn', '最近循环的歌手'),
  }),
  travelStyle: q({
    id: 'travelStyle',
    hobby: 'travel',
    type: 'multi',
    bubbles: [t("what's your **travel style**?", '你的**旅行风格**是？')],
    allowCustom: OTHER,
    options: [
      o('backpacking', '🎒', 'Backpacking', '穷游背包'),
      o('luxury', '🍾', 'Luxury', '精致度假'),
      o('foodie', '🥘', 'Foodie tours', '美食探店'),
      o('adventure', '⛰️', 'Adventure', '户外徒步'),
      o('beach', '🏖️', 'Beach + sun', '海边躺平'),
      o('city', '🏙️', 'City-hopping', 'citywalk'),
      o('solo', '✈️', 'Solo travel', '一个人旅行'),
      o('road', '🚗', 'Road trips', '自驾'),
    ],
  }),
  travelNext: q({
    id: 'travelNext',
    hobby: 'travel',
    type: 'text',
    bubbles: [t("what's your next **travel destination**?", '下一个想去的**目的地**是？')],
    placeholder: t('where u manifesting', '在许愿去哪'),
  }),
  readingGenres: q({
    id: 'readingGenres',
    hobby: 'reading',
    type: 'multi',
    bubbles: [t('what **genres** u reading rn?', '最近在读什么**类型**的书？')],
    allowCustom: OTHER,
    options: [
      o('litfic', '📖', 'Lit fic', '严肃文学'),
      o('fantasy', '🐉', 'Fantasy', '奇幻'),
      o('scifi', '🚀', 'Sci-fi', '科幻'),
      o('mystery', '🔍', 'Mystery', '推理悬疑'),
      o('romance', '💕', 'Romance', '言情'),
      o('memoir', '🪞', 'Memoir', '传记回忆录'),
      o('selfhelp', '🧠', 'Self-help', '自我成长'),
      o('poetry', '🌹', 'Poetry', '诗歌'),
      o('classics', '📚', 'Classics', '经典名著'),
      o('smut', '😏', 'Smut idk', '网文 hh'),
    ],
  }),
  readingLastBook: q({
    id: 'readingLastBook',
    hobby: 'reading',
    type: 'text',
    bubbles: [t('**last book** u finished?', '**最近读完**的一本书？')],
    placeholder: t('name + author', '书名 + 作者'),
  }),
  gymTrainingStyle: q({
    id: 'gymTrainingStyle',
    hobby: 'gym',
    type: 'multi',
    bubbles: [t("what's your **training style**?", '你的**训练方式**是？')],
    allowCustom: OTHER,
    options: [
      o('lifting', '🏋️', 'Lifting', '撸铁'),
      o('running', '🏃', 'Running', '跑步'),
      o('yoga', '🧘', 'Yoga', '瑜伽'),
      o('pilates', '🩰', 'Pilates', '普拉提'),
      o('crossfit', '💪', 'Crossfit', 'CrossFit'),
      o('boxing', '🥊', 'Boxing', '拳击'),
      o('dance', '💃', 'Dance', '跳舞'),
      o('cycling', '🚴', 'Cycling', '骑行'),
      o('climbing', '🧗', 'Climbing', '攀岩'),
    ],
  }),
  gymTrainingFor: q({
    id: 'gymTrainingFor',
    hobby: 'gym',
    type: 'text',
    bubbles: [t('what u **training for**?', '你在**为什么而练**？')],
    placeholder: t('aesthetic, marathon, principle...', '练好看、跑全马、单纯自律…'),
  }),
  gamingGenres: q({
    id: 'gamingGenres',
    hobby: 'gaming',
    type: 'multi',
    bubbles: [t('what **games** u into?', '你喜欢玩什么**游戏**？')],
    allowCustom: OTHER,
    options: [
      o('fps', '🔫', 'FPS', '射击'),
      o('rpg', '⚔️', 'RPG', '角色扮演'),
      o('sports', '⚽', 'Sports games', '体育竞技'),
      o('moba', '🎮', 'MOBA', 'MOBA'),
      o('strategy', '🧠', 'Strategy', '策略'),
      o('indie', '🎨', 'Indie', '独立游戏'),
      o('cozy', '🌱', 'Sims/cozy', '模拟经营'),
      o('fighting', '👊', 'Fighting', '格斗'),
      o('mmo', '🌐', 'MMO', '网游'),
    ],
  }),
  gamingNowPlaying: q({
    id: 'gamingNowPlaying',
    hobby: 'gaming',
    type: 'text',
    bubbles: [t('what u **playing** rn?', '最近在**玩**什么？')],
    placeholder: t('game on repeat rn', '最近沉迷的游戏'),
  }),
  moviesContent: q({
    id: 'moviesContent',
    hobby: 'movies',
    type: 'multi',
    bubbles: [t('what kinda **content** u like?', '你喜欢看什么**内容**？')],
    allowCustom: OTHER,
    options: [
      o('indie', '🎞️', 'Indie', '文艺片'),
      o('horror', '👻', 'Horror', '恐怖'),
      o('romcom', '💞', 'Rom-com', '爱情喜剧'),
      o('action', '💥', 'Action', '动作'),
      o('scifi', '🛸', 'Sci-fi', '科幻'),
      o('documentary', '📹', 'Documentary', '纪录片'),
      o('a24', '🎬', 'A24 stuff', 'A24 那挂'),
      o('foreign', '🌍', 'Foreign', '外语片'),
      o('anime', '🎌', 'Anime', '动漫'),
      o('reality', '📺', 'Reality TV', '综艺'),
    ],
  }),
  moviesLastWatch: q({
    id: 'moviesLastWatch',
    hobby: 'movies',
    type: 'text',
    bubbles: [t('**last great watch**?', '最近看过**最好看**的？')],
    placeholder: t('name it', '说个名字'),
  }),
  artMediums: q({
    id: 'artMediums',
    hobby: 'art',
    type: 'multi',
    bubbles: [t('what **mediums** u work in?', '你用什么**媒介**创作？')],
    allowCustom: OTHER,
    options: [
      o('drawing', '✏️', 'Drawing', '素描'),
      o('painting', '🎨', 'Painting', '绘画'),
      o('digital', '💻', 'Digital', '板绘数字'),
      o('pottery', '🏺', 'Pottery', '陶艺'),
      o('sculpture', '🗿', 'Sculpture', '雕塑'),
      o('collage', '✂️', 'Collage', '拼贴'),
      o('crafts', '🧵', 'Crafts', '手工'),
      o('mixed', '🌈', 'Mixed media', '综合材料'),
    ],
  }),
  artMake: q({
    id: 'artMake',
    hobby: 'art',
    type: 'text',
    bubbles: [t('what do u **make**?', '你都**做**些什么？')],
    placeholder: t('describe your work', '描述一下你的作品'),
  }),

  // ── Your type ──
  ethnicity: q({
    id: 'ethnicity',
    type: 'multi',
    bubbles: [t("What's your **ethnicity**?", '你的**族裔**是？')],
    private: true,
    options: ETHNICITIES,
    drop: { zh: true },
  }),
  expectedEthnicity: q({
    id: 'expectedEthnicity',
    type: 'multi',
    bubbles: [t('Any **ethnicity** preferences?', '对**族裔**有偏好吗？')],
    private: true,
    exclusive: ['noPreference'],
    options: [o('noPreference', undefined, 'No Preference', '没有偏好'), ...ETHNICITIES],
    drop: { zh: true },
  }),
  expectedAgeRange: q({
    id: 'expectedAgeRange',
    type: 'ageRange',
    bubbles: [t("What's your preferred **age range**?", '你希望对方的**年龄范围**是？')],
    private: true,
  }),
  expectedPosition: q({
    id: 'expectedPosition',
    type: 'multi',
    bubbles: [t('your **ideal match**?', '你的**理想型**？')],
    private: true,
    optionsFor: idealMatchOptions,
    exclusive: ['preferNot'],
    wrapPills: true,
    drop: { zh: true },
  }),
  position: q({
    id: 'position',
    type: 'single',
    bubbles: [t('how would u describe **yourself**?', '你会怎么形容**自己**？')],
    private: true,
    optionsFor: describeYourselfOptions,
    wrapPills: true,
    drop: { zh: true },
  }),
  height: q({
    id: 'height',
    type: 'height',
    bubbles: [t('How **tall** are you?', '你的**身高**是？')],
  }),
  heightRange: q({
    id: 'heightRange',
    type: 'heightRange',
    bubbles: [t('how **tall** should your match be?', '你希望对方**多高**？')],
    private: true,
  }),

  // ── Emails ──
  schoolEmail: q({
    id: 'schoolEmail',
    type: 'email',
    emailKind: 'school',
    bubbles: [t("what's your school **email**? 📧", '你的学校**邮箱**是？📧')],
    escape: t('not a student? continue without a school email', '不是学生？不用学校邮箱继续'),
    helper: t("we need a school email (.edu) to verify you're a student", '需要学校邮箱（edu.cn）来验证你的学生身份'),
    placeholder: t('you@school.edu', 'you@school.edu.cn'),
  }),
  personalEmail: q({
    id: 'personalEmail',
    type: 'email',
    emailKind: 'personal',
    bubbles: [t("what's your **email**? 📧", '你的**邮箱**是？📧')],
    helper: t("don't lose access to your account, verify your email", '验证一下邮箱，别把账号弄丢啦'),
    placeholder: t('you@email.com', 'you@email.com'),
  }),
  /** 12 §0.1: the page title is `let's verify that email 📬`, loading text `sending code…`. Code length per 07 JSON. */
  emailVerify: q({
    id: 'emailVerify',
    type: 'code',
    bubbles: [t("let's verify that **email** 📬", '验证一下你的**邮箱** 📬'), t('we sent u a 6-digit code', '我们给你发了一个 6 位验证码')],
    placeholder: t('6-digit code', '6 位验证码'),
  }),
  location: q({
    id: 'location',
    type: 'location',
    bubbles: [t('**Where do you want to date**? 📍', '**你想在哪一带约会**？📍')],
  }),

  // ── Photos + preferences tail ──
  photos: q({
    id: 'photos',
    type: 'photos',
    bubbles: [t("Let's add at least 2 photos 📷", '至少放 2 张照片吧 📷'), t('Choose photos that show your vibe', '挑几张最能体现你 vibe 的')],
    helper: t(
      'Photos from different moments help us find better matches. You can swap them anytime.',
      '不同场景的照片能帮我们匹配得更准，之后随时可以换。',
    ),
  }),
  preferencesIntro: q({
    id: 'preferencesIntro',
    type: 'interstitial',
    bubbles: [t("now let's talk about your preferences", '接下来聊聊你的偏好吧')],
  }),
  relationshipLooking: q({
    id: 'relationshipLooking',
    type: 'multi',
    bubbles: [t('What are you **looking for**?', '你在**找**什么样的关系？')],
    private: true,
    options: [
      o('lifePartner', undefined, 'Life partner', '人生伴侣'),
      o('serious', undefined, 'Serious relationship', '认真谈恋爱'),
      o('casual', undefined, 'Casual dates', '轻松约会'),
      o('friends', undefined, 'New friends', '交新朋友'),
      o('notSure', undefined, 'Not sure yet', '还没想好'),
    ],
  }),
  matchingStyle: q({
    id: 'matchingStyle',
    type: 'single',
    bubbles: [t('how do u want ditto to **match u** rn?', '你希望见一面现在怎么**帮你匹配**？')],
    private: true,
    options: [
      o('faster', '⚡️', 'More dates, faster', '多约几次，快一点'),
      o('steady', '⚖️', 'A steady mix', '稳稳的节奏'),
      o('fewer', '🎯', 'Fewer, better matches', '少而精'),
      o('theOne', '💎', 'Wait for the one', '等那个对的人'),
    ],
  }),
  schoolYear: q({
    id: 'schoolYear',
    type: 'single',
    bubbles: [t('what **year** are u going into this fall?', '这学期你读**几年级**？')],
    options: [
      o('freshman', undefined, 'Freshman', '大一'),
      o('sophomore', undefined, 'Sophomore', '大二'),
      o('junior', undefined, 'Junior', '大三'),
      o('senior', undefined, 'Senior', '大四'),
      o('master', undefined, 'Master', '硕士'),
      o('phd', undefined, 'PhD', '博士'),
      o('other', undefined, 'Other', '其他'),
    ],
  }),
  hearAbout: q({
    id: 'hearAbout',
    type: 'single',
    bubbles: [t('how did u **hear about us**?', '你是从哪儿**知道我们**的？')],
    allowCustom: OTHER,
    optionsFor: hearAboutOptions,
  }),
  occupation: q({
    id: 'occupation',
    type: 'longText',
    bubbles: [t('what do you do for **work**?', '你现在做什么**工作**？')],
    placeholder: t('e.g. software engineer at a tech company', '比如：在互联网公司做产品经理'),
    maxLength: 120,
  }),
  firstDateBudget: q({
    id: 'firstDateBudget',
    type: 'single',
    bubbles: [t('what’s ur **budget** for a first date?', '第一次约会的**预算**大概多少？'), t('per person, roughly', '人均，大概就行')],
    private: true,
    options: [
      o('under20', '☕', 'under $20', '100 元以内'),
      o('20to50', '🥂', '$20–50', '100–200 元'),
      o('50to100', '🍝', '$50–100', '200–400 元'),
      o('100plus', '✨', '$100+', '400 元以上'),
      o('noPref', '🤷', 'no preference', '都可以'),
    ],
  }),
  dealbreakers: q({
    id: 'dealbreakers',
    type: 'longText',
    bubbles: [
      t('**dealbreakers**: what should Ditto avoid?', '**雷区**：见一面要帮你避开什么？'),
      t('include anything you care about: physical traits, behavior, personality, etc.', '在意的都可以写：外形、习惯、性格等等。'),
    ],
    private: true,
    placeholder: t("anything that's a hard no…", '绝对不行的点…'),
    maxLength: 1000,
    optional: true,
  }),
  educationLevel: q({
    id: 'educationLevel',
    type: 'single',
    bubbles: [t("what's the highest level of **education** you've completed?", '你的最高**学历**是？')],
    private: true,
    options: [
      o('highSchool', undefined, 'high school', '高中及以下'),
      o('college', undefined, 'college degree', '本科 / 大专'),
      o('graduate', undefined, 'graduate degree', '硕士及以上'),
      o('preferNot', undefined, 'prefer not to say', '不想说'),
    ],
  }),
  educationImportance: q({
    id: 'educationImportance',
    type: 'single',
    bubbles: [t("does your match's **education** have to be at least yours?", '对方的**学历**需要不低于你吗？')],
    private: true,
    options: IMPORTANCE,
  }),
  politics: q({
    id: 'politics',
    type: 'single',
    bubbles: [t("what's your **political vibe**?", '你的**政治倾向**是？')],
    private: true,
    allowCustom: OTHER,
    options: [
      o('veryLeft', undefined, 'Very left', '很左'),
      o('left', undefined, 'Left-leaning', '偏左'),
      o('center', undefined, 'Center', '中间'),
      o('right', undefined, 'Right-leaning', '偏右'),
      o('veryRight', undefined, 'Very right', '很右'),
      o('apolitical', undefined, 'Apolitical', '不关心'),
    ],
    drop: { zh: true },
  }),
  politicsImportance: q({
    id: 'politicsImportance',
    type: 'single',
    bubbles: [t('does your match have to share your **politics**?', '对方需要和你**政治观点**一致吗？')],
    private: true,
    options: IMPORTANCE,
    drop: { zh: true },
  }),
  religion: q({
    id: 'religion',
    type: 'multi',
    bubbles: [t("what's your **religion**?", '你的**宗教信仰**是？')],
    private: true,
    allowCustom: OTHER,
    options: [
      o('atheist', undefined, 'Atheist', '无神论'),
      o('agnostic', undefined, 'Agnostic', '不可知论'),
      o('christian', undefined, 'Christian', '基督教'),
      o('catholic', undefined, 'Catholic', '天主教'),
      o('jewish', undefined, 'Jewish', '犹太教'),
      o('muslim', undefined, 'Muslim', '伊斯兰教'),
      o('hindu', undefined, 'Hindu', '印度教'),
      o('buddhist', undefined, 'Buddhist', '佛教'),
      o('spiritual', undefined, 'Spiritual', '灵性'),
    ],
    drop: { zh: true },
  }),
  religionImportance: q({
    id: 'religionImportance',
    type: 'single',
    bubbles: [t('does your match have to share your **religion**?', '对方需要和你**信仰**一致吗？')],
    private: true,
    options: IMPORTANCE,
    drop: { zh: true },
  }),
  firstMessage: q({
    id: 'firstMessage',
    type: 'longText',
    bubbles: [
      t('**first thing** your match should know?', '想让对方**第一时间**知道你什么？'),
      t('this helps your match get to know you better', '这能帮对方更快了解你'),
    ],
    sendHint: t('your match will see this', '对方会看到这段话'),
    placeholder: t('could be a fun fact, a vibe check…', '可以是一个冷知识，或者你的 vibe…'),
    maxLength: 300,
  }),
  datingHelp: q({
    id: 'datingHelp',
    type: 'multi',
    bubbles: [t('if you had a **dating coach**, what would you want help with?', '如果你有个**恋爱教练**，最想让 ta 帮你什么？')],
    optionsFor: datingHelpOptions,
  }),
} satisfies Record<string, Question>;

export type QuestionId = keyof typeof QUESTIONS;

export const BRANCHES: Record<string, [QuestionId, QuestionId]> = {
  cooking: ['cookingStyle', 'cookingSignatureDish'],
  music: ['musicSound', 'musicNowPlaying'],
  travel: ['travelStyle', 'travelNext'],
  reading: ['readingGenres', 'readingLastBook'],
  gym: ['gymTrainingStyle', 'gymTrainingFor'],
  gaming: ['gamingGenres', 'gamingNowPlaying'],
  movies: ['moviesContent', 'moviesLastWatch'],
  art: ['artMediums', 'artMake'],
};

export const PRIVATE_NOTE = t('Private. Only used to match you', '仅用于匹配，不会公开');

export function optionsOf(question: Question, ctx: FlowContext): Option[] {
  return question.optionsFor ? question.optionsFor(ctx) : (question.options ?? []);
}
