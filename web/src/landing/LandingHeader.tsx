import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { pick, useLang, useQuerySuffix } from '../i18n/lang';
import { BRAND, HEADER } from './copy';
import { useContinuePath } from './auth';

export function LandingHeader() {
  const lang = useLang();
  const q = useQuerySuffix();
  const continuePath = useContinuePath();
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  return (
    <header className={`lp-header${scrolled ? ' is-scrolled' : ''}`}>
      <Link to={`/${q}`} className="lp-logo" lang={lang === 'zh' ? 'zh-CN' : 'en'}>
        {pick(BRAND, lang)}
      </Link>
      <p className="lp-ghost">
        <span className="lp-ghost-m">{pick(HEADER.ghostingMobile, lang)} </span>
        <span className="lp-ghost-d">{pick(HEADER.ghostingDesktop, lang)} </span>
        <a href="#faq">
          <span className="lp-ghost-m">{pick(HEADER.fixIt, lang)}</span>
          <span className="lp-ghost-d">{pick(HEADER.newNumber, lang)}</span>
        </a>
      </p>
      <nav className="lp-nav">
        {continuePath ? (
          <Link to={continuePath + q} className="lp-pill is-join is-continue">
            {pick(HEADER.continue, lang)}
          </Link>
        ) : (
          <>
            <Link to={`/login${q}`} className="lp-pill">
              {pick(HEADER.logIn, lang)}
            </Link>
            <Link to={`/login${q}`} className="lp-pill is-join">
              {pick(HEADER.joinNow, lang)}
            </Link>
          </>
        )}
      </nav>
    </header>
  );
}
