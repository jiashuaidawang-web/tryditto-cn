import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useNavigate, useSearchParams } from 'react-router';
import { api } from '../api';
import { AppShell } from '../components/AppShell';
import { Header } from '../components/Header';
import { parseLang, pick, t } from '../i18n/lang';
import { interstitial } from '../motion/tokens';
import { DEFAULT_FLAGS, buildSteps, navigable, progressOf, staleBranchKeys, type FlowFlags } from './flow';
import { AGE_DEFAULT, PageFrame, QuestionPage, SpinnerPage } from './QuestionPage';
import { useOnboarding } from './store';
import type { Step } from './types';
import { ageOn, parseIsoDate } from './validate';

const WELCOME = {
  title: t('Welcome back', '欢迎回来'),
  body: t('Your progress is saved. Pick up where you left off.', '进度已保存，从上次的地方继续吧。'),
};

function useFlags(): FlowFlags {
  const [params] = useSearchParams();
  const tail = params.get('tail');
  return {
    skipAnswered: params.get('skipAnswered') === '1' || DEFAULT_FLAGS.skipAnswered,
    tail: tail === 'long' || tail === 'short' ? tail : DEFAULT_FLAGS.tail,
  };
}

export function OnboardingPage() {
  const [params] = useSearchParams();
  const lang = parseLang(params.get('lang'));
  const flags = useFlags();
  const navigate = useNavigate();
  const suffix = params.toString() ? `?${params.toString()}` : '';

  const answers = useOnboarding((s) => s.answers);
  const currentId = useOnboarding((s) => s.currentId);
  const furthestId = useOnboarding((s) => s.furthestId);
  const goTo = useOnboarding((s) => s.goTo);

  const steps = useMemo(() => buildSteps(answers, lang, flags), [answers, lang, flags.skipAnswered, flags.tail]); // eslint-disable-line react-hooks/exhaustive-deps
  const realSteps = steps.filter((s) => !s.ghost);

  const [spinnerFor, setSpinnerFor] = useState<Step | null>(null);
  const [fills, setFills] = useState<[number, number, number]>([0, 0, 0]);
  const [welcome, setWelcome] = useState(false);
  const [ageConfirm, setAgeConfirm] = useState<{ age: number; date: Date } | null>(null);
  const busy = useRef(false);

  // Resume at the furthest page reached.
  useEffect(() => {
    const st = useOnboarding.getState();
    const target = st.furthestId && steps.some((s) => s.id === st.furthestId) ? st.furthestId : (steps.find(navigable)?.id ?? null);
    if (target && target !== st.currentId) goTo(target);
    if (st.furthestId && st.furthestId !== steps[0]?.id && params.get('welcome') !== '0') {
      setWelcome(true);
      const timer = window.setTimeout(() => setWelcome(false), 3200);
      return () => clearTimeout(timer);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const current: Step | undefined =
    realSteps.find((s) => s.id === currentId && navigable(s)) ??
    realSteps.find((s) => s.id === furthestId && navigable(s)) ??
    realSteps.find(navigable);

  const indexOf = (list: Step[], id: string | null) => list.findIndex((s) => s.id === id);

  const advance = useCallback(
    async (opts?: { force?: boolean; ageConfirmed?: boolean }) => {
      if (!current || busy.current) return;
      const q = current.question;
      const st = useOnboarding.getState();
      let value = st.answers[q.id];

      if (q.type === 'date' && !opts?.ageConfirmed) {
        const d = parseIsoDate(value);
        if (d && ageOn(d) < 18) {
          setAgeConfirm({ age: ageOn(d), date: d });
          return;
        }
      }
      if (q.type === 'ageRange' && value === undefined) value = AGE_DEFAULT;
      if (q.type === 'longText' && value === undefined) value = '';

      busy.current = true;
      try {
        if (q.type !== 'interstitial') await api.saveAnswer(q.id, value);
        if (q.id === 'hobbies') st.removeAnswers(staleBranchKeys(useOnboarding.getState().answers));
        if (q.id === 'schoolEmail' && !opts?.force) st.setAnswer('noSchoolEmail', false);

        const fresh = useOnboarding.getState();
        const list = buildSteps(fresh.answers, lang, flags);
        const from = indexOf(list, q.id);
        let i = from + 1;
        const skipped: Step[] = [];
        while (i < list.length && !navigable(list[i]!)) {
          if (list[i]!.skip) skipped.push(list[i]!);
          i += 1;
        }
        const next = list[i];
        if (!next) {
          fresh.complete();
          navigate(`/${suffix}`);
          return;
        }
        const furthest = indexOf(list, fresh.furthestId);
        const nextFurthest = furthest > i ? fresh.furthestId : next.id;
        if (skipped.length) {
          setSpinnerFor(skipped.at(-1)!);
          await new Promise((r) => setTimeout(r, interstitial.skipSpinnerMs));
          setSpinnerFor(null);
        }
        goTo(next.id, nextFurthest);
      } finally {
        busy.current = false;
      }
    },
    [current, flags, lang, navigate, goTo, suffix],
  );

  const back = useCallback(() => {
    if (!current) return;
    const i = indexOf(steps, current.id);
    for (let j = i - 1; j >= 0; j--) {
      if (navigable(steps[j]!)) {
        goTo(steps[j]!.id);
        return;
      }
    }
  }, [current, steps, goTo]);

  const escape = useCallback(() => {
    useOnboarding.getState().setAnswer('noSchoolEmail', true);
    void advance({ force: true });
  }, [advance]);

  const firstNavigable = steps.find(navigable);
  const pageKey = spinnerFor ? `spinner:${spinnerFor.id}` : (current?.id ?? 'none');
  const shownStep = spinnerFor ?? current;

  return (
    <AppShell>
      <Header fills={fills} onBack={current && current.id !== firstNavigable?.id && !spinnerFor ? back : undefined} />

      <AnimatePresence mode="wait">
        <PageFrame key={pageKey} onMount={() => setFills(progressOf(shownStep))}>
          {spinnerFor ? (
            <SpinnerPage />
          ) : current ? (
            <QuestionPage step={current} lang={lang} answers={answers} onNext={() => void advance()} onEscape={escape} />
          ) : null}
        </PageFrame>
      </AnimatePresence>

      <AnimatePresence>
        {welcome && (
          <motion.div
            className="absolute inset-x-4 top-[84px] z-30 flex items-start gap-3 rounded-[20px] bg-white/95 px-4 py-3 text-ink shadow-lg"
            initial={{ opacity: 0, y: -8 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            onClick={() => setWelcome(false)}
          >
            <span className="text-[22px] leading-6">👋</span>
            <div>
              <div className="text-[15px] font-bold leading-5">{pick(WELCOME.title, lang)}</div>
              <div className="text-[13px] leading-[18px] opacity-80">{pick(WELCOME.body, lang)}</div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {ageConfirm && (
          <AgeConfirm
            lang={lang}
            age={ageConfirm.age}
            date={ageConfirm.date}
            onEdit={() => setAgeConfirm(null)}
            onConfirm={() => {
              setAgeConfirm(null);
              void advance({ ageConfirmed: true });
            }}
          />
        )}
      </AnimatePresence>
    </AppShell>
  );
}

function AgeConfirm({
  lang,
  age,
  date,
  onEdit,
  onConfirm,
}: {
  lang: 'en' | 'zh';
  age: number;
  date: Date;
  onEdit: () => void;
  onConfirm: () => void;
}) {
  const enDate = date.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
  const zhDate = `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日`;
  const body =
    lang === 'en'
      ? `You're ${age} years old. Your birthday is ${enDate}. You can't change it later, so make sure it's right.`
      : `你今年 ${age} 岁，生日是 ${zhDate}。之后不能再改，确认一下哦。`;
  return (
    <motion.div className="absolute inset-0 z-50 flex items-end bg-black/40 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <motion.div
        role="alertdialog"
        className="w-full rounded-[28px] bg-white/95 p-6 text-ink"
        initial={{ y: 40 }}
        animate={{ y: 0 }}
        exit={{ y: 40 }}
      >
        <p className="text-[16px] font-medium leading-[22px]">{body}</p>
        <button type="button" className="mt-5 h-12 w-full rounded-full bg-ink text-[16px] font-bold text-white" onClick={onConfirm}>
          {lang === 'en' ? "Yes, that's right" : '没错'}
        </button>
        <button type="button" className="mt-2 h-12 w-full rounded-full text-[16px] font-bold" onClick={onEdit}>
          {lang === 'en' ? 'Edit birthday' : '修改生日'}
        </button>
      </motion.div>
    </motion.div>
  );
}
