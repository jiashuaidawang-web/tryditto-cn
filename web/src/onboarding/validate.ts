import type { FlowContext, HeightRangeValue, HeightValue, Question } from './types';

const SCHOOL_EMAIL = {
  en: /^[^\s@]+@[^\s@]+\.edu$/i,
  zh: /^[^\s@]+@[^\s@]+\.edu(\.cn)?$/i,
};
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** 'YYYY-MM-DD' → Date if it is a real calendar date. */
export function parseIsoDate(value: unknown): Date | null {
  if (typeof value !== 'string') return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return null;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const date = new Date(y, mo - 1, d);
  if (date.getFullYear() !== y || date.getMonth() !== mo - 1 || date.getDate() !== d) return null;
  if (y < 1900 || date > new Date()) return null;
  return date;
}

export function ageOn(date: Date, now = new Date()): number {
  let age = now.getFullYear() - date.getFullYear();
  const m = now.getMonth() - date.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < date.getDate())) age -= 1;
  return age;
}

export function isValidEmail(kind: 'school' | 'personal', value: string, lang: 'en' | 'zh'): boolean {
  const v = value.trim();
  return kind === 'school' ? SCHOOL_EMAIL[lang].test(v) : EMAIL.test(v);
}

export function isAnswered(question: Question, value: unknown, ctx: FlowContext): boolean {
  switch (question.type) {
    case 'single':
      return typeof value === 'string' && value.length > 0;
    case 'multi':
      return Array.isArray(value) && value.length > 0;
    case 'text':
      return typeof value === 'string' && value.trim().length > 0;
    case 'longText':
      return question.optional ? true : typeof value === 'string' && value.trim().length > 0;
    case 'date':
      return parseIsoDate(value) !== null;
    case 'email':
      return typeof value === 'string' && isValidEmail(question.emailKind ?? 'personal', value, ctx.lang);
    case 'ageRange':
      // The default 18–24 is submittable as-is.
      return value === undefined || (Array.isArray(value) && value.length === 2);
    case 'height':
      return typeof (value as HeightValue | undefined)?.cm === 'number';
    case 'heightRange': {
      const v = value as HeightRangeValue | undefined;
      return !!v && (v.any || (typeof v.min === 'number' && typeof v.max === 'number'));
    }
    case 'photos':
      return typeof value === 'number' && value >= 2;
    case 'interstitial':
      return true;
    case 'code':
      return typeof value === 'string' && /^\d{6}$/.test(value);
    case 'location':
      return typeof (value as { placeId?: unknown } | undefined)?.placeId === 'string';
  }
}
