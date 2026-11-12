import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { Link } from 'react-router';
import { motion, useAnimationFrame, useReducedMotion } from 'motion/react';
import { pick, useLang, useQuerySuffix, type Lang } from '../i18n/lang';
import { HERO, HERO_MATCHES, HERO_TITLE, type HeroMatch } from './copy';
import { useContinuePath } from './auth';

/*
 * Timeline (ms, relative to T0 = intro start + 600ms, measured in docs/research/06):
 * letters 0…, phone rises at 1757, dove flies in 421→3842, thread events below.
 */
const INTRO_DELAY = 600;
const PHONE_UP = 1757;
const EVENTS = [421, 4351, 5326, 7129, 7741, 8963, 9630, 10450, 12291, 12913, 14960];
const FADE_AT = 17500;
const PERIOD = 17700;

declare global {
  interface Window {
    __heroT0?: number;
  }
}

type Item =
  | { key: string; kind: 'stamp' }
  | { key: string; kind: 'in' | 'out'; text: string; tail?: boolean }
  | { key: string; kind: 'typing' }
  | { key: string; kind: 'poster'; match: HeroMatch }
  | { key: string; kind: 'receipt'; text: string };

function buildThread(step: number, cycle: number, lang: Lang): Item[] {
  const m = HERO_MATCHES[cycle % HERO_MATCHES.length]!;
  const k = (s: string) => `${cycle}-${s}`;
  const items: Item[] = [];
  if (step >= 1) {
    items.push({ key: k('stamp'), kind: 'stamp' }, { key: k('first'), kind: 'in', text: pick(HERO.firstMsg, lang) });
  }
  if (step >= 2) items.push({ key: k('poster'), kind: 'poster', match: m });
  if (step === 2) items.push({ key: k('typing1'), kind: 'typing' });
  if (step >= 3) items.push({ key: k('intro'), kind: 'in', text: pick(m.intro, lang) });
  if (step >= 4) items.push({ key: k('reply'), kind: 'out', text: pick(m.reply, lang) });
  if (step >= 5 && step < 9) {
    items.push({ key: k('r1'), kind: 'receipt', text: pick(step >= 7 ? HERO.read : HERO.delivered, lang) });
  }
  if (step === 6 || step === 7) items.push({ key: k('typing2'), kind: 'typing' });
  if (step >= 8) items.push({ key: k('plan'), kind: 'in', text: pick(m.plan, lang) });
  if (step >= 9) items.push({ key: k('yes'), kind: 'out', text: pick(m.yes, lang) });
  if (step >= 10) {
    items.push({ key: k('r2'), kind: 'receipt', text: pick(step >= 11 ? HERO.read : HERO.delivered, lang) });
  }
  // iMessage draws a tail only on the last bubble of a same-side run.
  items.forEach((it, i) => {
    if (it.kind !== 'in' && it.kind !== 'out') return;
    const next = items[i + 1];
    const sameSide = next && (next.kind === it.kind || (it.kind === 'in' && (next.kind === 'poster' || next.kind === 'typing')));
    it.tail = !sameSide;
  });
  return items;
}

interface Clock {
  cycle: number;
  step: number;
  fading: boolean;
  up: boolean;
}

function clockAt(t: number): { clock: Clock; next: number } {
  if (t < 0) return { clock: { cycle: 0, step: 0, fading: false, up: false }, next: 0 };
  const cycle = Math.floor(t / PERIOD);
  const local = t - cycle * PERIOD;
  const step = EVENTS.filter((e) => e <= local).length;
  const fading = local >= FADE_AT;
  const bounds = [...EVENTS, FADE_AT, PERIOD].map((b) => cycle * PERIOD + b);
  if (t < PHONE_UP) bounds.push(PHONE_UP);
  const next = Math.min(...bounds.filter((b) => b > t));
  return { clock: { cycle, step, fading, up: t >= PHONE_UP }, next };
}

