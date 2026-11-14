import { useEffect, useRef, useState } from 'react';
import { motion, type Variants } from 'motion/react';
import { pick, type Lang } from '../i18n/lang';
import { page as pageTokens, bubble as bubbleTokens, pageEnterEase } from '../motion/tokens';
import { ChatBubbles } from '../components/ChatBubble';
import { OptionList } from '../components/OptionList';
import { OptionPill } from '../components/OptionPill';
import { DoneButton, FloatingNext, Helper, PrivateNote, RoundButton, SendHint, TextAreaBox, TextBar } from '../components/Inputs';
import { BirthdayInput } from '../components/BirthdayInput';
import { RangeSlider } from '../components/RangeSlider';
import { HeightRuler, cmToIn, formatFtIn, inToCm } from '../components/HeightRuler';
import { PhotoGrid } from '../components/PhotoGrid';
import { IntroIllustration, Spinner } from '../components/Illustration';
import { Confetti } from '../components/Confetti';
import { HelpCircle } from '../components/icons';
import { LocationInput, SearchSheet, WhySheet, DEFAULT_LOCATION } from '../components/LocationPicker';
import { useA11y } from '../i18n/a11y';
import { t } from '../i18n/lang';
import { ApiError, MOCK_API, sendEmailCode, setPhotos, uploadPhotos, useDb } from '../api';
import { DEMO_CODE } from '../landing/auth';
import { PRIVATE_NOTE, optionsOf } from './questions';
import { isAnswered } from './validate';
import type { Answers, HeightRangeValue, HeightValue, LocationValue, Step } from './types';
import { useOnboarding } from './store';

export const AGE_DEFAULT: [number, number] = [18, 24];
export const HEIGHT_RANGE_DEFAULT: HeightRangeValue = { any: false, min: 168, max: 188 };
const DONT_CARE = t("I don't care about height", '身高无所谓');
const BOT_LABEL = t('Ditto', '见一面');
const DONE = t('Done', '完成');
const CODE_COPY = {
  sending: t('sending code…', '正在发送验证码…'),
  sentTo: t('sent to', '已发送至'),
  resend: t('resend code', '重新发送'),
  demo: MOCK_API
    ? t('Demo: any 6 digits work', '演示环境：输入任意 6 位数字即可')
    : t(`Demo: no email is sent, use ${DEMO_CODE}`, `演示环境：不会真的发邮件，验证码同登录验证码 ${DEMO_CODE}`),
};

/** Old page flies up; new column slides up from ~420px below and settles exponentially (12 §4.1). */
const slide: Variants = {
  initial: { y: pageTokens.enterFromY, opacity: 0 },
  enter: {
    y: 0,
    opacity: [pageTokens.enterFromOpacity, 1],
    transition: {
      y: { duration: pageTokens.enterDurationMs / 1000, ease: pageEnterEase, delay: pageTokens.gap },
      opacity: { duration: pageTokens.enterOpacityDuration, ease: 'easeOut' as const, delay: pageTokens.gap },
    },
  },
  exit: {
    y: pageTokens.exit.y,
    opacity: 0,
    transition: { duration: pageTokens.exit.duration, ease: pageTokens.exit.ease },
  },
};

export function PageFrame({ children, onMount }: { children: React.ReactNode; onMount?: () => void }) {
  return (
    <motion.div
      className="absolute inset-0"
      initial="initial"
      animate="enter"
      exit="exit"
      onAnimationStart={(def) => def === 'enter' && onMount?.()}
    >
      {children}
    </motion.div>
  );
}

export function SpinnerPage() {
  return (
    <motion.div
      className="absolute inset-0 grid place-items-center"
      variants={{ initial: { opacity: 0 }, enter: { opacity: 1, transition: { delay: 0.04 } }, exit: { opacity: 0, transition: { duration: 0.08 } } }}
    >
      <Spinner />
    </motion.div>
  );
}

