import { useRef } from 'react';

/**
 * Dual-thumb slider used by the age range page (S1/15-a):
 * white readout tiles above each thumb, dark pill track with decorative ticks that glow pink between the thumbs.
 * Geometry is measured at 390px: track 358×64, thumbs 56×35, readouts 60×50, 18px gap.
 */
export function RangeSlider({
  value,
  onChange,
  min,
  max,
  /** Visual domain (the thumbs never reach the track ends — Ditto's aria range is 17–36 for 18–35). */
  domain,
  format,
  labels = ['Minimum', 'Maximum'],
}: {
  value: [number, number];
  onChange: (v: [number, number]) => void;
  min: number;
  max: number;
  domain: [number, number];
  format: (n: number) => string;
  labels?: [string, string];
}) {
  const track = useRef<HTMLDivElement>(null);
  const active = useRef<0 | 1 | null>(null);
  const PAD = 28;
  const WIDTH = 358;
  const inner = WIDTH - PAD * 2;
  const xOf = (v: number) => PAD + ((v - domain[0]) / (domain[1] - domain[0])) * inner;
  const vOf = (x: number) => Math.round(domain[0] + ((x - PAD) / inner) * (domain[1] - domain[0]));

  const set = (which: 0 | 1, raw: number) => {
    const v = Math.max(min, Math.min(max, raw));
    const next: [number, number] = which === 0 ? [Math.min(v, value[1]), value[1]] : [value[0], Math.max(v, value[0])];
    if (next[0] !== value[0] || next[1] !== value[1]) onChange(next);
  };

  const localX = (e: React.PointerEvent) => {
    const r = track.current!.getBoundingClientRect();
    return ((e.clientX - r.left) / r.width) * WIDTH;
  };

  const ticks = Array.from({ length: 32 }, (_, i) => 38 + i * 9);
  const lo = xOf(value[0]);
  const hi = xOf(value[1]);

  return (
    <div className="relative w-full">
      <div className="relative h-[68px]">
        {value.map((v, i) => (
          <div
            key={i}
            className="absolute top-0 grid h-[50px] w-[60px] -translate-x-1/2 place-items-center rounded-[14px] bg-white/95 text-[20px] font-bold text-ink"
            style={{ left: `${(xOf(v) / WIDTH) * 100}%` }}
          >
            {format(v)}
          </div>
        ))}
      </div>
      <div
        ref={track}
        className="ob-dock relative h-16 touch-none rounded-full"
        onPointerDown={(e) => {
          const x = localX(e);
          const which: 0 | 1 = Math.abs(x - lo) <= Math.abs(x - hi) ? (x > hi ? 1 : 0) : 1;
          active.current = which;
          (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
          set(which, vOf(x));
        }}
        onPointerMove={(e) => active.current !== null && set(active.current, vOf(localX(e)))}
        onPointerUp={() => (active.current = null)}
        onPointerCancel={() => (active.current = null)}
      >
        {ticks.map((x) => {
          const on = x >= lo - 2 && x <= hi + 2;
          return (
            <span
              key={x}
              className="absolute top-[22px] h-[17px] w-[2px] rounded-full"
              style={{
                left: `${(x / WIDTH) * 100}%`,
                background: on ? '#ff8fd6' : 'rgba(255,255,255,0.32)',
                boxShadow: on ? '0 0 4px rgba(255,64,180,0.9)' : 'none',
              }}
            />
          );
        })}
        {value.map((v, i) => (
          <span
            key={i}
            role="slider"
            tabIndex={0}
            aria-label={labels[i]}
            aria-valuemin={min}
            aria-valuemax={max}
            aria-valuenow={v}
            onKeyDown={(e) => {
              if (e.key === 'ArrowRight' || e.key === 'ArrowUp') set(i as 0 | 1, v + 1);
              if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') set(i as 0 | 1, v - 1);
            }}
            className="absolute top-[14px] h-[35px] w-14 -translate-x-1/2 rounded-full outline-none"
            style={{
              left: `${(xOf(v) / WIDTH) * 100}%`,
              background: 'rgba(255,255,255,0.13)',
              boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.28), inset 0 1px 4px rgba(255,255,255,0.18), 0 2px 8px rgba(0,0,0,0.25)',
              backdropFilter: 'blur(2px)',
              WebkitBackdropFilter: 'blur(2px)',
            }}
          />
        ))}
      </div>
    </div>
  );
}
