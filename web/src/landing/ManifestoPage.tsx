import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { pick, useLang, useQuerySuffix, type Lang } from '../i18n/lang';
import { useContinuePath } from './auth';
import { BRAND } from './copy';
import { Footer } from './Footer';
import { LandingHeader } from './LandingHeader';
import { MANIFESTO_PAGE as M, type ManifestoLine } from './manifesto';
import { BG, Group } from './Sections';
import './landing.css';

function Line({ line, lang }: { line: ManifestoLine; lang: Lang }) {
  if ('before' in line) {
    return (
      <p>
        {pick(line.before, lang)}
        <span className="mf-em">{pick(line.em, lang)}</span>
        {pick(line.after, lang)}
      </p>
    );
  }
  return <p>{pick(line, lang)}</p>;
}

function Join() {
  const lang = useLang();
  const q = useQuerySuffix();
  const navigate = useNavigate();
  const continuePath = useContinuePath();
  const [phone, setPhone] = useState('');

  if (continuePath) {
    return (
      <div className="mf-join">
        <Link to={continuePath + q} className="mf-join-btn">
          {pick(M.continue, lang)}
        </Link>
      </div>
    );
  }
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    navigate(`/login${q}`, { state: { phone: phone.trim() } });
  };
  return (
    <form className="mf-join" onSubmit={onSubmit}>
      <input
        type="tel"
        inputMode="numeric"
        autoComplete="tel-national"
        maxLength={11}
        placeholder={pick(M.phone, lang)}
        aria-label={pick(M.phone, lang)}
        value={phone}
        onChange={(e) => setPhone(e.target.value.replace(/\D/g, ''))}
      />
      <button type="submit" className="mf-join-btn">
        {pick(M.join, lang)}
      </button>
    </form>
  );
}

export function ManifestoPage() {
  const lang = useLang();

  useEffect(() => {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.title = `${pick(M.docTitle, lang)} · ${pick(BRAND, lang)}`;
  }, [lang]);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  return (
    <div className="lp is-manifesto" lang={lang === 'zh' ? 'zh-CN' : 'en'}>
      <LandingHeader ghost={false} />
      <main>
        <Group bg={BG.manifesto}>
          <article className="mf-page">
            <h1>
              {pick(M.title[0], lang)}
              <br />
              {pick(M.title[1], lang)}
            </h1>
            {M.blocks.map((b, i) =>
              'h2' in b ? (
                <h2 key={i}>{pick(b.h2, lang)}</h2>
              ) : (
                <div key={i} className="mf-block">
                  {b.lines.map((l, j) => (
                    <Line key={j} line={l} lang={lang} />
                  ))}
                </div>
              ),
            )}
          </article>
        </Group>
        <Group bg={BG.manifestoCta} zig>
          <section className="mf-cta">
            {M.cta.map((c, i) => (
              <div key={i} className="mf-cta-title">
                {pick(c, lang)}
              </div>
            ))}
            <Join />
          </section>
        </Group>
      </main>
      <Footer />
    </div>
  );
}