interface Props {
  step: Step;
  lang: Lang;
  answers: Answers;
  onNext: () => void;
  onEscape?: () => void;
  /**
   * `edit`: /onboarding/edit/:field (13 §3) — no progress header, 20px higher column, `Done` pill instead of
   * the round Next (text bars and birthday keep the round Send).
   */
  mode?: 'onboarding' | 'edit';
  /** Edit mode, hobbies with branch hobbies picked: the button turns back into the round Next (09 §10.2). */
  editNext?: boolean;
  /** Last page: confetti + first option tap submits (09 §6). */
  finalTap?: boolean;
  /** Write answers somewhere other than the onboarding store (edit-mode draft). */
  onSet?: (id: string, value: unknown) => void;
  /** Code page: the address shown under the bubbles. */
  verifyEmail?: string;
}

export function QuestionPage({ step, lang, answers, onNext, onEscape, mode = 'onboarding', editNext, finalTap, onSet, verifyEmail }: Props) {
  const a11y = useA11y();
  const q = step.question;
  const value = answers[q.id];
  const setAnswer = useOnboarding((s) => s.setAnswer);
  const photos = useDb((s) => s.photos);
  const ctx = { answers, lang };
  const set = (v: unknown) => (onSet ? onSet(q.id, v) : setAnswer(q.id, v));
  const valid = isAnswered(q, value, ctx);
  const str = typeof value === 'string' ? value : '';
  const edit = mode === 'edit';
  const [searchOpen, setSearchOpen] = useState(false);
  const [whyOpen, setWhyOpen] = useState(false);
  const submitted = useRef(false);

  const isList = q.type === 'single' || q.type === 'multi';
  const floatingNext = isList || ['ageRange', 'height', 'heightRange', 'interstitial', 'photos', 'location'].includes(q.type);
  const heightUnit: 'cm' | 'ft' = lang === 'zh' ? 'cm' : ((answers.height as HeightValue | undefined)?.unit ?? 'ft');

  const startDelayMs = pageTokens.gap * 1000 + bubbleTokens.firstWordDelayMs;

  // Photos: the onboarding answer is the count; urls live in the api's mock DB.
  useEffect(() => {
    if (q.type === 'photos' && !edit && photos.length !== value) set(photos.length);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [photos.length, q.type]);

  const onListChange = (v: string | string[]) => {
    set(v);
    // 09 §30: the first option tap on the last page submits; the pill turns pink first.
    if (finalTap && !submitted.current && (Array.isArray(v) ? v.length > 0 : !!v)) {
      submitted.current = true;
      window.setTimeout(onNext, 320);
    }
  };

  const bottomButton = edit ? (
    editNext ? <RoundButton enabled={valid} onClick={onNext} /> : <DoneButton label={pick(DONE, lang)} enabled={valid} onClick={onNext} />
  ) : null;

  return (
    <>
      <motion.div className="absolute inset-0" variants={slide} data-step={step.id}>
        {finalTap && <Confetti />}
        <div className={`ob-scroll absolute inset-x-0 bottom-0 ${edit ? 'top-[52px]' : 'top-[72px]'} ${isList ? 'ob-fade-bottom' : ''}`}>
          <div className="px-4 pb-[150px] pt-3">
            <div className="mb-[10px] pl-4 text-[12px] leading-4 text-white/90">{pick(BOT_LABEL, lang)}</div>
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <ChatBubbles copies={(edit ? q.bubbles.slice(-1) : q.bubbles).map((b) => pick(b, lang))} lang={lang} startDelayMs={startDelayMs} />
              </div>
              {q.type === 'location' && (
                <button type="button" aria-label={a11y.whyWhere} onClick={() => setWhyOpen(true)} className="mr-[42px] mt-3 grid size-6 place-items-center text-white">
                  <HelpCircle size={22} />
                </button>
              )}
            </div>

            {q.escape && !edit && (
              <button type="button" onClick={onEscape} className="mt-5 text-left text-[14px] leading-5 text-white underline underline-offset-2">
                {pick(q.escape, lang)}
              </button>
            )}

            {q.type === 'code' && <CodeStatus lang={lang} email={verifyEmail ?? ''} />}

            {q.private && (
              <div className="mt-[18px]">
                <PrivateNote>{pick(PRIVATE_NOTE, lang)}</PrivateNote>
              </div>
            )}

            {isList && (
              <div className={q.private ? 'mt-[14px]' : 'mt-[18px]'}>
                <OptionList
                  lang={lang}
                  options={optionsOf(q, ctx)}
                  multi={q.type === 'multi'}
                  value={value as string | string[] | undefined}
                  onChange={onListChange}
                  exclusive={q.exclusive}
                  maxSelect={q.maxSelect}
                  maxHint={q.maxHint}
                  allowCustom={q.allowCustom}
                />
              </div>
            )}

            {q.type === 'location' && (
              <div className="mt-[17px]">
                <LocationInput
                  value={value as Partial<LocationValue> | undefined}
                  lang={lang}
                  onChange={(v) => set(v)}
                  onSearchOpenChange={setSearchOpen}
                />
              </div>
            )}

            {q.type === 'interstitial' && q.id === 'preferencesIntro' && (
              <div className="mt-[18px]">
                <IntroIllustration />
              </div>
            )}
          </div>
        </div>

        {/* Bottom-anchored inputs travel with the column (12 §4.1 item 4). */}
        {(q.type === 'text' || q.type === 'email' || q.type === 'date' || q.type === 'code') && (
          <div className="absolute inset-x-4 bottom-8">
            {q.helper && <Helper className="mb-[14px]">{pick(q.helper, lang)}</Helper>}
            {q.type === 'code' && <Helper className="mb-[14px]">{pick(CODE_COPY.demo, lang)}</Helper>}
            {q.type === 'date' ? (
              <BirthdayInput lang={lang} value={value} placeholder={pick(q.placeholder!, lang)} onChange={set} onSubmit={onNext} />
            ) : (
              <TextBar
                value={str}
                placeholder={pick(q.placeholder ?? t('', ''), lang)}
                type={q.type === 'email' ? 'email' : 'text'}
                inputMode={q.type === 'email' ? 'email' : q.type === 'code' ? 'numeric' : 'text'}
                autoComplete={q.type === 'email' ? 'email' : q.type === 'code' ? 'one-time-code' : q.id === 'name' ? 'given-name' : 'off'}
                maxLength={q.type === 'code' ? 6 : (q.maxLength ?? 120)}
                onChange={(v) => set(q.type === 'code' ? v.replace(/\D/g, '').slice(0, 6) : v)}
                onSubmit={onNext}
                canSubmit={valid}
              />
            )}
          </div>
        )}

        {q.type === 'longText' && (
          <div className="absolute inset-x-4 bottom-[41px]">
            {q.sendHint && (
              <div className="mb-5">
                <SendHint>{pick(q.sendHint, lang)}</SendHint>
              </div>
            )}
            <TextAreaBox
              value={str}
              placeholder={pick(q.placeholder ?? t('', ''), lang)}
              maxLength={q.maxLength}
              onChange={set}
              onSubmit={onNext}
              canSubmit={valid}
              {...(edit ? { doneLabel: pick(DONE, lang) } : {})}
            />
          </div>
        )}

        {q.type === 'ageRange' && (
          <div className="absolute inset-x-4 bottom-[96px]">
            <RangeSlider
              value={(value as [number, number] | undefined) ?? AGE_DEFAULT}
              onChange={set}
              // Measured: students 15.9px/yr (domain 17–36), non-students 8.86px/yr (domain 17–51).
              min={18}
              max={answers.isStudent === 'yes' ? 35 : 50}
              domain={answers.isStudent === 'yes' ? [17, 36] : [17, 51]}
              format={(n) => String(n)}
              labels={['Minimum age', 'Maximum age']}
            />
          </div>
        )}

        {q.type === 'height' && (
          <div className="absolute inset-x-4 bottom-[97px]">
            <HeightRuler
              value={value as HeightValue | undefined}
              defaultCm={lang === 'en' ? 173 : 170}
              defaultUnit={lang === 'en' ? 'ft' : 'cm'}
              allowFt={lang === 'en'}
              onChange={set}
            />
          </div>
        )}

        {q.type === 'heightRange' && (
          <HeightRangeInput lang={lang} unit={heightUnit} value={value as HeightRangeValue | undefined} onChange={set} />
        )}

        {q.type === 'photos' && (
          <div className="absolute inset-x-4 bottom-[102px]">
            {q.helper && <Helper className="mx-auto mb-4 max-w-[330px] !leading-[18px]">{pick(q.helper, lang)}</Helper>}
            <PhotoGrid urls={photos} onChange={(urls) => void setPhotos(urls)} onAddFiles={(files) => void uploadPhotos(files)} />
          </div>
        )}
      </motion.div>

      {edit && floatingNext && <div className="absolute bottom-8 right-4 z-10">{bottomButton}</div>}
      {!edit && floatingNext && <FloatingNext enabled={finalTap ? false : valid} onClick={onNext} />}

      {q.type === 'location' && (
        <>
          <SearchSheet
            open={searchOpen}
            lang={lang}
            onClose={() => setSearchOpen(false)}
            onPick={(placeId) => {
              const cur = value as Partial<LocationValue> | undefined;
              set({ placeId, mode: cur?.mode ?? DEFAULT_LOCATION.mode, minutes: cur?.minutes ?? DEFAULT_LOCATION.minutes });
              setSearchOpen(false);
            }}
          />
          <WhySheet open={whyOpen} lang={lang} onClose={() => setWhyOpen(false)} />
        </>
      )}
    </>
  );
}

/** `sending code…` while the request is in flight (12 §0.1), then where it went + resend. */
function CodeStatus({ lang, email }: { lang: Lang; email: string }) {
  const [sent, setSent] = useState(false);
  const send = () => {
    setSent(false);
    // 429 = a code went out under a minute ago (StrictMode runs this effect twice in dev); it is still valid.
    void sendEmailCode(email).then(
      () => setSent(true),
      (e: unknown) => e instanceof ApiError && e.status === 429 && setSent(true),
    );
  };
  useEffect(send, [email]); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <div className="mt-5 pl-1 text-[14px] leading-5 text-white/90">
      {sent ? (
        <>
          <span>
            {pick(CODE_COPY.sentTo, lang)} <b className="font-bold">{email}</b>
          </span>
          <button type="button" onClick={send} className="ml-2 underline underline-offset-2">
            {pick(CODE_COPY.resend, lang)}
          </button>
        </>
      ) : (
        <span className="text-white/75">{pick(CODE_COPY.sending, lang)}</span>
      )}
    </div>
  );
}

