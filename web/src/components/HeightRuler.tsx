import { useEffect, useRef, useState } from 'react';
import { animate, motion, useMotionValue, useMotionValueEvent, useTransform } from 'motion/react';
import { useA11y } from '../i18n/a11y';
import type { HeightValue } from '../onboarding/types';

const PX_PER_STEP = 12;
const CM = { min: 130, max: 240 };
const IN = { min: 48, max: 96 };

export const cmToIn = (cm: number) => Math.round(cm / 2.54);
export const inToCm = (inch: number) => Math.round(inch * 2.54);
export const formatFtIn = (inch: number) => `${Math.floor(inch / 12)}'${inch % 12}"`;
export const formatHeight = (cm: number, unit: 'cm' | 'ft') => (unit === 'cm' ? `${cm} cm` : formatFtIn(cmToIn(cm)));

/**
 * Horizontal height ruler (S1/16-a): readout tile, dark box with 12px/step ticks (minor 15, mid 26, major 39),
 * pink centre needle, faded edges, `ft / in` | `cm` toggle. Drag or arrow keys; snaps to whole steps.
 */
export function HeightRuler({
  value,
  defaultCm,
  defaultUnit,
  allowFt,
  onChange,
}: {
  value: HeightValue | undefined;
  defaultCm: number;
  defaultUnit: 'cm' | 'ft';
  allowFt: boolean;
  onChange: (v: HeightValue) => void;
}) {
  const a11y = useA11y();
  const [unit, setUnit] = useState<'cm' | 'ft'>(value?.unit ?? defaultUnit);
  const range = unit === 'cm' ? CM : IN;
  const startCm = value?.cm ?? defaultCm;
  const pos = useMotionValue(unit === 'cm' ? startCm : cmToIn(startCm));
  const [shown, setShown] = useState(Math.round(pos.get()));
  const minRef = useRef(range.min);
  minRef.current = range.min;
  const offset = useTransform(pos, (p) => -(p - minRef.current) * PX_PER_STEP);
  const drag = useRef<{ x: number; p: number } | null>(null);
  const BOX = 358;

  useMotionValueEvent(pos, 'change', (p) => setShown(Math.round(p)));

  const commit = (steps: number) => {
    const v = Math.max(range.min, Math.min(range.max, Math.round(steps)));
    animate(pos, v, { type: 'spring', stiffness: 500, damping: 45 });
    onChange({ unit, cm: unit === 'cm' ? v : inToCm(v) });
  };

  useEffect(() => {
    pos.set(unit === 'cm' ? startCm : cmToIn(startCm));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [unit]);

  const switchUnit = (u: 'cm' | 'ft') => {
    if (u === unit) return;
    setUnit(u);
    onChange({ unit: u, cm: unit === 'cm' ? Math.round(pos.get()) : inToCm(Math.round(pos.get())) });
  };

  const count = range.max - range.min + 1;
  const isMajor = (v: number) => (unit === 'cm' ? v % 10 === 0 : v % 12 === 0);
  const isMid = (v: number) => (unit === 'cm' ? v % 5 === 0 : v % 6 === 0);

  return (
    <div className="flex w-full flex-col items-center">
      <div className="mb-4 grid h-[51px] min-w-[102px] place-items-center rounded-[16px] bg-white/95 px-5 text-[20px] font-bold text-ink">
        {unit === 'cm' ? `${shown} cm` : formatFtIn(shown)}
      </div>
      <div
        className="ob-dock relative h-[139px] w-full touch-none overflow-hidden rounded-[24px]"
        role="slider"
        tabIndex={0}
        aria-label={a11y.height}
        aria-valuemin={range.min}
        aria-valuemax={range.max}
        aria-valuenow={shown}
        onKeyDown={(e) => {
          if (e.key === 'ArrowRight' || e.key === 'ArrowUp') commit(Math.round(pos.get()) + 1);
          if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') commit(Math.round(pos.get()) - 1);
        }}
        onPointerDown={(e) => {
          (e.currentTarget as HTMLElement).setPointerCapture?.(e.pointerId);
          drag.current = { x: e.clientX, p: pos.get() };
        }}
        onPointerMove={(e) => {
          if (!drag.current) return;
          const p = drag.current.p - (e.clientX - drag.current.x) / PX_PER_STEP;
          pos.set(Math.max(range.min, Math.min(range.max, p)));
        }}
        onPointerUp={() => {
          if (!drag.current) return;
          drag.current = null;
          commit(pos.get());
        }}
        onWheel={(e) => {
          const d = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
          commit(pos.get() + Math.sign(d));
        }}
      >
        <div
          className="absolute inset-x-0 top-0 h-[110px]"
          style={{
            WebkitMaskImage: 'linear-gradient(90deg, rgba(0,0,0,0.12) 0%, #000 24%, #000 76%, rgba(0,0,0,0.12) 100%)',
            maskImage: 'linear-gradient(90deg, rgba(0,0,0,0.12) 0%, #000 24%, #000 76%, rgba(0,0,0,0.12) 100%)',
          }}
        >
          <motion.div className="absolute top-0 h-full" style={{ left: BOX / 2, x: offset }}>
            {Array.from({ length: count }, (_, i) => {
              const v = range.min + i;
              const major = isMajor(v);
              const h = major ? 39 : isMid(v) ? 26 : 15;
              return (
                <span key={v} className="absolute top-[24px]" style={{ left: i * PX_PER_STEP }}>
                  <span
                    className="absolute block w-[2px] -translate-x-1/2 rounded-full"
                    style={{ height: h, background: major ? 'rgba(255,255,255,0.5)' : 'rgba(255,255,255,0.3)' }}
                  />
                  {major && (
                    <span className="absolute top-[56px] -translate-x-1/2 text-[13px] leading-4 text-white/55">
                      {unit === 'cm' ? v : `${v / 12}'`}
                    </span>
                  )}
                </span>
              );
            })}
          </motion.div>
        </div>
        <span
          className="pointer-events-none absolute left-1/2 top-[24px] h-[50px] w-1 -translate-x-1/2 rounded-full"
          style={{ background: '#ff6fcf', boxShadow: '0 0 6px rgba(255,64,180,0.95), 0 0 2px #fff inset' }}
        />
        {allowFt && (
          <div
            className="absolute bottom-[7px] left-1/2 flex h-7 w-[102px] -translate-x-1/2 items-center rounded-full p-[2px] text-[11px]"
            style={{ background: 'rgba(0,10,30,0.35)', boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.08)' }}
            onPointerDown={(e) => e.stopPropagation()}
          >
            {(['ft', 'cm'] as const).map((u) => (
              <button
                key={u}
                type="button"
                onClick={() => switchUnit(u)}
                className={`h-full flex-1 rounded-full ${unit === u ? 'bg-white/95 font-bold text-ink' : 'font-normal text-white/70'}`}
              >
                {u === 'ft' ? 'ft / in' : 'cm'}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
