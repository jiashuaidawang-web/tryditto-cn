import { pick, t, type Lang, type T } from '../i18n/lang';
import { formatFtIn, cmToIn } from '../components/HeightRuler';
import { CUSTOM_PREFIX } from '../components/OptionList';
import { BRANCHES, HOBBIES, QUESTIONS, optionsOf, type QuestionId } from '../onboarding/questions';
import type { Answers, HeightRangeValue, HeightValue, Question } from '../onboarding/types';

/**
 * /edit-profile and /deep-info rows (13 §2.2, §5). Row labels are NOT the bubble questions (13 §2.2 要点 1),
 * so each row carries its own label. `field` is Ditto's `/onboarding/edit/<field>` suffix.
 */
export interface FieldRow {
  field: string;
  q: QuestionId;
  label: T;
  /** Shown only on one path (13 §2.2: work/education/budget are professional-only, 08 §对复刻的启发 5). */
  only?: 'student' | 'professional';
  /** Deep Info rows show the stored lower-case value as-is (13 §5: `doesn't matter`). */
  raw?: boolean;
}

export const ABOUT_ROWS: FieldRow[] = [
  { field: 'name', q: 'name', label: t('What’s your name?', '你的名字') },
  { field: 'birthday', q: 'birthday', label: t('When is your birthday?', '你的生日') },
  { field: 'gender', q: 'gender', label: t('What’s your gender?', '你的性别') },
  { field: 'ethnicity', q: 'ethnicity', label: t('What’s your ethnicity?', '你的族裔') },
  { field: 'height', q: 'height', label: t('How tall are you?', '你的身高') },
  { field: 'hobbyRanking', q: 'hobbies', label: t('Share your hobbies and interests', '你的爱好和兴趣') },
  { field: 'isStudent', q: 'isStudent', label: t('Are you currently a student?', '现在是学生吗？') },
  // 推断: Ditto's student profile was not captured; the year is the only student-only answer with an edit page.
  { field: 'schoolYear', q: 'schoolYear', label: t('What year are you in?', '你读几年级？'), only: 'student' },
  { field: 'occupation', q: 'occupation', label: t('What do you do for work?', '你做什么工作？'), only: 'professional' },
  { field: 'educationLevel', q: 'educationLevel', label: t('Highest level of education', '最高学历'), only: 'professional' },
  { field: 'referralSource', q: 'hearAbout', label: t('How did you hear about us?', '你是从哪儿知道我们的？') },
  { field: 'firstImpression', q: 'firstMessage', label: t("What's the first thing your match should know?", '想让对方第一时间知道你什么？') },
  { field: 'redFlags', q: 'dealbreakers', label: t('Dealbreakers: what should Ditto avoid?', '雷区：见一面要帮你避开什么？') },
];

export const TYPE_ROWS: FieldRow[] = [
  { field: 'intention', q: 'relationshipLooking', label: t('What are you looking for right now?', '你现在想找什么样的关系？') },
  { field: 'expectedGender', q: 'expectedGender', label: t('Who do you wanna date?', '你想和谁约会？') },
  { field: 'ageRange', q: 'expectedAgeRange', label: t('What age range would you like to date in?', '希望对方的年龄范围') },
  { field: 'expectedHeightRange', q: 'heightRange', label: t('How tall should your match be?', '希望对方的身高') },
  { field: 'expectedEthnicity', q: 'expectedEthnicity', label: t('What ethnicities are you attracted to?', '对族裔有偏好吗？') },
  { field: 'matchAccuracy', q: 'matchingStyle', label: t('How do you want Ditto to match you rn?', '希望见一面现在怎么帮你匹配？') },
  { field: 'firstDateBudget', q: 'firstDateBudget', label: t('Budget for a first date (per person)', '第一次约会预算（人均）'), only: 'professional' },
  {
    field: 'educationImportance',
    q: 'educationImportance',
    label: t("Does your match's education have to be at least yours?", '对方的学历需要不低于你吗？'),
    only: 'professional',
  },
];

export const DEEP_ROWS: FieldRow[] = [
  { field: 'religious', q: 'religion', label: t('Religion', '宗教信仰'), raw: true },
  { field: 'religionImportance', q: 'religionImportance', label: t('Religion importance', '信仰是否要一致'), raw: true },
  { field: 'politicalBeliefs', q: 'politics', label: t('Political beliefs', '政治倾向'), raw: true },
  { field: 'politicsImportance', q: 'politicsImportance', label: t('Politics importance', '政治观点是否要一致'), raw: true },
];

const ALL_ROWS = [...ABOUT_ROWS, ...TYPE_ROWS, ...DEEP_ROWS];

/** `/onboarding/edit/:field` accepts Ditto's field ids and our question ids. */
export function resolveField(field: string | undefined): { row: FieldRow; back: '/edit-profile' | '/deep-info' } | null {
  if (!field) return null;
  const row = ALL_ROWS.find((r) => r.field === field) ?? ALL_ROWS.find((r) => r.q === field);
  if (!row) return null;
  return { row, back: DEEP_ROWS.includes(row) ? '/deep-info' : '/edit-profile' };
}