/* Dove orbit keyframes: k blends between "offscreen on `side`" and x (in u) around the title centre. */
interface DoveKey {
  t: number;
  k: number;
  side: -1 | 1;
  x: number;
  y: number;
  r: number;
  s: number;
}
const DOVE: DoveKey[] = [
  { t: 421, k: 1, side: -1, x: 0, y: -366, r: 0, s: 1 },
  { t: 2215, k: 0.957, side: -1, x: 0, y: -360, r: 0.7, s: 1 },
  { t: 2628, k: 0.746, side: -1, x: 0, y: -328, r: 2.5, s: 1 },
  { t: 3024, k: 0.451, side: -1, x: 0, y: -281, r: 3.3, s: 1 },
  { t: 3227, k: 0.294, side: -1, x: 0, y: -256, r: 3.2, s: 1.02 },
  { t: 3432, k: 0.154, side: -1, x: 0, y: -235, r: 2.8, s: 1.04 },
  { t: 3842, k: 0, side: -1, x: 7.6, y: -209, r: 1.4, s: 1.06 },
  { t: 4249, k: 0, side: -1, x: 20.9, y: -205, r: 0.2, s: 1.04 },
  { t: 6668, k: 0, side: -1, x: 72, y: -191, r: 0, s: 1 },
  { t: 10143, k: 0, side: -1, x: 15, y: -207, r: -0.4, s: 1 },
  { t: 13121, k: 0, side: -1, x: -30, y: -262, r: -7, s: 1.02 },
  { t: 15422, k: 0, side: 1, x: 17, y: 167, r: 11.7, s: 0.92 },
  { t: 16490, k: 0, side: 1, x: 40, y: 307, r: 0.1, s: 1 },
  { t: 18500, k: 1, side: 1, x: 40, y: 420, r: 0, s: 1 },
];
const DOVE_BEHIND_FROM = 14200;
const DOVE_LOOP_FROM = 1215;
const DOVE_LOOP_PERIOD = DOVE[DOVE.length - 1]!.t - DOVE_LOOP_FROM;
const DOVE_FRAMES = 17;

const easeInOut = (p: number) => 0.5 - Math.cos(Math.PI * p) / 2;

function doveAt(t: number) {
  const last = DOVE[DOVE.length - 1]!;
  const lt = t > last.t ? DOVE_LOOP_FROM + ((t - last.t) % DOVE_LOOP_PERIOD) : Math.max(t, DOVE[0]!.t);
  let i = 0;
  while (i < DOVE.length - 2 && DOVE[i + 1]!.t <= lt) i++;
  const a = DOVE[i]!;
  const b = DOVE[i + 1]!;
  const p = easeInOut(Math.min(1, Math.max(0, (lt - a.t) / (b.t - a.t))));
  const mix = (x: number, y: number) => x + (y - x) * p;
  const side = a.k > 0 ? a.side : b.side;
  return {
    k: mix(a.k, b.k),
    side,
    x: mix(a.x, b.x),
    y: mix(a.y, b.y),
    r: mix(a.r, b.r),
    s: mix(a.s, b.s),
    behind: lt >= DOVE_BEHIND_FROM,
    flying: lt < 3842 || lt > 15422,
  };
}

const heroUnit = () => Math.min(0.8, Math.max(0.45, window.innerHeight / 1600));

function Dove({ t0, reduced }: { t0: number | null; reduced: boolean }) {
  const orbit = useRef<HTMLDivElement>(null);
  const sprite = useRef<HTMLDivElement>(null);
  useAnimationFrame(() => {
    if (reduced || t0 === null || !orbit.current || !sprite.current) return;
    const now = performance.now();
    const d = doveAt(now - t0);
    const u = heroUnit();
    const w = 379 * u;
    const x = d.k * d.side * (window.innerWidth / 2 + w) + (1 - d.k) * d.x * u;
    const st = orbit.current.style;
    st.setProperty('--x', `${x.toFixed(1)}px`);
    st.setProperty('--y', `${(d.y * u).toFixed(1)}px`);
    st.setProperty('--r', `${d.r.toFixed(2)}deg`);
    st.setProperty('--s', d.s.toFixed(3));
    orbit.current.classList.toggle('is-behind', d.behind);
    const frame = Math.floor(now / (d.flying ? 41 : 70)) % DOVE_FRAMES;
    sprite.current.style.backgroundPositionX = `${((frame / (DOVE_FRAMES - 1)) * 100).toFixed(3)}%`;
  });
  return (
    <div ref={orbit} className="lp-dove-orbit" aria-hidden>
      <div ref={sprite} className="lp-dove" />
    </div>
  );
}

