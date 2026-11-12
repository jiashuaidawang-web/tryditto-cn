import type { Lang } from '../i18n/lang';

/** Product clock is pinned to Asia/Shanghai (PRD §4.1). UTC+8, no DST. */
const OFFSET_MS = 8 * 3600_000;

interface LocalParts {
  y: number;
  m: number;
  d: number;
  dow: number;
  minutes: number;
}

function local(at: Date): LocalParts {
  const s = new Date(at.getTime() + OFFSET_MS);
  return { y: s.getUTCFullYear(), m: s.getUTCMonth(), d: s.getUTCDate(), dow: s.getUTCDay(), minutes: s.getUTCHours() * 60 + s.getUTCMinutes() };
}

/** UTC instant of a Shanghai wall-clock time `daysAhead` days after `from`'s Shanghai date. */
function at(from: Date, daysAhead: number, hh: number, mm: number): Date {
  const p = local(from);
  return new Date(Date.UTC(p.y, p.m, p.d + daysAhead, hh, mm) - OFFSET_MS);
}

/** Next weekly drop: Wednesday 19:00 Shanghai (01 §4.2, PRD §4.1). */
export function nextDrop(now = new Date()): Date {
  const p = local(now);
  let days = (3 - p.dow + 7) % 7;
  if (days === 0 && p.minutes >= 19 * 60) days = 7;
  return at(now, days, 19, 0);
}

/** Response deadline after a drop: Thursday 22:00 (PRD §4.1). */
export function respondBy(drop: Date): Date {
  return at(drop, 1, 22, 0);
}

/** Date slots offered in the poster: Fri 18:30, Sat 15:30, Sun 12:00 after `from` (PRD §4.1 slot grid). */
export function proposedSlots(from = new Date()): Date[] {
  const p = local(from);
  const toFri = ((5 - p.dow + 7) % 7) || 7;
  return [at(from, toFri, 18, 30), at(from, toFri + 1, 15, 30), at(from, toFri + 2, 12, 0)];
}

export function countdownParts(target: Date, now = new Date()) {
  const ms = Math.max(0, target.getTime() - now.getTime());
  const s = Math.floor(ms / 1000);
  return { d: Math.floor(s / 86400), h: Math.floor((s % 86400) / 3600), m: Math.floor((s % 3600) / 60), s: s % 60 };
}

export function formatCountdown(target: Date, lang: Lang, now = new Date()): string {
  const { d, h, m, s } = countdownParts(target, now);
  const pad = (n: number) => String(n).padStart(2, '0');
  if (lang === 'zh') return d > 0 ? `${d} 天 ${h} 小时 ${pad(m)} 分` : `${h} 小时 ${pad(m)} 分 ${pad(s)} 秒`;
  return d > 0 ? `${d}d ${h}h ${pad(m)}m` : `${h}h ${pad(m)}m ${pad(s)}s`;
}

const WEEK_ZH = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const WEEK_EN = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export function formatSlot(iso: string, lang: Lang): { day: string; time: string } {
  const p = local(new Date(iso));
  const hh = Math.floor(p.minutes / 60);
  const mm = String(p.minutes % 60).padStart(2, '0');
  if (lang === 'zh') return { day: `${WEEK_ZH[p.dow]} ${p.m + 1}月${p.d}日`, time: `${hh}:${mm}` };
  const h12 = hh % 12 || 12;
  return { day: `${WEEK_EN[p.dow]} ${p.m + 1}/${p.d}`, time: `${h12}:${mm}${hh < 12 ? 'am' : 'pm'}` };
}

export function formatDropLabel(drop: Date, lang: Lang): string {
  const p = local(drop);
  return lang === 'zh' ? `${WEEK_ZH[p.dow]} ${p.m + 1}月${p.d}日 19:00` : `Wed ${p.m + 1}/${p.d}, 7pm`;
}
