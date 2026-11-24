import { useEffect, useState } from 'react';
import { ChatBubbles } from '../components/ChatBubble';
import { TextBar } from '../components/Inputs';
import { OptionPill } from '../components/OptionPill';
import { Header } from '../components/Header';
import { Spinner } from '../components/Illustration';
import {
  answerVoice,
  getVoiceProfile,
  startVoice,
  stopVoice,
  type VoiceProfile,
  type VoiceQuestion,
} from '../api/voice';
import { pick, t, useLang } from '../i18n/lang';
import { AppScreen, PrimaryButton, useGo } from './ui';

const C = {
  bot: t('Ditto', '见一面'),
  hint: t('Pick one, or say it the way you actually would. You can stop anytime.', '选一个，或用你平时会说的话说。可以随时停。'),
  custom: t('Say it in your own words', '换我自己说'),
  stop: t("That's enough", '先不答了'),
  home: t('Back to home', '回首页'),
  again: t('Answer a few more', '再答几题'),
  savedTitle: t('How you talk', '你的说话方式'),
  doneTitle: t('Your double will talk like this', '分身会按这个语气说话'),
  doneBody: t(
    'What you just wrote is saved as your voice. The next match simulation uses it.',
    '你写的原话已经存下来了。下次模拟见面，分身会照着这个语气说话。',
  ),
  savedLead: t('The next match simulation reads this as how you talk.', '下次模拟见面时，分身会把这段当成你的语气。'),
  empty: t('Not enough of your own words yet. A few more scenes will make this sharper.', '你自己的原话还不多。再答几题，分身会更像你。'),
  fail: t("Couldn't save. Please try again.", '没保存上，请再试一次'),
  step: t('Scene', '第'),
};

const MAX_STEPS = 12;

function styleOf(p: VoiceProfile | null): string {
  if (!p) return '';
  return p.voiceStyle || p.traits?.voice_style || '';
}

function summaryOf(p: VoiceProfile | null): string {
  if (!p) return '';
  return p.summary || p.traits?.summary || '';
}

function fillsFor(step: number): [number, number, number] {
  const s = Math.max(0, Math.min(MAX_STEPS, step));
  return [Math.min(1, s / 4), Math.min(1, Math.max(0, (s - 4) / 4)), Math.min(1, Math.max(0, (s - 8) / 4))];
}