function Bubble({ item, lang }: { item: Item; lang: Lang }) {
  switch (item.kind) {
    case 'stamp':
      return (
        <div className="lp-stamp lp-enter-fade">
          {pick(HERO.stamp, lang)}
          <br />
          <b>{pick(HERO.stampTime, lang).split(' ')[0]}</b> {pick(HERO.stampTime, lang).split(' ').slice(1).join(' ')}
        </div>
      );
    case 'in':
    case 'out':
      return (
        <div className={`lp-msg is-${item.kind}${item.tail ? ' has-tail' : ''} ${item.kind === 'in' ? 'lp-enter-in' : 'lp-enter-out'}`}>
          {item.text}
        </div>
      );
    case 'typing':
      return (
        <div className="lp-msg is-in has-tail lp-typing lp-enter-in" aria-label="typing">
          <i />
          <i />
          <i />
        </div>
      );
    case 'poster':
      return (
        <div className="lp-poster lp-enter-in" style={{ background: item.match.tint }}>
          <div className="lp-poster-card">
            <small>{pick(HERO.posterKicker, lang)}</small>
            <b>{pick(item.match.name, lang)}</b>
            <i />
            <i />
            <i />
          </div>
          <div className="lp-poster-sticker" />
        </div>
      );
    case 'receipt':
      return (
        <div key={item.text} className="lp-receipt lp-enter-fade">
          {item.text}
        </div>
      );
  }
}

const ROW_CLASS: Record<Item['kind'], string> = {
  stamp: 'is-center',
  in: 'is-in',
  typing: 'is-in',
  poster: 'is-in',
  out: 'is-out',
  receipt: 'is-out',
};

export function Hero() {
  const lang = useLang();
  const q = useQuerySuffix();
  const continuePath = useContinuePath();
  const reduced = !!useReducedMotion();
  const [t0, setT0] = useState<number | null>(null);
  const [clock, setClock] = useState<Clock>({ cycle: 0, step: 0, fading: false, up: false });

  useEffect(() => {
    const start = performance.now() + INTRO_DELAY;
    window.__heroT0 = performance.timeOrigin + start;
    setT0(start);
    if (reduced) {
      setClock({ cycle: 0, step: EVENTS.length, fading: false, up: true });
      return;
    }
    let timer = 0;
    const tick = () => {
      const { clock: c, next } = clockAt(performance.now() - start);
      setClock(c);
      timer = window.setTimeout(tick, Math.max(0, next - (performance.now() - start)) + 4);
    };
    tick();
    return () => window.clearTimeout(timer);
  }, [reduced]);

  const items = buildThread(clock.step, clock.cycle, lang);
  let i = 0;
  const lines = HERO_TITLE[lang];

  return (
    <section className={`lp-hero${t0 !== null ? ' is-intro' : ''}`} aria-label={lines.join(' ')}>
      <div className="lp-rays" />
      <div className="lp-strip is-far" aria-hidden>
        {[0, 1, 2, 3].map((n) => (
          <img key={n} src="/landing/hero-far.jpg" alt="" draggable={false} />
        ))}
      </div>
      <h1 className="lp-title" lang={lang === 'zh' ? 'zh-CN' : 'en'} aria-label={lines.join(' ')}>
        {lines.map((line) => (
          <span key={line} className="lp-title-line" aria-hidden>
            {Array.from(line).map((ch) => {
              const n = i++;
              const style: CSSProperties = { animationDelay: `${-60 * n}ms, ${INTRO_DELAY + 28 * n}ms` };
              return (
                <span key={n} className="lp-ch" style={style}>
                  {ch === ' ' ? '\u00a0' : ch}
                </span>
              );
            })}
          </span>
        ))}
      </h1>
      <div className="lp-phone-slot" aria-hidden>
        <div className={`lp-phone${clock.up ? ' is-up' : ''}`}>
          <div className="lp-island" />
          <div className="lp-phone-head">
            <img src="/brand/pigeon-avatar.jpg" alt="" />
            <span>{pick(HERO.ditto, lang)}</span>
          </div>
          <div className={`lp-thread${clock.fading ? ' is-fading' : ''}`}>
            {items.map((it) => (
              <motion.div
                key={it.key}
                layout="position"
                transition={{ duration: 0.34, ease: [0.22, 1, 0.36, 1] }}
                className={`lp-row ${ROW_CLASS[it.kind]}`}
              >
                <Bubble item={it} lang={lang} />
              </motion.div>
            ))}
          </div>
        </div>
      </div>
      <div className="lp-strip is-campus" aria-hidden>
        {[0, 1, 2, 3].map((n) => (
          <img key={n} src="/landing/hero-campus.jpg" alt="" draggable={false} />
        ))}
      </div>
      <Dove t0={t0} reduced={reduced} />
      <div className="lp-strip is-river" aria-hidden>
        {[0, 1, 2, 3].map((n) => (
          <img key={n} src="/landing/hero-river.jpg" alt="" draggable={false} />
        ))}
      </div>
      <Link className="lp-hero-cta" to={(continuePath ?? '/login') + q}>
        {pick(continuePath ? HERO.continue : HERO.cta, lang)}
      </Link>
    </section>
  );
}
