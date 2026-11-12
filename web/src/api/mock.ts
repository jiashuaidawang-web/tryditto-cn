/**
 * 推断设计 — fake partners for the match loop demo. Avatars are generated SVG art (never real people).
 * TODO(spring-boot): replaced by GET /api/matches/current (partner card + AI reasons are server-side).
 */
import { t, type T } from '../i18n/lang';
import type { Answers } from '../onboarding/types';
import type { Partner, Reason } from './types';

export const PARTNERS: Partner[] = [
  {
    id: 'linxia',
    name: t('Lin Xia', '林夏'),
    gender: 'woman',
    age: 21,
    school: t('Peking University', '北京大学'),
    major: t('Chinese Literature', '中文系'),
    year: t('Junior', '大三'),
    heightCm: 165,
    hobbies: ['reading', 'cooking', 'movies', 'travel'],
    extraTags: [t('film cameras', '胶片相机'), t('citywalk', 'citywalk'), t('hotpot', '火锅')],
    avatar: { bg: ['#ffb4a2', '#ffd6a5'], skin: '#f6d1b5', hair: 'bob', hairColor: '#2b1d1a', shirt: '#5b8def', earring: true },
    scenes: ['books', 'coffee', 'film', 'noodles', 'sunset'],
    headline: t('Lin Xia is the perfect date you’re looking for!', '林夏就是你一直在等的那个约会对象！'),
    blurb: [
      t(
        'She’s warm, quick-witted and always has a paperback in her tote — the kind of person who turns a coffee into a three-hour talk about the last book that wrecked her.',
        '她温柔又机灵，帆布包里永远揣着一本书。一杯咖啡能被她聊成三小时，从最近让她哭的那本小说，一路聊到楼下哪家面馆最好吃。',
      ),
      t(
        'You’ll love how curious she is. If you’re into someone who asks the second question, not just the first, Lin Xia’s your kind of vibe.',
        '你会喜欢她的好奇心——她不只问第一个问题，还会追问第二个。如果你喜欢认真听你说话的人，林夏就是你的菜。',
      ),
    ],
    wechat: 'linxia_demo_0412',
    venue: {
      id: 'pku-treehole',
      name: t('Treehole Café · by Weiming Lake', '树洞咖啡 · 未名湖畔'),
      address: t('200 m inside PKU East Gate', '北京大学东门内 200 米'),
      activity: t('One drink, about 45 min', '一杯饮品，约 45 分钟'),
      extra: t('Then a lap around the lake if it’s going well', '聊得来的话，绕未名湖走一圈'),
    },
    openers: [
      t('Ask her which book she’d hand a stranger on the subway.', '问问她：如果要在地铁上递给陌生人一本书，她会选哪本？'),
      t('She just got back from Dali — ask about the film roll she hasn’t developed yet.', '她刚从大理回来，问问那卷还没冲洗的胶卷里拍了什么。'),
    ],
    fallbackReasons: [
      {
        text: t('You both want something real this semester, not endless swiping.', '你们这学期都想认真认识一个人，而不是一直刷来刷去。'),
        basis: t('Both picked “Serious relationship”', '你们都选了「认真谈恋爱」'),
      },
      {
        text: t('Your humor lands with her: she wrote that she falls for people who make her laugh at dinner.', '你的幽默会被她接住：她写过「饭桌上能把我逗笑的人最加分」。'),
        basis: t('Her “first thing your match should know”', 'ta 的「想让对方知道的第一件事」'),
      },
    ],
  },
  {
    id: 'zhouyu',
    name: t('Zhou Yu', '周屿'),
    gender: 'man',
    age: 22,
    school: t('Tsinghua University', '清华大学'),
    major: t('Architecture', '建筑学院'),
    year: t('Senior', '大四'),
    heightCm: 181,
    hobbies: ['gym', 'music', 'art', 'travel'],
    extraTags: [t('sketching', '速写'), t('indie gigs', 'livehouse'), t('early runs', '晨跑')],
    avatar: { bg: ['#a0c4ff', '#bdb2ff'], skin: '#eac4a4', hair: 'short', hairColor: '#1b1716', shirt: '#2d3142', glasses: true },
    scenes: ['guitar', 'mountain', 'camera', 'bike', 'coffee'],
    headline: t('Zhou Yu is the perfect date you’re looking for!', '周屿就是你一直在等的那个约会对象！'),
    blurb: [
      t(
        'He’s calm, a little nerdy and secretly hilarious — sketches buildings on napkins, runs at 6am, and knows every small livehouse in Haidian.',
        '他安静、有点书呆子气，但其实很好笑：会在餐巾纸上画建筑速写，早上 6 点去操场跑步，海淀每家小 livehouse 都门儿清。',
      ),
      t(
        'You’ll love how steady he is. If you want someone who plans the second date before the first one ends, Zhou Yu’s your kind of vibe.',
        '你会喜欢他的稳。如果你想找一个第一次约会还没结束、就开始计划第二次的人，周屿就是你的菜。',
      ),
    ],
    wechat: 'zhouyu_demo_0907',
    venue: {
      id: 'thu-lotus',
      name: t('Lotus Pond Book Café · Tsinghua', '荷塘书咖 · 清华近春园旁'),
      address: t('Next to Jinchun Garden, Tsinghua', '清华大学近春园东侧'),
      activity: t('Coffee and a walk, about 1 hour', '一杯咖啡加散步，约 1 小时'),
      extra: t('He’ll show you his favourite building on campus', '他会带你看校园里他最喜欢的那栋楼'),
    },
    openers: [
      t('Ask him which campus building he’d tear down and why.', '问问他：校园里哪栋楼他最想重新设计？为什么？'),
      t('He’s been to 40 livehouse shows this year — ask for the one band you should hear next.', '他今年看了 40 场 livehouse，问问他你下一个该听哪支乐队。'),
    ],
    fallbackReasons: [
      {
        text: t('You both pick “a steady mix” — neither of you wants to rush.', '你们都选了「稳稳的节奏」——都不想太快。'),
        basis: t('Both answered “how do u want Ditto to match u”', '你们都回答了「希望怎么帮你匹配」'),
      },
      {
        text: t('He likes people who notice small things; your answers are full of them.', '他喜欢会注意小细节的人，而你的回答里全是细节。'),
        basis: t('His dealbreakers + your first message', 'ta 的「雷区」+ 你的「第一句话」'),
      },
    ],
  },
  {
    id: 'xumu',
    name: t('Xu Mu', '许沐'),
    gender: 'nonbinary',
    age: 20,
    school: t('Renmin University of China', '中国人民大学'),
    major: t('Journalism', '新闻学院'),
    year: t('Sophomore', '大二'),
    heightCm: 172,
    hobbies: ['gaming', 'music', 'movies', 'cooking'],
    extraTags: [t('podcasts', '播客'), t('cats', '猫'), t('late-night noodles', '深夜面馆')],
    avatar: { bg: ['#caffbf', '#9bf6ff'], skin: '#f1cfb0', hair: 'curly', hairColor: '#4a2f27', shirt: '#ff6b9a' },
    scenes: ['gamepad', 'cat', 'noodles', 'film', 'plant'],
    headline: t('Xu Mu is the perfect date you’re looking for!', '许沐就是你一直在等的那个约会对象！'),
    blurb: [
      t(
        'They’re sharp, playful and run a tiny campus podcast — equally happy debating a film’s ending or carrying a team in a ranked match.',
        'ta 机灵又爱玩，在学校做一档小播客：既能和你争论一部电影的结局，也能在排位赛里带你上分。',
      ),
      t(
        'You’ll love how easy they are to talk to. If you’re into someone with opinions and a soft spot for stray cats, Xu Mu’s your kind of vibe.',
        '你会发现和 ta 聊天毫不费力。如果你喜欢有观点、又会偷偷喂流浪猫的人，许沐就是你的菜。',
      ),
    ],
    wechat: 'xumu_demo_1123',
    venue: {
      id: 'ruc-mingde',
      name: t('Mingde Coffee Corner · RUC', '明德咖啡角 · 人大东门'),
      address: t('Inside RUC East Gate, Mingde Building 1F', '中国人民大学东门内，明德楼一层'),
      activity: t('One drink, about 45 min', '一杯饮品，约 45 分钟'),
      extra: t('Noodles nearby if you’re both hungry', '饿了的话，附近有家深夜面馆'),
    },
    openers: [
      t('Ask what the last podcast episode they recorded was about.', '问问 ta 最近一期播客聊了什么。'),
      t('They have strong opinions on film endings — bring one you hated.', 'ta 对电影结局很有看法，带一个你最讨厌的结局去聊。'),
    ],
    fallbackReasons: [
      {
        text: t('You both said you want someone easy to talk to over food.', '你们都想找一个能边吃边聊、聊得很轻松的人。'),
        basis: t('Both chose food-related hobbies', '你们的爱好里都有「吃」'),
      },
      {
        text: t('Neither of you is in a hurry — you both picked fewer, better matches.', '你们都不着急——都选了「少而精」。'),
        basis: t('Both answered “how do u want Ditto to match u”', '你们都回答了「希望怎么帮你匹配」'),
      },
    ],
  },
];