export function rowsFor(rows: FieldRow[], answers: Answers, lang: Lang): FieldRow[] {
  const student = answers.isStudent === 'yes';
  return rows.filter((r) => {
    if (QUESTIONS[r.q].drop?.[lang]) return false;
    if (r.only === 'student') return student;
    if (r.only === 'professional') return !student;
    return true;
  });
}

const cap = (s: string) => (s ? s[0]!.toUpperCase() + s.slice(1) : s);
const custom = (v: string) => (v.startsWith(CUSTOM_PREFIX) ? v.slice(CUSTOM_PREFIX.length) : null);

function optionLabel(q: Question, id: string, answers: Answers, lang: Lang): string {
  const c = custom(id);
  if (c !== null) return c;
  const opt = optionsOf(q, { answers, lang }).find((o) => o.id === id);
  return opt ? pick(opt.label, lang) : id;
}

/** 13 §2.2: the profile shows the stored value (`Male`, not the pill text `Man`). */
const GENDER_STORED: Record<string, T> = {
  man: t('Male', '男生'),
  woman: t('Female', '女生'),
  nonbinary: t('Nonbinary', '非二元'),
  other: t('Other', '其他'),
};

const BRANCH_TEXT_PREFIX: Record<string, T> = {
  cooking: t('signature dish', '拿手菜'),
  music: t('streaming', '在听'),
  travel: t('next trip', '想去'),
  reading: t('last book', '最近读完'),
  gym: t('training for', '为了'),
  gaming: t('playing', '在玩'),
  movies: t('last watch', '最近看'),
  art: t('makes', '作品'),
};

/** 09 §10.3: `Gym — Lifting, Running; training for: test`; hobbies without branch answers show just the name. */
function formatHobbies(answers: Answers, lang: Lang): string {
  const picked = Array.isArray(answers.hobbies) ? (answers.hobbies as string[]) : [];
  return picked
    .map((id) => {
      const c = custom(id);
      if (c !== null) return c;
      const name = pick(HOBBIES.find((h) => h.id === id)?.label ?? t(id, id), lang);
      const branch = BRANCHES[id];
      if (!branch) return name;
      const [multiId, textId] = branch;
      const multi = Array.isArray(answers[multiId]) ? (answers[multiId] as string[]).map((v) => optionLabel(QUESTIONS[multiId], v, answers, lang)) : [];
      const text = typeof answers[textId] === 'string' ? (answers[textId] as string).trim() : '';
      if (!multi.length && !text) return name;
      const zh = lang === 'zh';
      const parts = [multi.join(zh ? '、' : ', '), text ? `${pick(BRANCH_TEXT_PREFIX[id]!, lang)}${zh ? '：' : ': '}${text}` : ''].filter(Boolean);
      return `${name} — ${parts.join(zh ? '；' : '; ')}`;
    })
    .join('\n');
}

export function formatAnswer(row: FieldRow, answers: Answers, lang: Lang): string {
  const q = QUESTIONS[row.q] as Question;
  const v = answers[row.q];
  const en = lang === 'en';
  const finish = (s: string) => (en && !row.raw ? cap(s) : s);
  if (row.q === 'hobbies') return formatHobbies(answers, lang);
  if (row.q === 'gender' && typeof v === 'string') return pick(GENDER_STORED[v] ?? t(v, v), lang);
  if (row.q === 'isStudent' && typeof v === 'string') return v === 'yes' ? pick(t('Yes', '是'), lang) : pick(t('No', '不是'), lang);
  switch (q.type) {
    case 'single':
      return typeof v === 'string' ? finish(optionLabel(q, v, answers, lang)) : '';
    case 'multi':
      return Array.isArray(v) ? finish((v as string[]).map((id) => optionLabel(q, id, answers, lang)).join(en ? ', ' : '、')) : '';
    case 'text':
    case 'longText':
      return typeof v === 'string' ? v : '';
    case 'date': {
      const m = typeof v === 'string' ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(v) : null;
      if (!m) return '';
      return en ? `${m[2]}/${m[3]}/${m[1]}` : `${m[1]}/${m[2]}/${m[3]}`;
    }
    case 'height': {
      const h = v as HeightValue | undefined;
      if (!h) return '';
      if (en && h.unit === 'ft') {
        const inch = cmToIn(h.cm);
        return `${Math.floor(inch / 12)}ft${inch % 12}in`;
      }
      return `${h.cm} cm`;
    }
    case 'heightRange': {
      const r = v as HeightRangeValue | undefined;
      if (!r) return '';
      if (r.any) return pick(t("I don't care about height", '身高无所谓'), lang);
      const ft = en && (answers.height as HeightValue | undefined)?.unit !== 'cm';
      return ft ? `${formatFtIn(cmToIn(r.min))}–${formatFtIn(cmToIn(r.max))}` : `${r.min}–${r.max} cm`;
    }
    case 'ageRange': {
      const r = Array.isArray(v) ? (v as number[]) : [18, 24];
      return `${r[0]}–${r[1]}`;
    }
    default:
      return '';
  }
}

/** aria-label `Edit <bubble question>` (13 §2.1). */
export function editAria(row: FieldRow, lang: Lang): string {
  const bubble = QUESTIONS[row.q].bubbles.at(-1) ?? QUESTIONS[row.q].bubbles[0]!;
  const text = pick(bubble, lang).replace(/\*\*/g, '');
  return lang === 'en' ? `Edit ${text}` : `编辑：${text}`;
}
