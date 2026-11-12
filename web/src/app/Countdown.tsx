import { useEffect, useState } from 'react';
import { formatCountdown, formatDropLabel, nextDrop } from '../api';
import type { Lang } from '../i18n/lang';

/** "Next match drops Wed 7pm · in 2d 3h 10m" (weekly drop, 01 §4.2). Ticks once a second. */
export function NextDrop({ lang, className = '' }: { lang: Lang; className?: string }) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  const drop = nextDrop(now);
  return (
    <p className={`text-center text-[14px] leading-5 text-white/85 ${className}`} aria-live="off">
      {lang === 'zh' ? (
        <>
          下一次匹配：{formatDropLabel(drop, lang)}
          <br />
          还有 <b className="font-bold text-white tabular-nums">{formatCountdown(drop, lang, now)}</b>
        </>
      ) : (
        <>
          Next match drops {formatDropLabel(drop, lang)}
          <br />
          in <b className="font-bold text-white tabular-nums">{formatCountdown(drop, lang, now)}</b>
        </>
      )}
    </p>
  );
}