export function partnerById(id: string | undefined): Partner {
  return PARTNERS.find((p) => p.id === id) ?? PARTNERS[0]!;
}

/** Pick a partner consistent with who the user wants to date. */
export function defaultPartnerFor(answers: Answers): Partner {
  const eg = Array.isArray(answers.expectedGender) ? (answers.expectedGender as string[]) : [];
  if (eg.length === 1 && eg[0] === 'women') return PARTNERS[0]!;
  if (eg.length === 1 && eg[0] === 'men') return PARTNERS[1]!;
  return PARTNERS[2]!;
}

const str = (v: unknown) => (typeof v === 'string' && v.trim() ? v.trim() : undefined);

const HOBBY_LABEL: Record<string, T> = {
  cooking: t('Cooking', '下厨'),
  music: t('Music', '音乐'),
  travel: t('Travel', '旅行'),
  reading: t('Reading', '阅读'),
  gym: t('Gym', '健身'),
  gaming: t('Gaming', '游戏'),
  movies: t('Movies/TV', '影视'),
  art: t('Art', '艺术'),
};

/** One reason per shared hobby, quoting the user's own branch answer when there is one. */
function hobbyReason(hobby: string, answers: Answers, partner: Partner): Reason | null {
  const name = partner.name;
  const basis = t(`Your “${HOBBY_LABEL[hobby]!.en}” + ${name.en}’s`, `你的「${HOBBY_LABEL[hobby]!.zh}」+ ${name.zh}的`);
  switch (hobby) {
    case 'cooking': {
      const dish = str(answers.cookingSignatureDish);
      return {
        text: dish
          ? t(`You both cook to unwind — you brag about ${dish}, ${name.en} slow-cooks soup in a dorm-sized pot.`, `你们都把下厨当解压：你最拿手的是「${dish}」，${name.zh}能用宿舍小锅炖一晚上的汤。`)
          : t(`You both cook to unwind; ${name.en} slow-cooks soup in a dorm-sized pot.`, `你们都把下厨当解压，${name.zh}能用宿舍小锅炖一晚上的汤。`),
        basis,
      };
    }
    case 'music': {
      const now = str(answers.musicNowPlaying);
      return {
        text: now
          ? t(`You have ${now} on repeat; ${name.en} has been to 40 small shows this year. Swap playlists.`, `你最近在循环「${now}」，${name.zh}今年去了 40 场小型演出——见面交换歌单吧。`)
          : t(`Music is how you both decompress — trade one song each.`, '音乐是你们俩的解压方式，见面各交换一首歌。'),
        basis,
      };
    }
    case 'reading': {
      const book = str(answers.readingLastBook);
      return {
        text: book
          ? t(`You just finished ${book}; ${name.en} always has a paperback in their bag.`, `你刚读完《${book.replace(/[《》]/g, '')}》，${name.zh}的包里永远有一本书。`)
          : t(`You both read for fun — rare these days.`, '你们都会为了乐趣读书，这年头不多见了。'),
        basis,
      };
    }
    case 'travel': {
      const next = str(answers.travelNext);
      return {
        text: next
          ? t(`You’re manifesting ${next}; ${name.en} just came back from Dali with an undeveloped film roll.`, `你在许愿去「${next}」，${name.zh}刚从大理回来，还有一卷没冲洗的胶卷。`)
          : t(`You both plan trips around food and long walks.`, '你们旅行都是围着吃和走路来安排的。'),
        basis,
      };
    }
    case 'movies': {
      const watch = str(answers.moviesLastWatch);
      return {
        text: watch
          ? t(`Your last great watch was ${watch}; ${name.en} will have an opinion about its ending.`, `你最近最爱的是《${watch.replace(/[《》]/g, '')}》，${name.zh}一定对它的结局有话说。`)
          : t(`You’d both rather argue about a film ending than small-talk.`, '比起寒暄，你们都更愿意争论一部电影的结局。'),
        basis,
      };
    }
    case 'gym':
      return { text: t(`You both train in the morning — a run could be date two.`, '你们都习惯早上运动，第二次约会可以一起晨跑。'), basis };
    case 'gaming':
      return { text: t(`You both game to switch off — duo queue is a love language.`, '你们都靠打游戏放空，双排也是一种示好。'), basis };
    case 'art':
      return { text: t(`You both make things with your hands; bring a sketch.`, '你们都喜欢动手做东西，见面带一张速写吧。'), basis };
    default:
      return null;
  }
}

