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
  b?: number;
}
/* From 3842 on: tryditto.com `.dove-orbit` --x/--y sampled every 250 ms at 390×844, px ÷ (844/1600), shifted +674 ms to align arrival. */
const DOVE: DoveKey[] = [
  { t: 421, k: 1, side: -1, x: 0, y: -366, r: 0, s: 1 },
  { t: 2215, k: 0.957, side: -1, x: 0, y: -360, r: 0.7, s: 1 },
  { t: 2628, k: 0.746, side: -1, x: 0, y: -328, r: 2.5, s: 1 },
  { t: 3024, k: 0.451, side: -1, x: 0, y: -281, r: 3.3, s: 1 },
  { t: 3227, k: 0.294, side: -1, x: 0, y: -256, r: 3.2, s: 1.02 },
  { t: 3432, k: 0.154, side: -1, x: 0, y: -235, r: 2.8, s: 1.04 },
  { t: 3842, k: 0, side: -1, x: -17.4, y: -195.3, r: 0.11, s: 1.063 },
  { t: 5120, k: 0, side: -1, x: 2.3, y: -211.4, r: -0.13, s: 1.054 },
  { t: 6648, k: 0, side: -1, x: 73.2, y: -185.2, r: 0.63, s: 1.065 },
  { t: 7156, k: 0, side: -1, x: 83.0, y: -179.5, r: 0.16, s: 1.067 },
  { t: 8928, k: 0, side: -1, x: 18.2, y: -178.8, r: -0.25, s: 1.071 },
  { t: 10969, k: 0, side: -1, x: 37.0, y: -192.4, r: 0.05, s: 1.066 },
  { t: 11477, k: 0, side: -1, x: 45.5, y: -272.8, r: -5.78, s: 1.008 },
  { t: 11984, k: 0, side: -1, x: 49.9, y: -333.8, r: -0.65, s: 1 },
  { t: 12490, k: 0, side: -1, x: 41.1, y: -237.2, r: 7.09, s: 0.967, b: 0.973 },
  { t: 12995, k: 0, side: -1, x: 26.9, y: -33.0, r: 11.59, s: 0.882, b: 0.902 },
  { t: 13512, k: 0, side: -1, x: 13.3, y: 190.9, r: 10.09, s: 0.929, b: 0.941 },
  { t: 14024, k: 0, side: -1, x: 8.3, y: 300.3, r: 3.71, s: 1 },
  { t: 14788, k: 0, side: -1, x: 31.7, y: 305.6, r: 0.13, s: 1 },
  { t: 15637, k: 0, side: -1, x: 67.3, y: 305.6, r: -0.06, s: 1 },
  { t: 16143, k: 0, side: -1, x: 62.6, y: 264.5, r: -3.56, s: 1.014 },
  { t: 16932, k: 0, side: -1, x: 33.2, y: 37.9, r: -8.96, s: 1.118 },
  { t: 17694, k: 0, side: -1, x: 1.5, y: -212.5, r: -7.89, s: 1.051 },
  { t: 18472, k: 0, side: -1, x: -10.8, y: -328.0, r: -1.17, s: 1 },
  { t: 19512, k: 0, side: -1, x: -0.4, y: -272.6, r: 2.27, s: 1.004 },
  { t: 21039, k: 0, side: -1, x: 8.0, y: -175.7, r: 0.32, s: 1.073 },
  { t: 21292, k: 0, side: -1, x: 5.1, y: -179.1, r: -0.55, s: 1.071 },
  { t: 21802, k: 0, side: -1, x: 1.7, y: -295.0, r: -5.61, s: 1 },
  { t: 22312, k: 0, side: -1, x: -1.7, y: -151.3, r: 12.29, s: 1.086 },
  { t: 22824, k: 0, side: -1, x: -4.4, y: 221.6, r: 13.98, s: 1.05 },
  { t: 23372, k: 0, side: -1, x: -2.7, y: 316.8, r: 0.88, s: 1 },
  { t: 24135, k: 0, side: -1, x: 2.7, y: 317.9, r: -0.71, s: 1 },
  { t: 24640, k: 0, side: -1, x: 17.3, y: 198.1, r: -8.52, s: 1.066 },
  { t: 25147, k: 0, side: -1, x: 41.7, y: -38.1, r: -12.69, s: 1.117 },
  { t: 25909, k: 0, side: -1, x: 66.5, y: -313.9, r: -6.35, s: 1 },
  { t: 26418, k: 0, side: -1, x: 65.2, y: -326.6, r: 0.88, s: 1 },
  { t: 27431, k: 0, side: -1, x: 65.6, y: -202.7, r: 2.94, s: 1.059 },
  { t: 27940, k: 0, side: -1, x: 67.3, y: -184.3, r: 0.37, s: 1.068 },
  { t: 30224, k: 0, side: -1, x: 13.1, y: -171.2, r: 0.02, s: 1.074 },
];
/* Only the first dive drops below the title (`.behind`); the dove never goes under the phone. */
const DOVE_BEHIND = [11984, 15774] as const;
const DOVE_LOOP_FROM = 8928;
const DOVE_LOOP_PERIOD = DOVE[DOVE.length - 1]!.t - DOVE_LOOP_FROM;
const DOVE_FRAMES = 17;

