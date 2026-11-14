import { Fragment, useState, type CSSProperties, type ReactNode } from 'react';
import { Link } from 'react-router';
import { pick, useLang, useQuerySuffix, type Lang, type T } from '../i18n/lang';
import { CHIPS, COMPARISON, FAQ, MANIFESTO, MATCHMAKER, SAFETY, STAMP, STATS, STATS_NOTE, STEPS, TESTIMONIALS } from './copy';
import { useReveal } from './useReveal';

function Reveal({ children, className = '', style }: { children: ReactNode; className?: string; style?: CSSProperties }) {
  const ref = useReveal<HTMLDivElement>();
  return (
    <div ref={ref} className={`lp-reveal ${className}`} style={style}>
      {children}
    </div>
  );
}

/** Black tape heading; `hot` words are pink, `br` breaks the line before that part. */
function Chip({ parts, lang, hot = [], br, size }: { parts: readonly T[]; lang: Lang; hot?: number[]; br?: number; size?: 'sm' | 'md' }) {
  return (
    <h2 className={`lp-chip${size ? ` is-${size}` : ''}`}>
      {parts.map((p, i) => (
        <Fragment key={i}>
          {i === br && <br />}
          {hot.includes(i) ? <em>{pick(p, lang)}</em> : <span>{pick(p, lang)}</span>}
        </Fragment>
      ))}
    </h2>
  );
}

export function Group({ bg, zig, children, id }: { bg: string; zig?: boolean; children: ReactNode; id?: string }) {
  return (
    <div id={id} className={`lp-group${zig ? ' lp-zig' : ''}`}>
      <div className="lp-group-bg" aria-hidden>
        <div className={bg} />
      </div>
      {children}
    </div>
  );
}

export function HowItWorks() {
  const lang = useLang();
  return (
    <section className="lp-section">
      <Chip parts={CHIPS.how} lang={lang} hot={[1]} />
      <div className="lp-steps">
        {STEPS.map((s, i) => (
          <Reveal key={s.img} className="lp-step">
            <h3 className="lp-h3 lp-step-title">
              <span className="lp-badge" style={{ color: s.color }}>
                {i + 1}
              </span>
              {pick(s.title, lang)}
            </h3>
            <p className="lp-body">{pick(s.desc, lang)}</p>
            <img src={s.img} alt="" width={s.w} loading="lazy" />
          </Reveal>
        ))}
      </div>
    </section>
  );
}

export function RealDates() {
  const lang = useLang();
  return (
    <section className="lp-section">
      <Chip parts={CHIPS.real} lang={lang} hot={[0]} />
      <Reveal className="lp-collage">
        <img className="is-mobile" src="/landing/photos-mobile.jpg" alt="" loading="lazy" />
        <img className="is-desktop" src="/landing/photos-desktop.jpg" alt="" loading="lazy" />
        {STATS.map((s) => (
          <div key={s.tone} className={`lp-sticker is-${s.tone}`}>
            <b>{pick(s.big, lang)}</b>
            <span>{pick(s.small, lang)}</span>
          </div>
        ))}
      </Reveal>
      <span className="lp-collage-note">{pick(STATS_NOTE, lang)}</span>
    </section>
  );
}

export function Matchmaker() {
  const lang = useLang();
  return (
    <section className="lp-section">
      <Chip parts={CHIPS.matchmaker} lang={lang} hot={[1]} br={lang === 'en' ? 2 : undefined} />
      <div className="lp-match-grid">
        {MATCHMAKER.map((m) => (
          <Reveal key={m.img} className="lp-match-item" style={{ '--w': `${m.w}px`, '--mw': `${m.mw}px` } as CSSProperties}>
            <h3 className="lp-h3 is-lg">{pick(m.title, lang)}</h3>
            <img src={m.img} alt="" loading="lazy" />
          </Reveal>
        ))}
      </div>
    </section>
  );
}

export function GreatTimes() {
  const lang = useLang();
  const cards = [...TESTIMONIALS, ...TESTIMONIALS];
  return (
    <section className="lp-section">
      <h2 className="lp-chip">{pick(CHIPS.great, lang)}</h2>
      <div className="lp-marquee">
        <div className="lp-marquee-track">
          {cards.map((c, i) => (
            <figure key={i} className="lp-card" style={{ backgroundImage: `url(${c.img})`, margin: 0 }} aria-hidden={i >= TESTIMONIALS.length}>
              <figcaption className="lp-quote">
                <small>{pick(c.who, lang)}</small>
                <p>{pick(c.text, lang)}</p>
                <span className="lp-quote-icon">{c.icon}</span>
              </figcaption>
            </figure>
          ))}
        </div>
      </div>
    </section>
  );
}

function MessagesGlyph() {
  return (
    <svg viewBox="0 0 24 24" fill="#fff" aria-hidden>
      <path d="M12 3C6.5 3 2 6.6 2 11c0 2.5 1.4 4.7 3.6 6.2-.2 1.3-.9 2.6-1.9 3.5 2 0 3.8-.7 5.1-1.8 1 .3 2.1.4 3.2.4 5.5 0 10-3.6 10-8.1S17.5 3 12 3z" />
    </svg>
  );
}