/** 2–3 "why you two" reasons. Server-side this is an LLM call grounded on both profiles (PRD §4.4). */
export function reasonsFor(partner: Partner, answers: Answers): Reason[] {
  const mine = Array.isArray(answers.hobbies) ? (answers.hobbies as string[]) : [];
  const shared = partner.hobbies.filter((h) => mine.includes(h));
  const out = shared.map((h) => hobbyReason(h, answers, partner)).filter((r): r is Reason => !!r);
  return [...out, ...partner.fallbackReasons].slice(0, 3);
}

export function sharedInterests(partner: Partner, answers: Answers): { label: T; shared: boolean }[] {
  const mine = Array.isArray(answers.hobbies) ? (answers.hobbies as string[]) : [];
  return [
    ...partner.hobbies.map((h) => ({ label: HOBBY_LABEL[h]!, shared: mine.includes(h) })).sort((a, b) => Number(b.shared) - Number(a.shared)),
    ...partner.extraTags.map((label) => ({ label, shared: false })),
  ];
}

export const DECLINE_REASONS: { id: string; label: T }[] = [
  { id: 'notMyType', label: t('Not my type', '不是我喜欢的类型') },
  { id: 'reasonsOff', label: t('The reasons don’t sound like me', '匹配理由不像我') },
  { id: 'tooFar', label: t('Too far / bad place', '地点太远或不合适') },
  { id: 'noTime', label: t('No time this week', '这周没时间') },
  { id: 'knowThem', label: t('I know them already', '我认识这个人') },
  { id: 'other', label: t('Something else', '其他原因') },
];

