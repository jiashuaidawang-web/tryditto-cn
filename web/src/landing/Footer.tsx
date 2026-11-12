import { useState } from 'react';
import { Link } from 'react-router';
import { pick, useLang, useQuerySuffix, type T } from '../i18n/lang';
import { BRAND, CITIES, COLLEGES, FOOTER } from './copy';

const Arrow = () => (
  <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden>
    <path d="M5 11L11 5M6 5h5v5" />
  </svg>
);

function SocialIcons() {
  return (
    <div className="lp-social">
      <a href="#" aria-label="Instagram">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
          <rect x="3" y="3" width="18" height="18" rx="5" />
          <circle cx="12" cy="12" r="4" />
          <circle cx="17.3" cy="6.7" r="1" fill="currentColor" stroke="none" />
        </svg>
      </a>
      <a href="#" aria-label="TikTok">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
          <path d="M16.6 3c.3 2.1 1.6 3.6 3.6 3.8v3a7 7 0 0 1-3.6-1.1v6.4A5.9 5.9 0 1 1 10.7 9v3.1a2.8 2.8 0 1 0 2.8 2.8V3h3.1z" />
        </svg>
      </a>
      <a href="#" aria-label="X">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden>
          <path d="M17.8 3h3.1l-6.8 7.8 8 10.2h-6.3l-4.9-6.4L5.3 21H2.2l7.3-8.3L1.8 3h6.4l4.4 5.9L17.8 3zm-1.1 16.2h1.7L7.2 4.7H5.3l11.4 14.5z" />
        </svg>
      </a>
    </div>
  );
}

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
      <div className="lp-footer-grid">
        <nav className="lp-res">
          <h4>{pick(FOOTER.resources, lang)}</h4>
          <ul>
            <li>
              <a href="#">
                {pick(FOOTER.careers, lang)}
                <Arrow />
              </a>
            </li>
            <li>
              <a href="#">
                {pick(FOOTER.manifesto, lang)}
                <Arrow />
              </a>
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
        <SocialIcons />
        <div className="lp-legal">
          <span>{pick(FOOTER.copyright, lang)}</span>
          <nav>
            <a href="#">{pick(FOOTER.terms, lang)}</a>
            <a href="#">{pick(FOOTER.privacy, lang)}</a>
            <a href="#">{pick(FOOTER.cookies, lang)}</a>
          </nav>
        </div>
      </div>
    </footer>
  );
}
