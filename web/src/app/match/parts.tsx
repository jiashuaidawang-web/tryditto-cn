import type { ReactNode } from 'react';
import { motion } from 'motion/react';
import { Check } from '../../components/icons';
import type { Venue } from '../../api';
import { pick, type Lang } from '../../i18n/lang';

/** Glass / magenta pill shared by the match pages (same tokens as onboarding pills, 05 §6). */
export function Pill({ selected, onClick, children, check }: { selected: boolean; onClick: () => void; children: ReactNode; check?: boolean }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`flex min-h-[44px] items-center gap-2 rounded-full px-4 py-2 text-left text-[15px] transition-colors ${selected ? 'ob-selected font-bold' : 'ob-glass'}`}
    >
      <span className="flex-1">{children}</span>
      {check && selected && (
        <motion.span initial={{ opacity: 0, scale: 0.6 }} animate={{ opacity: 1, scale: 1 }} className="grid size-[18px] place-items-center rounded-full bg-white text-ditto-pink">
          <Check size={11} />
        </motion.span>
      )}
    </button>
  );
}

/** Light-sheet chip (decline / report reasons). */
export function SheetChip({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={`rounded-full border px-4 py-[9px] text-[15px] transition-colors ${selected ? 'border-ink bg-ink font-bold text-white' : 'border-ink/15 bg-ink/[0.05] text-ink'}`}
    >
      {children}
    </button>
  );
}

export function Card({ title, children, className = '' }: { title?: string; children: ReactNode; className?: string }) {
  return (
    <section className={`app-card mx-4 px-5 py-5 ${className}`}>
      {title && <h2 className="app-display mb-3 text-[24px] leading-7">{title}</h2>}
      {children}
    </section>
  );
}

/** Venue placeholder map (TODO: AMap static map for the venue coordinates). */
export function VenueMap({ venue, lang, className = '' }: { venue: Venue; lang: Lang; className?: string }) {
  return (
    <div className={`relative overflow-hidden rounded-[18px] bg-[#151d23] ${className}`}>
      <svg viewBox="0 0 340 180" preserveAspectRatio="xMidYMid slice" className="absolute inset-0 size-full" aria-hidden>
        <rect width="340" height="180" fill="#151d23" />
        <ellipse cx="70" cy="60" rx="60" ry="34" fill="#18261f" />
        <ellipse cx="270" cy="140" rx="50" ry="28" fill="#1a2c3d" />
        <path d="M-10 120 C 80 90, 160 150, 350 100" stroke="#1a2c3d" strokeWidth="14" fill="none" />
        <g stroke="#2c3741" strokeWidth="2.2" fill="none">
          <path d="M0 40 L340 70" />
          <path d="M120 0 L150 180" />
          <path d="M240 0 L215 180" />
        </g>
        <g stroke="#222c34" strokeWidth="1.2" fill="none">
          <path d="M0 150 L340 160" />
          <path d="M60 0 L40 180" />
          <path d="M300 0 L320 180" />
          <path d="M0 95 L340 120" />
        </g>
      </svg>
      <span className="absolute left-1/2 top-[46%] grid size-[24px] -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-white shadow-[0_0_0_6px_rgba(255,44,171,0.3)]">
        <span className="block size-[12px] rounded-full bg-[#ff2cab]" />
      </span>
      <span className="absolute left-1/2 top-[calc(46%+18px)] max-w-[80%] -translate-x-1/2 truncate rounded-full bg-[#3b3f48]/85 px-[10px] py-[3px] text-[12px] font-bold text-white">
        {pick(venue.name, lang).split(' · ')[0]}
      </span>
      <span className="absolute bottom-[6px] left-[8px] text-[10px] font-bold tracking-wide text-white/40">{lang === 'zh' ? '示意地图' : 'Map preview'}</span>
    </div>
  );
}

export const fill = (copy: string, vars: Record<string, string | number>) => copy.replace(/\{(\w+)\}/g, (_, k: string) => String(vars[k] ?? ''));