function HeightRangeInput({
  lang,
  unit,
  value,
  onChange,
}: {
  lang: Lang;
  unit: 'cm' | 'ft';
  value: HeightRangeValue | undefined;
  onChange: (v: HeightRangeValue) => void;
}) {
  const v = value ?? HEIGHT_RANGE_DEFAULT;
  const inFt = unit === 'ft';
  const pair: [number, number] = inFt ? [cmToIn(v.min), cmToIn(v.max)] : [v.min, v.max];
  return (
    <>
      {!v.any && (
        <div className="absolute inset-x-4 bottom-[96px]">
          <RangeSlider
            value={pair}
            onChange={([a, b]) => onChange({ any: false, min: inFt ? inToCm(a) : a, max: inFt ? inToCm(b) : b })}
            min={inFt ? 54 : 140}
            max={inFt ? 84 : 210}
            domain={inFt ? [53, 85] : [138, 212]}
            format={(n) => (inFt ? formatFtIn(n) : String(n))}
            labels={['Minimum height', 'Maximum height']}
          />
        </div>
      )}
      <div className="absolute bottom-8 left-4 w-[273px]">
        <OptionPill
          label={pick(DONT_CARE, lang)}
          emoji="🤷"
          selected={v.any}
          showCheck={false}
          widths={[273, 273]}
          center
          onClick={() => onChange({ ...v, any: !v.any })}
        />
      </div>
    </>
  );
}
