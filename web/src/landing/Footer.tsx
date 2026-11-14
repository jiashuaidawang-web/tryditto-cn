import { useState } from 'react';
import { Link } from 'react-router';
import { pick, useLang, useQuerySuffix, type T } from '../i18n/lang';
import { BRAND, CITIES, COLLEGES, FOOTER } from './copy';

const Arrow = () => (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden>
    <path d="M5 11L11 5M6 5h5v5" />
  </svg>
);

function Expandable({ label, items }: { label: T; items: T[] }) {
  const lang = useLang();
  const [open, setOpen] = useState(false);
  return (
    <li>
      <button type="button" aria-expanded={open} onClick={() => setOpen(!open)}>
        {pick(label, lang)}
        <Arrow />
      </button>
      {open && (
        <div className="lp-res-list">
          {items.map((c) => (
            <span key={c.en}>{pick(c, lang)}</span>
          ))}
        </div>
      )}
    </li>
  );
}

export function Footer() {
  const lang = useLang();
  const q = useQuerySuffix();
  return (
    <footer className="lp-footer">
      <div className="lp-grain" aria-hidden />
      <div className="lp-footer-grid">
        <nav className="lp-res">
          <h4>{pick(FOOTER.resources, lang)}</h4>
          <ul>
            <li>
              <Link to={`/manifesto${q}`}>
                {pick(FOOTER.manifesto, lang)}
                <Arrow />
              </Link>
            </li>
            <Expandable label={FOOTER.byCity} items={CITIES} />
            <Expandable label={FOOTER.byCollege} items={COLLEGES} />
          </ul>
        </nav>
        <div>
          <span className="lp-brand-bubble">{pick(FOOTER.bubble, lang)}</span>
          <Link to={`/${q}`} className="lp-logo" lang={lang === 'zh' ? 'zh-CN' : 'en'}>
            {pick(BRAND, lang)}
          </Link>
        </div>
      </div>
      <div className="lp-bottom">
        <div className="lp-legal">
          <span>{pick(FOOTER.copyright, lang)}</span>
          <nav>
            <Link to={`/terms${q}`}>{pick(FOOTER.terms, lang)}</Link>
            <Link to={`/privacy${q}`}>{pick(FOOTER.privacy, lang)}</Link>
            <Link to={`/cookies${q}`}>{pick(FOOTER.cookies, lang)}</Link>
          </nav>
        </div>
      </div>
    </footer>
  );
}