export function Comparison() {
  const lang = useLang();
  return (
    <section className="lp-section">
      <div className="lp-tired">
        <h2 className="lp-chip is-md">{pick(CHIPS.tired, lang)}</h2>
        <h2 className="lp-chip is-md">{pick(CHIPS.forYou, lang)}</h2>
      </div>
      <div className="lp-compare">
        <Reveal className="lp-compare-col">
          <small>{pick(COMPARISON.dittoLabel, lang)}</small>
          <h3 className="lp-h3">{pick(COMPARISON.dittoTitle, lang)}</h3>
          <div className="lp-invite">
            <div className="lp-msgs-icon">
              <MessagesGlyph />
              <span className="lp-badge-dot">1</span>
            </div>
            <div className="lp-notif">
              <span className="lp-notif-avatar" />
              <div>
                <b>{pick(COMPARISON.notifTitle, lang)}</b>
                <p>{pick(COMPARISON.notifBody, lang)}</p>
              </div>
              <time>{pick(COMPARISON.now, lang)}</time>
            </div>
          </div>
        </Reveal>
        <Reveal className="lp-compare-col">
          <small>{pick(COMPARISON.otherLabel, lang)}</small>
          <h3 className="lp-h3">
            {pick(COMPARISON.otherTitle[0], lang)}
            <small>{pick(COMPARISON.otherTitle[1], lang)}</small>
            {pick(COMPARISON.otherTitle[2], lang)}
          </h3>
          <div className="lp-paper">
            <img src="/landing/paper.webp" alt="" loading="lazy" />
            <span className="lp-app is-1">
              {pick(COMPARISON.app1, lang)}
              <b>3,321</b>
            </span>
            <span className="lp-app is-2">
              {pick(COMPARISON.app2, lang)}
              <b>1,675</b>
            </span>
          </div>
        </Reveal>
      </div>
    </section>
  );
}

function VerifiedStamp({ lang }: { lang: Lang }) {
  const ring = pick(STAMP.ring, lang);
  return (
    <svg viewBox="0 0 140 140" width="132" height="132" aria-hidden style={{ rotate: '-8deg' }}>
      <defs>
        <path id="lp-ring" d="M70 70 m-47 0 a47 47 0 1 1 94 0 a47 47 0 1 1 -94 0" />
        <filter id="lp-ink">
          <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="2" seed="4" />
          <feColorMatrix values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 -2.2 1.7" />
          <feComposite in="SourceGraphic" operator="in" />
        </filter>
      </defs>
      <g fill="none" stroke="#ff4fb5" filter="url(#lp-ink)">
        <circle cx="70" cy="70" r="64" strokeWidth="5" />
        <circle cx="70" cy="70" r="34" strokeWidth="3" />
        <text fill="#ff4fb5" stroke="none" fontFamily="Arial Black, Arial, PingFang SC, sans-serif" fontWeight="900" fontSize="15" letterSpacing={lang === 'zh' ? 6 : 2.4}>
          <textPath href="#lp-ring" startOffset="0">
            {ring} ★ {ring} ★
          </textPath>
        </text>
        <path d="M70 84 C56 74 52 66 58 60 C62 56 68 58 70 63 C72 58 78 56 82 60 C88 66 84 74 70 84Z" fill="#ff4fb5" stroke="none" />
        <path d="M66 58 L70 46 L74 58 M68 52 H72" strokeWidth="2.4" />
      </g>
    </svg>
  );
}

export function Safety() {
  const lang = useLang();
  return (
    <section className="lp-section">
      <Chip parts={CHIPS.safe} lang={lang} hot={[1]} size="sm" />
      <div className="lp-safe-list">
        {SAFETY.map((s) => (
          <Reveal key={s.kind} className="lp-safe">
            <div className="lp-safe-media">
              {s.kind === 'stamp' ? (
                <VerifiedStamp lang={lang} />
              ) : (
                <img src={s.kind === 'photo' ? '/landing/safe-2.webp' : '/landing/safe-3.webp'} alt="" loading="lazy" />
              )}
            </div>
            <div className="lp-safe-text">
              <small>{pick(s.kicker, lang)}</small>
              <h3 className="lp-h3">{pick(s.title, lang)}</h3>
            </div>
          </Reveal>
        ))}
      </div>
    </section>
  );
}

function Chevron() {
  return (
    <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M5 7.5l5 5 5-5" />
    </svg>
  );
}

export function Faq() {
  const lang = useLang();
  const [open, setOpen] = useState<number | null>(null);
  return (
    <section className="lp-section" id="faq">
      <h2 className="lp-chip is-sm">{pick(CHIPS.faq, lang)}</h2>
      <div className="lp-faq">
        {FAQ.map((f, i) => (
          <div key={i} className={`lp-faq-item${open === i ? ' is-open' : ''}`}>
            <button type="button" className="lp-faq-q" aria-expanded={open === i} onClick={() => setOpen(open === i ? null : i)}>
              {pick(f.q, lang)}
              <Chevron />
            </button>
            <div className="lp-faq-a">
              <div>
                <p>{pick(f.a, lang)}</p>
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

export function Manifesto() {
  const lang = useLang();
  const q = useQuerySuffix();
  return (
    <section className="lp-section lp-manifesto">
      <span className="lp-mani-line">{pick(MANIFESTO.line, lang)}</span>
      <span className="lp-mani-script">{pick(MANIFESTO.script, lang)}</span>
      <span className="lp-mani-line">{pick(MANIFESTO.line, lang)}</span>
      <Link to={`/manifesto${q}`} className="lp-pill lp-mani-btn">
        {pick(MANIFESTO.cta, lang)}
      </Link>
    </section>
  );
}
