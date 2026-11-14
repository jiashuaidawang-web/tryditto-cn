import { useEffect, type ReactNode } from 'react';
import { Link } from 'react-router';
import { pick, useLang, useQuerySuffix } from '../i18n/lang';
import { BRAND } from './copy';
import { LEGAL, LEGAL_UI, type LegalKind } from './legal';
import './landing.css';

function Frame({ title, children }: { title: string; children: ReactNode }) {
  const lang = useLang();
  const q = useQuerySuffix();

  useEffect(() => {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.title = `${title} · ${pick(BRAND, lang)}`;
  }, [lang, title]);

  return (
    <div className="lp lg-root" lang={lang === 'zh' ? 'zh-CN' : 'en'}>
      <div className="lg-scenery" aria-hidden />
      <div className="lg-shell">
        <div className="lg-frame">
          <div className="ditto-bg" aria-hidden />
          <Link to={`/${q}`} className="lg-back" aria-label={pick(LEGAL_UI.back, lang)}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M15 5l-7 7 7 7" />
            </svg>
          </Link>
          <div className="lg-logo">
            <span className="lp-logo" style={{ fontSize: 34 }}>
              {pick(BRAND, lang)}
            </span>
          </div>
          {children}
        </div>
      </div>
    </div>
  );
}

export function LegalPage({ kind }: { kind: LegalKind }) {
  const lang = useLang();
  const doc = LEGAL[kind];
  return (
    <Frame title={pick(doc.title, lang)}>
      <article className="lg-card lg-doc">
        <h1>{pick(doc.title, lang)}</h1>
        <p className="lg-doc-meta">
          <span className="lg-demo">{pick(LEGAL_UI.draft, lang)}</span>
          <span>{pick(LEGAL_UI.updated, lang)}</span>
        </p>
        {doc.sections.map((s) => (
          <section key={s.h.en}>
            <h2>{pick(s.h, lang)}</h2>
            {s.p.map((p) => (
              <p key={p.en}>{pick(p, lang)}</p>
            ))}
          </section>
        ))}
      </article>
    </Frame>
  );
}

export function NotFoundPage() {
  const lang = useLang();
  const q = useQuerySuffix();
  return (
    <Frame title={pick(LEGAL_UI.notFoundTitle, lang)}>
      <div className="lg-card lg-404">
        <h1>{pick(LEGAL_UI.notFoundTitle, lang)}</h1>
        <p>{pick(LEGAL_UI.notFoundBody, lang)}</p>
        <Link to={`/${q}`} className="lg-submit">
          {pick(LEGAL_UI.home, lang)}
        </Link>
      </div>
    </Frame>
  );
}