const catmull = (p0: number, p1: number, p2: number, p3: number, p: number) =>
  0.5 * (2 * p1 + (p2 - p0) * p + (2 * p0 - 5 * p1 + 4 * p2 - p3) * p * p + (3 * p1 - p0 - 3 * p2 + p3) * p * p * p);

function doveAt(t: number) {
  const last = DOVE[DOVE.length - 1]!;
  const lt = t > last.t ? DOVE_LOOP_FROM + ((t - last.t) % DOVE_LOOP_PERIOD) : Math.max(t, DOVE[0]!.t);
  let i = 0;
  while (i < DOVE.length - 2 && DOVE[i + 1]!.t <= lt) i++;
  const a = DOVE[i]!;
  const b = DOVE[i + 1]!;
  const a0 = DOVE[i - 1] ?? a;
  const b1 = DOVE[i + 2] ?? b;
  const p = Math.min(1, Math.max(0, (lt - a.t) / (b.t - a.t)));
  const at = (f: (k: DoveKey) => number) => catmull(f(a0), f(a), f(b), f(b1), p);
  const y = at((k) => k.y);
  return {
    k: Math.min(1, Math.max(0, at((k) => k.k))),
    side: a.side,
    x: at((k) => k.x),
    y,
    r: at((k) => k.r),
    s: at((k) => k.s),
    b: at((k) => k.b ?? 1),
    behind: lt >= DOVE_BEHIND[0] && lt < DOVE_BEHIND[1],
    flying: lt < 3842 || Math.abs(b.y - a.y) / (b.t - a.t) > 0.05,
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
    st.setProperty('--b', d.b.toFixed(3));
    orbit.current.classList.toggle('is-behind', d.behind);
    const frame = Math.floor(now / (d.flying ? 41 : 70)) % DOVE_FRAMES;
    const framePos = `${((frame / (DOVE_FRAMES - 1)) * 100).toFixed(3)}%`;
    sprite.current.style.backgroundPositionX = framePos;

    const shade = document.querySelector<HTMLElement>('.lp-phone-shade');
    const phone = shade?.parentElement;
    if (shade && phone) {
      const o = orbit.current.getBoundingClientRect();
      const p = phone.getBoundingClientRect();
      const fw = orbit.current.offsetWidth * d.s;
      const fh = orbit.current.offsetHeight * d.s;
      const cx = o.x + o.width / 2 - p.x + 6 * u;
      const cy = o.y + o.height / 2 - p.y + 14 * u;
      shade.style.width = `${fw.toFixed(1)}px`;
      shade.style.height = `${fh.toFixed(1)}px`;
      shade.style.transform = `translate(${(cx - fw / 2).toFixed(1)}px, ${(cy - fh / 2).toFixed(1)}px) rotate(${d.r.toFixed(2)}deg)`;
      shade.style.setProperty('mask-position', `${framePos} 0`);
      shade.style.setProperty('-webkit-mask-position', `${framePos} 0`);
    }
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
        <img
          className="lp-poster lp-enter-in"
          src={`/landing/cards/${item.match.card}-${lang}.jpg`}
          alt={`${pick(HERO.posterKicker, lang)} ${pick(item.match.name, lang)}`}
          draggable={false}
        />
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

  useEffect(() => {
    for (const m of HERO_MATCHES) new Image().src = `/landing/cards/${m.card}-${lang}.jpg`;
  }, [lang]);

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
      <div className="lp-strip is-campus is-back" aria-hidden>
        {[0, 1, 2, 3].map((n) => (
          <img key={n} src="/landing/hero-campus.jpg" alt="" draggable={false} />
        ))}
      </div>
      <div className="lp-phone-slot" aria-hidden>
        <div className={`lp-phone${clock.up ? ' is-up' : ''}`}>
          <div className="lp-island" />
          <div className="lp-phone-shade" />
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
          <img key={n} src="/landing/hero-campus-front.png" alt="" draggable={false} />
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
