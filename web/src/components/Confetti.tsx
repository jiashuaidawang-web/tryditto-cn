import { useMemo } from 'react';
import { motion, useReducedMotion } from 'motion/react';

/**
 * Last-question confetti (09 §30, 21-flow-3.png): small magenta / green / yellow / white / blue / violet
 * rectangles drifting down around the options while the bubble is still typing. Duration was not
 * measured (09 §7 【猜测】); pieces loop until the page leaves.
 */
const COLORS = ['#e40089', '#ff3fa4', '#14c38e', '#f5c242', '#ffffff', '#3d7bf2', '#a873ff'];

function rand(seed: number) {
  const x = Math.sin(seed * 9301 + 49297) * 233280;
  return x - Math.floor(x);
}

export function Confetti({ count = 46, className = '' }: { count?: number; className?: string }) {
  const reduced = useReducedMotion();
  const pieces = useMemo(
    () =>
      Array.from({ length: count }, (_, i) => ({
        x: rand(i + 1) * 100,
        w: 7 + rand(i + 11) * 5,
        h: 9 + rand(i + 21) * 6,
        color: COLORS[i % COLORS.length]!,
        delay: rand(i + 31) * 2.4,
        dur: 3.6 + rand(i + 41) * 2.4,
        rot: rand(i + 51) * 360,
        drift: (rand(i + 61) - 0.5) * 60,
        startY: -40 - rand(i + 71) * 300,
      })),
    [count],
  );
  if (reduced) return null;
  return (
    <div className={`pointer-events-none absolute inset-0 z-[5] overflow-hidden ${className}`} aria-hidden>
      {pieces.map((p, i) => (
        <motion.span
          key={i}
          className="absolute top-0 block rounded-[1.5px]"
          style={{ left: `${p.x}%`, width: p.w, height: p.h, background: p.color, opacity: 0.9 }}
          initial={{ y: p.startY, x: 0, rotate: p.rot }}
          animate={{ y: 900, x: p.drift, rotate: p.rot + 540 }}
          transition={{ duration: p.dur, delay: p.delay, repeat: Infinity, ease: 'linear' }}
        />
      ))}
    </div>
  );
}