/** 入驻后的选填情景采集：选题或写原话，停下来就把语气资产交给分身。 */
export function VoicePage() {
  const lang = useLang();
  const go = useGo();
  const [phase, setPhase] = useState<'loading' | 'saved' | 'ask' | 'done' | 'error'>('loading');
  const [question, setQuestion] = useState<VoiceQuestion | null>(null);
  const [profile, setProfile] = useState<VoiceProfile | null>(null);
  const [custom, setCustom] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    void getVoiceProfile()
      .then((p) => {
        if (!live) return;
        if (p.active) {
          setProfile(p);
          setPhase('saved');
        } else {
          void begin();
        }
      })
      .catch(() => live && setPhase('error'));
    return () => {
      live = false;
    };
    // 只在进入页面时看一次已有资产。
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function begin() {
    setError(null);
    setBusy(true);
    try {
      const q = await startVoice();
      setQuestion(q);
      setCustom('');
      setPhase('ask');
    } catch (e) {
      setError(e instanceof Error && e.message ? e.message : pick(C.fail, lang));
      setPhase('error');
    } finally {
      setBusy(false);
    }
  }

  async function submit(body: { chosenKey?: string; customText?: string }) {
    if (!question || busy) return;
    setBusy(true);
    setError(null);
    try {
      const res = await answerVoice(question.questionId, body);
      if (res.done) {
        setProfile(res.profile ?? null);
        setQuestion(null);
        setPhase('done');
      } else if (res.question) {
        setQuestion(res.question);
        setCustom('');
      } else {
        setError(pick(C.fail, lang));
      }
    } catch (e) {
      setError(e instanceof Error && e.message ? e.message : pick(C.fail, lang));
    } finally {
      setBusy(false);
    }
  }

  async function leave() {
    if (phase === 'ask' && question && !busy) {
      setBusy(true);
      try {
        const p = await stopVoice(question.sessionId);
        if (p.active) {
          setProfile(p);
          setPhase('done');
          setBusy(false);
          return;
        }
      } catch (e) {
        setError(e instanceof Error && e.message ? e.message : pick(C.fail, lang));
        setBusy(false);
        return;
      }
    }
    go('/home');
  }

  const style = styleOf(profile);
  const summary = summaryOf(profile);

  return (
    <AppScreen>
      <Header onBack={() => void leave()} fills={phase === 'ask' && question ? fillsFor(question.step) : [1, 1, 1]} />
      {phase === 'loading' && (
        <div className="absolute inset-0 grid place-items-center">
          <Spinner />
        </div>
      )}
      {phase === 'error' && (
        <div className="absolute inset-x-6 top-[120px] text-center text-white">
          <p className="text-[16px] leading-6">{error || pick(C.fail, lang)}</p>
          <div className="mt-6">
            <PrimaryButton onClick={() => void begin()}>{pick(C.again, lang)}</PrimaryButton>
          </div>
          <button type="button" className="mt-4 text-[14px] text-white/80" onClick={() => go('/home')}>
            {pick(C.home, lang)}
          </button>
        </div>
      )}
      {(phase === 'saved' || phase === 'done') && (
        <div className="absolute inset-x-5 top-[96px] bottom-8 flex flex-col text-white">
          <h1 className="app-display pr-24 text-[28px] leading-9">{pick(phase === 'done' ? C.doneTitle : C.savedTitle, lang)}</h1>
          <p className="mt-3 text-[15px] leading-[22px] text-white/80">
            {pick(phase === 'done' ? C.doneBody : style ? C.savedLead : C.empty, lang)}
          </p>
          <div className="mt-5 rounded-[20px] bg-black/25 px-4 py-4 text-[16px] leading-6">
            {style || summary || pick(C.empty, lang)}
          </div>
          {summary && style && <p className="mt-3 text-[14px] leading-5 text-white/75">{summary}</p>}
          <div className="mt-auto flex flex-col gap-3">
            <PrimaryButton onClick={() => void begin()} disabled={busy}>
              {pick(C.again, lang)}
            </PrimaryButton>
            <button type="button" className="h-12 text-[15px] text-white/85" onClick={() => go('/home')}>
              {pick(C.home, lang)}
            </button>
          </div>
        </div>
      )}
      {phase === 'ask' && question && (
        <>
          <div className="ob-scroll absolute inset-x-0 bottom-[108px] top-[72px]">
            <div className="px-4 pb-8 pt-3">
              <div className="mb-[10px] flex items-baseline justify-between pl-4 pr-1 text-[12px] leading-4 text-white/90">
                <span>{pick(C.bot, lang)}</span>
                <span>
                  {pick(C.step, lang)} {question.step}/{MAX_STEPS}
                </span>
              </div>
              <ChatBubbles key={question.questionId} copies={[question.scenario]} lang={lang} startDelayMs={80} />
              <p className="mt-4 pl-4 text-[13px] leading-5 text-white/70">{pick(C.hint, lang)}</p>
              <div className="mt-5 flex flex-col items-end gap-3" role="radiogroup">
                {question.options.map((opt) => (
                  <OptionPill
                    key={opt.key}
                    label={opt.text}
                    selected={false}
                    showCheck={false}
                    disabled={busy}
                    widths={[300, 320]}
                    onClick={() => void submit({ chosenKey: opt.key })}
                  />
                ))}
              </div>
              <button
                type="button"
                className="mt-6 pl-4 text-[14px] text-white/75 underline-offset-2 hover:underline"
                disabled={busy}
                onClick={() => void leave()}
              >
                {pick(C.stop, lang)}
              </button>
              {error && <p className="mt-3 pl-4 text-[13px] text-white">{error}</p>}
            </div>
          </div>
          <div className="absolute inset-x-4 bottom-6">
            <TextBar
              value={custom}
              placeholder={pick(C.custom, lang)}
              maxLength={80}
              onChange={setCustom}
              canSubmit={!busy && custom.trim().length > 0}
              onSubmit={() => void submit({ customText: custom.trim() })}
            />
          </div>
        </>
      )}
    </AppScreen>
  );
}
