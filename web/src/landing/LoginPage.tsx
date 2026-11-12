import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { pick, useLang, useQuerySuffix } from '../i18n/lang';
import { useOnboarding } from '../onboarding/store';
import { BRAND, LOGIN } from './copy';
import { DEMO_CODE, isValidCode, isValidPhone, login, sendCode } from './auth';
import './landing.css';

type Msg = { tone: 'error' | 'ok'; text: string } | null;

export function LoginPage() {
  const lang = useLang();
  const q = useQuerySuffix();
  const navigate = useNavigate();
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [agree, setAgree] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [sentOnce, setSentOnce] = useState(false);
  const [sending, setSending] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [msg, setMsg] = useState<Msg>(null);
  const [bad, setBad] = useState<'phone' | 'code' | null>(null);
  const codeRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.title = `${pick(LOGIN.title, lang)} · ${pick(BRAND, lang)}`;
  }, [lang]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const id = window.setTimeout(() => setCooldown((c) => c - 1), 1000);
    return () => window.clearTimeout(id);
  }, [cooldown]);

  const fail = (field: 'phone' | 'code' | null, text: string) => {
    setBad(field);
    setMsg({ tone: 'error', text });
  };

  const onSend = async () => {
    if (!isValidPhone(phone)) return fail('phone', pick(LOGIN.errPhone, lang));
    setSending(true);
    setMsg(null);
    setBad(null);
    const res = await sendCode(phone);
    setSending(false);
    if (!res.ok) return fail('phone', pick(LOGIN.errPhone, lang));
    setCooldown(res.cooldown);
    setSentOnce(true);
    setMsg({ tone: 'ok', text: pick(LOGIN.sent, lang) });
    codeRef.current?.focus();
  };

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!isValidPhone(phone)) return fail('phone', pick(LOGIN.errPhone, lang));
    if (!isValidCode(code)) return fail('code', pick(LOGIN.errCode, lang));
    if (!agree) return fail(null, pick(LOGIN.errAgree, lang));
    setSubmitting(true);
    setMsg(null);
    setBad(null);
    const res = await login(phone, code);
    setSubmitting(false);
    if (!res.ok) {
      if (res.error === 'phone') return fail('phone', pick(LOGIN.errPhone, lang));
      return fail('code', pick(res.error === 'wrong' ? LOGIN.errWrong : LOGIN.errCode, lang));
    }
    navigate((useOnboarding.getState().completed ? '/home' : '/onboarding') + q, { replace: true });
  };

  const sendLabel = sending ? pick(LOGIN.sending, lang) : cooldown > 0 ? `${cooldown}s` : pick(sentOnce ? LOGIN.resend : LOGIN.sendCode, lang);

  return (
    <div className="lp lg-root" lang={lang === 'zh' ? 'zh-CN' : 'en'}>
      <div className="lg-scenery" aria-hidden />
      <div className="lg-shell">
        <div className="lg-frame">
          <div className="ditto-bg" aria-hidden />
          <Link to={`/${q}`} className="lg-back" aria-label={pick(LOGIN.back, lang)}>
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
              <path d="M15 5l-7 7 7 7" />
            </svg>
          </Link>
          <div className="lg-logo">
            <span className="lp-logo" style={{ fontSize: 34 }}>
              {pick(BRAND, lang)}
            </span>
          </div>
          <form className="lg-card" onSubmit={onSubmit} noValidate>
            <h1>{pick(LOGIN.title, lang)}</h1>
            <label className={`lg-field${bad === 'phone' ? ' is-error' : ''}`}>
              <span className="lg-cc">
                <span aria-hidden>🇨🇳</span>+86
              </span>
              <input
                className="lg-input"
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                placeholder={pick(LOGIN.phone, lang)}
                aria-label={pick(LOGIN.phone, lang)}
                maxLength={11}
                value={phone}
                onChange={(e) => {
                  setPhone(e.target.value.replace(/\D/g, '').slice(0, 11));
                  if (bad === 'phone') setBad(null);
                }}
              />
              <button type="button" className="lg-send" disabled={sending || cooldown > 0} onClick={onSend}>
                {sendLabel}
              </button>
            </label>
            <label className={`lg-field${bad === 'code' ? ' is-error' : ''}`}>
              <input
                ref={codeRef}
                className="lg-input"
                type="text"
                inputMode="numeric"
                autoComplete="one-time-code"
                placeholder={pick(LOGIN.code, lang)}
                aria-label={pick(LOGIN.code, lang)}
                maxLength={6}
                value={code}
                onChange={(e) => {
                  setCode(e.target.value.replace(/\D/g, '').slice(0, 6));
                  if (bad === 'code') setBad(null);
                }}
              />
            </label>
            <label className="lg-agree">
              <input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
              <span>
                {pick(LOGIN.agreePrefix, lang)}
                <a href="#" onClick={(e) => e.stopPropagation()}>
                  {pick(LOGIN.terms, lang)}
                </a>
                {pick(LOGIN.and, lang)}
                <a href="#" onClick={(e) => e.stopPropagation()}>
                  {pick(LOGIN.privacy, lang)}
                </a>
              </span>
            </label>
            <button type="submit" className="lg-submit" disabled={submitting}>
              {submitting ? pick(LOGIN.verifying, lang) : pick(LOGIN.submit, lang)}
            </button>
            <p className={`lg-msg${msg ? ` is-${msg.tone}` : ''}`} role="status">
              {msg?.text}
            </p>
            <p style={{ textAlign: 'center', margin: 0 }}>
              <span className="lg-demo">
                {pick(LOGIN.demo, lang)}
                {DEMO_CODE}
              </span>
            </p>
          </form>
          <p className="lg-foot">{pick(LOGIN.footer, lang)}</p>
        </div>
      </div>
    </div>
  );
}
