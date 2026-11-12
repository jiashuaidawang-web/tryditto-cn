import { Link } from 'react-router';
import { AppShell } from '../components/AppShell';
import { pick, t, useLang, useQuerySuffix } from '../i18n/lang';
import { useOnboarding } from '../onboarding/store';

const COPY = {
  title: t('Ditto is finding you a date!', '见一面正在帮你找约会对象！'),
  done: t('Onboarding saved. Email verification comes next (not built yet).', '入驻信息已保存，下一步邮箱验证（待实现）。'),
  start: t('Start onboarding', '开始入驻'),
  resume: t('Continue onboarding', '继续入驻'),
  reset: t('Reset local progress', '清空本地进度'),
};

/** Placeholder home until the logged-in shell is built. */
export function Home() {
  const lang = useLang();
  const suffix = useQuerySuffix();
  const { completed, furthestId, reset } = useOnboarding();
  return (
    <AppShell>
      <div className="absolute inset-0 flex flex-col items-center justify-center gap-5 px-8 text-center">
        <h1 className="text-[30px] font-bold leading-9">{pick(COPY.title, lang)}</h1>
        {completed && <p className="text-[14px] leading-5 text-white/85">{pick(COPY.done, lang)}</p>}
        <Link
          to={`/onboarding${suffix}`}
          className="grid h-12 w-full max-w-[320px] place-items-center rounded-full bg-white/95 text-[16px] font-bold tracking-[-0.32px] text-ink"
        >
          {pick(furthestId ? COPY.resume : COPY.start, lang)}
        </Link>
        <button type="button" className="text-[13px] text-white/70 underline" onClick={reset}>
          {pick(COPY.reset, lang)}
        </button>
        <div className="flex gap-3 text-[13px] text-white/70">
          <Link to="/?lang=zh">中文</Link>
          <span>·</span>
          <Link to="/?lang=en">English</Link>
        </div>
      </div>
    </AppShell>
  );
}
