import { motion, type Variants } from 'motion/react';
import { pick, type Lang } from '../i18n/lang';
import { page as pageTokens, bubble as bubbleTokens, pageEnterEase } from '../motion/tokens';
import { ChatBubbles } from '../components/ChatBubble';
import { OptionList } from '../components/OptionList';
import { OptionPill } from '../components/OptionPill';
import { FloatingNext, Helper, PrivateNote, SendHint, TextAreaBox, TextBar } from '../components/Inputs';
import { BirthdayInput } from '../components/BirthdayInput';
import { RangeSlider } from '../components/RangeSlider';
import { HeightRuler, cmToIn, formatFtIn, inToCm } from '../components/HeightRuler';
import { PhotoGrid } from '../components/PhotoGrid';
import { IntroIllustration, Spinner } from '../components/Illustration';
import { t } from '../i18n/lang';
import { PRIVATE_NOTE, optionsOf } from './questions';
import { isAnswered } from './validate';
import type { Answers, HeightRangeValue, HeightValue, Step } from './types';
import { useOnboarding } from './store';

export const AGE_DEFAULT: [number, number] = [18, 24];
export const HEIGHT_RANGE_DEFAULT: HeightRangeValue = { any: false, min: 168, max: 188 };
const DONT_CARE = t("I don't care about height", '身高无所谓');
const BOT_LABEL = t('Ditto', '见一面');

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
  onEscape: () => void;
}

export function QuestionPage({ step, lang, answers, onNext, onEscape }: Props) {
  const q = step.question;
  const value = answers[q.id];
  const setAnswer = useOnboarding((s) => s.setAnswer);
  const photoUrls = useOnboarding((s) => s.photoUrls);
  const setPhotoUrls = useOnboarding((s) => s.setPhotoUrls);
  const ctx = { answers, lang };
  const set = (v: unknown) => setAnswer(q.id, v);
  const valid = isAnswered(q, value, ctx);
  const str = typeof value === 'string' ? value : '';

  const isList = q.type === 'single' || q.type === 'multi';
  const floatingNext = isList || ['ageRange', 'height', 'heightRange', 'interstitial', 'photos'].includes(q.type);
  const heightUnit: 'cm' | 'ft' = lang === 'zh' ? 'cm' : ((answers.height as HeightValue | undefined)?.unit ?? 'ft');

  const startDelayMs = pageTokens.gap * 1000 + bubbleTokens.firstWordDelayMs;

  return (
    <>
      <motion.div className="absolute inset-0" variants={slide} data-step={step.id}>
        <div className={`ob-scroll absolute inset-x-0 bottom-0 top-[72px] ${isList ? 'ob-fade-bottom' : ''}`}>
          <div className="px-4 pb-[150px] pt-3">
            <div className="mb-[10px] pl-4 text-[12px] leading-4 text-white/90">{pick(BOT_LABEL, lang)}</div>
            <ChatBubbles copies={q.bubbles.map((b) => pick(b, lang))} lang={lang} startDelayMs={startDelayMs} />

            {q.escape && (
              <button type="button" onClick={onEscape} className="mt-5 text-left text-[14px] leading-5 text-white underline underline-offset-2">
                {pick(q.escape, lang)}
              </button>
            )}

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
                  onChange={set}
                  exclusive={q.exclusive}
                  maxSelect={q.maxSelect}
                  maxHint={q.maxHint}
                  allowCustom={q.allowCustom}
                />
              </div>
            )}

            {q.type === 'interstitial' && q.id === 'preferencesIntro' && (
              <div className="mt-[18px]">
                <IntroIllustration />
              </div>
            )}
            {q.id === 'emailVerify' && (
              <div className="mt-10 grid place-items-center">
                <Spinner />
              </div>
            )}
          </div>
        </div>

        {/* Bottom-anchored inputs travel with the column (12 §4.1 item 4). */}
        {(q.type === 'text' || q.type === 'email' || q.type === 'date') && (
          <div className="absolute inset-x-4 bottom-8">
            {q.helper && <Helper className="mb-[14px]">{pick(q.helper, lang)}</Helper>}
            {q.type === 'date' ? (
              <BirthdayInput lang={lang} value={value} placeholder={pick(q.placeholder!, lang)} onChange={set} onSubmit={onNext} />
            ) : (
              <TextBar
                value={str}
                placeholder={pick(q.placeholder ?? t('', ''), lang)}
                type={q.type === 'email' ? 'email' : 'text'}
                inputMode={q.type === 'email' ? 'email' : 'text'}
                autoComplete={q.type === 'email' ? 'email' : q.id === 'name' ? 'given-name' : 'off'}
                maxLength={q.maxLength ?? 120}
                onChange={set}
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
            <PhotoGrid
              urls={photoUrls}
              onChange={(urls) => {
                setPhotoUrls(urls);
                set(urls.length);
              }}
            />
          </div>
        )}
      </motion.div>

      {floatingNext && <FloatingNext enabled={valid} onClick={onNext} />}
    </>
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
