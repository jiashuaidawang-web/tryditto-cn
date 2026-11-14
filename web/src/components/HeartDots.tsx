import { motion } from 'motion/react';
import { useA11y } from '../i18n/a11y';

/**
 * Submit → home transition (09 §6, 21-flow-4.png): a heart of round dots on the bare sky, centre dots
 * glowing pink, no header. Grid read off the frame: 25px pitch, 10px dots, rows 321…521 at x 95…295.
 * Timing was not captured; dots ripple out from the centre, then the pink core pulses.
 */
const ROWS: [number, number][][] = [
  [[-3, -2], [2, 3]],
  [[-4, -1], [1, 4]],
  [[-4, 4]],
  [[-4, 4]],
  [[-4, 4]],
  [[-3, 3]],
  [[-2, 2]],
  [[-1, 1]],
  [[0, 0]],
];
const PINK = new Set(['-1,3', '0,3', '1,3', '-1,4', '0,4', '1,4']);
const BLUSH = new Set(['0,2', '-1,5', '0,5', '1,5']);

const DOTS = ROWS.flatMap((spans, row) => spans.flatMap(([a, b]) => Array.from({ length: b - a + 1 }, (_, i) => ({ col: a + i, row }))));

export function HeartDots() {
  const a11y = useA11y();
  return (
    <div className="absolute inset-0 grid place-items-center" aria-label={a11y.loading} role="status">
      <div className="relative" style={{ width: 225, height: 225 }}>
        {DOTS.map(({ col, row }) => {
          const key = `${col},${row}`;
          const pink = PINK.has(key);
          const blush = BLUSH.has(key);
          const dist = Math.hypot(col, row - 3.5);
          return (
            <motion.span
              key={key}
              className="absolute block size-[10px] rounded-full"
              style={{
                left: 112.5 + col * 25 - 5,
                top: row * 25 + 7.5,
                background: pink ? '#ff5fc0' : blush ? 'rgba(255,190,225,0.75)' : 'rgba(255,255,255,0.34)',
                boxShadow: pink ? '0 0 10px 3px rgba(255,80,190,0.55)' : 'none',
              }}
              initial={{ opacity: 0, scale: 0.4 }}
              animate={pink ? { opacity: [0, 1, 0.75, 1], scale: [0.4, 1.15, 1, 1.1] } : { opacity: 1, scale: 1 }}
              transition={{
                delay: dist * 0.06,
                duration: pink ? 1.4 : 0.35,
                ease: 'easeOut',
                ...(pink ? { repeat: Infinity, repeatType: 'reverse' as const } : {}),
              }}
            />
          );
        })}
      </div>
    </div>
  );
}