export const REPORT_CATEGORIES: { id: string; label: T }[] = [
  { id: 'harassment', label: t('Harassment or threats', '骚扰或威胁') },
  { id: 'scam', label: t('Asking for money / scam signs', '索要钱财、推销或诈骗迹象') },
  { id: 'fake', label: t('Photos or identity don’t match', '照片或身份不符') },
  { id: 'offline', label: t('Inappropriate behaviour in person', '线下不当行为') },
  { id: 'minor', label: t('May be under 18', '疑似未满 18 周岁') },
  { id: 'other', label: t('Something else', '其他') },
];

/** Demo answers used by the demo panel's "seed profile" (and when /home is opened with ?demo=1 before onboarding). */
export const SAMPLE_ANSWERS: Answers = {
  isStudent: 'yes',
  name: 'Alex',
  gender: 'man',
  expectedGender: ['women'],
  birthday: '2004-01-15',
  hobbies: ['cooking', 'music', 'reading'],
  cookingStyle: ['asian'],
  cookingSignatureDish: '番茄牛腩',
  musicSound: ['indie'],
  musicNowPlaying: '草东没有派对',
  readingGenres: ['litfic'],
  readingLastBook: '长安的荔枝',
  ethnicity: ['eastAsian'],
  expectedEthnicity: ['noPreference'],
  expectedAgeRange: [19, 24],
  height: { cm: 178, unit: 'cm' },
  heightRange: { any: true, min: 160, max: 180 },
  schoolEmail: 'alex@pku.edu.cn',
  emailVerify: '123456',
  photos: 0,
  location: { placeId: 'pku', mode: 'transit', minutes: 45 },
  relationshipLooking: ['serious'],
  matchingStyle: 'steady',
  schoolYear: 'junior',
  hearAbout: 'friend',
  dealbreakers: '',
  politics: 'apolitical',
  politicsImportance: 'none',
  religion: ['atheist'],
  religionImportance: 'none',
  firstMessage: '会做一桌菜，也会认真听你说话。',
  datingHelp: ['justDate'],
};
