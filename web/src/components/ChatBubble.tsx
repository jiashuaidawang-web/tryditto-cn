import { useEffect, useMemo, useState } from 'react';
import { motion, useReducedMotion } from 'motion/react';
import type { Lang } from '../i18n/lang';
import { bubble as tokens } from '../motion/tokens';

interface Token {
  text: string;
  keyword: boolean;
}

/** Split `**keyword**` copy into reveal tokens: words for en, 2-char groups for zh; a keyword is always one token. */
export function tokenize(copy: string, lang: Lang): Token[] {
  const out: Token[] = [];
  copy.split(/(\*\*[^*]+\*\*)/g).forEach((seg) => {
    if (!seg) return;
    if (seg.startsWith('**') && seg.endsWith('**')) {
      out.push({ text: seg.slice(2, -2), keyword: true });
      return;
    }
    if (lang === 'zh') {
      const chars = Array.from(seg);
      for (let i = 0; i < chars.length; i += tokens.zhCharsPerTick) {
        out.push({ text: chars.slice(i, i + tokens.zhCharsPerTick).join(''), keyword: false });
      }
      return;
    }
    (seg.match(/\s*\S+\s*/g) ?? [seg]).forEach((w) => out.push({ text: w, keyword: false }));
  });
  return out;
}

export const plainText = (copy: string) => copy.replace(/\*\*/g, '');

function Keyword({ text, animate }: { text: string; animate: boolean }) {
  return (
    <span className="whitespace-nowrap font-bold">
      {Array.from(text).map((ch, i) =>
        ch === ' ' || ch === '\u00a0' ? (
          <span key={i}>{'\u00a0'}</span>
        ) : (
          <motion.span
            key={i}
            className="inline-block origin-bottom"
            initial={animate ? { scale: 1 } : false}
            animate={animate ? { scale: [1, tokens.waveScale, 1] } : undefined}
            transition={{ duration: tokens.waveLetterDuration, delay: (40 + i * tokens.waveLetterDelayMs) / 1000, ease: 'easeInOut' }}
          >
            {ch}
          </motion.span>
        ),
      )}
    </span>
  );
}

interface BubbleProps {
  copy: string;
  lang: Lang;
  /** Number of tokens currently revealed. */
  shown: number;
  instant: boolean;
}

function Bubble({ copy, lang, shown, instant }: BubbleProps) {
  const list = useMemo(() => tokenize(copy, lang), [copy, lang]);
  return (
    <div
      className="w-fit max-w-full rounded-[28px] bg-white/90 px-4 py-3 text-[16px] font-medium leading-5 text-ink"
      aria-label={plainText(copy)}
      role="text"
    >
      <span aria-hidden>
        {list.slice(0, shown).map((tk, i) => (
          <motion.span
            key={i}
            initial={instant ? false : { opacity: tokens.wordFadeFrom }}
            animate={{ opacity: 1 }}
            transition={{ duration: tokens.wordFadeDuration, ease: 'linear' }}
          >
            {tk.keyword ? <Keyword text={tk.text} animate={!instant} /> : tk.text}
          </motion.span>
        ))}
        {shown === 0 && '\u200b'}
      </span>
    </div>
  );
}

/**
 * Sequential typing for one or more bubbles (12 §4.2):
 * empty pill first → first word after `startDelayMs` → 93ms/word → next bubble appears empty, waits ~220ms, types.
 */
export function ChatBubbles({
  copies,
  lang,
  startDelayMs,
  onDone,
}: {
  copies: string[];
  lang: Lang;
  startDelayMs: number;
  onDone?: () => void;
}) {
  const reduced = useReducedMotion();
  const counts = useMemo(() => copies.map((c) => tokenize(c, lang).length), [copies, lang]);
  const instant = !!reduced || new URLSearchParams(window.location.search).get('instant') === '1';
  // [bubbleIndex, tokensShownInThatBubble]
  const [cursor, setCursor] = useState<[number, number]>(() => (instant ? [copies.length - 1, counts.at(-1) ?? 0] : [0, 0]));

  useEffect(() => {
    if (instant) {
      onDone?.();
      return;
    }
    const timers: number[] = [];
    let at = startDelayMs;
    counts.forEach((n, b) => {
      if (b > 0) {
        // Next frame after the previous bubble finished: the empty pill pops in, then holds.
        at += 40;
        const appearAt = at;
        timers.push(window.setTimeout(() => setCursor([b, 0]), appearAt));
        at += tokens.secondBubbleHoldMs;
      }
      for (let i = 1; i <= n; i++) {
        const when = at + (i - 1) * tokens.wordIntervalMs;
        timers.push(window.setTimeout(() => setCursor([b, i]), when));
      }
      at += (n - 1) * tokens.wordIntervalMs;
    });
    timers.push(window.setTimeout(() => onDone?.(), at + 40));
    return () => timers.forEach(clearTimeout);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="flex flex-col items-start gap-3">
      {copies.slice(0, cursor[0] + 1).map((copy, b) => (
        <Bubble key={b} copy={copy} lang={lang} shown={b < cursor[0] ? (counts[b] ?? 0) : cursor[1]} instant={instant} />
      ))}
    </div>
  );
}
