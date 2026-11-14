import { useEffect, useMemo, useRef, useState } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { useA11y } from '../i18n/a11y';
import { pick, t, type Lang } from '../i18n/lang';
import { CURRENT_PLACE_ID, nearbyCount, placeById, searchPlaces, type Place } from '../api';
import type { LocationValue, TravelMode } from '../onboarding/types';
import { Car, ChevronDown, Close, Crosshair, Footprints, MapPin, Search, Train } from './icons';

/**
 * Dating-area picker (09 §19, 18-location-*.png; /where 02-where.png). Ditto draws a dark Google map with
 * a pink isochrone; we have no map provider offline, so the map is a seeded stylized placeholder with the
 * same layout and the isochrone radius driven by mode × minutes. TODO: AMap JS API + isochrone service.
 */

export const LOCATION_COPY = {
  search: t('Search a location', '搜索地点或学校'),
  current: t('Use my current location', '使用当前位置'),
  updating: t('Updating area…', '正在更新范围…'),
  whyTitle: t('Your dating area', '你的约会范围'),
  whyBody: t('Set your area so we match you with people and spots near you.', '设定一个范围，我们会在这附近帮你找人、找约会地点。'),
  gotIt: t('Got it', '知道了'),
  noResults: t('No places found', '没有找到这个地点'),
  nearby: t('nearby', '人在附近'),
  mapNote: t('Map preview', '示意地图'),
};

const MODE_COPY: Record<TravelMode, ReturnType<typeof t>> = {
  walk: t('walking', '步行'),
  transit: t('transit', '公共交通'),
  drive: t('driving', '驾车'),
};

export const DEFAULT_LOCATION: Omit<LocationValue, 'placeId'> = { mode: 'drive', minutes: 60 };
export const MIN_MINUTES = 15;
export const MAX_MINUTES = 120;

function hash(s: string) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return () => {
    h = Math.imul(h ^ (h >>> 15), 2246822507);
    h = Math.imul(h ^ (h >>> 13), 3266489909);
    return ((h ^= h >>> 16) >>> 0) / 4294967296;
  };
}

function radiusPx(mode: TravelMode, minutes: number) {
  const kmh = mode === 'walk' ? 4.5 : mode === 'transit' ? 18 : 28;
  return 14 + 26 * Math.log1p((kmh * minutes) / 60);
}

export function formatMinutes(minutes: number, lang: Lang) {
  const h = minutes / 60;
  if (minutes < 60) return lang === 'zh' ? `${minutes}分钟` : `${minutes}m`;
  const v = Number.isInteger(h) ? String(h) : h.toFixed(1).replace(/\.0$/, '');
  return lang === 'zh' ? `${v}小时` : `${v}h`;
}

/** Stylized dark map with roads, water, labels, pink isochrone and the white/pink pin. */
export function MapCanvas({
  place,
  mode,
  minutes,
  lang,
  showCount = false,
  className = '',
}: {
  place: Place | undefined;
  mode: TravelMode;
  minutes: number;
  lang: Lang;
  showCount?: boolean;
  className?: string;
}) {
  const W = 340;
  const H = 380;
  const seed = place?.id ?? 'none';
  const art = useMemo(() => {
    const r = hash(seed);
    const roads = Array.from({ length: 9 }, () => {
      const pts = Array.from({ length: 5 }, (_, i) => [((i / 4) * W * 1.2 - W * 0.1) | 0, (r() * H) | 0]);
      if (r() > 0.5) pts.forEach((p) => p.reverse());
      return pts.map(([x, y]) => `${x},${y}`).join(' ');
    });
    const river = `M-10 ${(H * (0.3 + r() * 0.4)) | 0} C ${W * 0.3} ${H * r()}, ${W * 0.6} ${H * r()}, ${W + 10} ${(H * (0.3 + r() * 0.4)) | 0}`;
    const parks = Array.from({ length: 4 }, () => ({ x: r() * W, y: r() * H, rx: 14 + r() * 26, ry: 10 + r() * 18 }));
    const labelPos = [
      [0.26, 0.2],
      [0.72, 0.28],
      [0.22, 0.74],
      [0.7, 0.8],
    ].map(([x, y]) => [x! * W + (r() - 0.5) * 30, y! * H + (r() - 0.5) * 20]);
    const blob = Array.from({ length: 34 }, (_, i) => 0.62 + 0.5 * r() * (i % 3 === 0 ? 1 : 0.55));
    return { roads, river, parks, labelPos, blob };
  }, [seed]);

  const cx = W / 2;
  const cy = H * 0.48;
  const rad = radiusPx(mode, minutes);
  const blobPath = art.blob
    .map((k, i) => {
      const a = (i / art.blob.length) * Math.PI * 2;
      const rr = rad * k;
      return `${i ? 'L' : 'M'}${(cx + Math.cos(a) * rr).toFixed(1)} ${(cy + Math.sin(a) * rr * 0.92).toFixed(1)}`;
    })
    .join(' ')
    .concat('Z');

  return (
    <div className={`relative overflow-hidden bg-[#141c22] ${className}`}>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="xMidYMid slice" className="absolute inset-0 size-full" aria-hidden>
        <rect width={W} height={H} fill="#151d23" />
        {art.parks.map((p, i) => (
          <ellipse key={i} cx={p.x} cy={p.y} rx={p.rx} ry={p.ry} fill="#18261f" />
        ))}
        <path d={art.river} stroke="#1a2c3d" strokeWidth={14} fill="none" />
        {art.roads.map((pts, i) => (
          <polyline key={i} points={pts} fill="none" stroke={i % 3 ? '#222c34' : '#2c3741'} strokeWidth={i % 3 ? 1.2 : 2.4} />
        ))}
        {place &&
          place.nearby.map((label, i) => {
            const [x, y] = art.labelPos[i]!;
            return (
              <g key={i}>
                <circle cx={x} cy={y} r={2.2} fill="#e5ecf2" />
                <text x={x! + 6} y={y! + 4} fontSize={lang === 'zh' ? 12 : 11.5} fill="#d6dee6" fontFamily="Arial, PingFang SC" fontWeight={600}>
                  {pick(label, lang)}
                </text>
              </g>
            );
          })}
        {place && (
          <motion.path
            key={`${mode}-${minutes}-${seed}`}
            d={blobPath}
            fill="rgba(228,0,137,0.28)"
            stroke="#ff2cab"
            strokeWidth={2}
            strokeLinejoin="round"
            initial={{ opacity: 0, scale: 0.85 }}
            animate={{ opacity: 1, scale: 1 }}
            style={{ transformOrigin: `${cx}px ${cy}px` }}
            transition={{ duration: 0.35, ease: 'easeOut' }}
          />
        )}
      </svg>
      {place ? (
        <>
          <span
            className="absolute grid size-[22px] -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-white shadow-[0_0_0_4px_rgba(255,44,171,0.35)]"
            style={{ left: '50%', top: '48%' }}
          >
            <span className="block size-[12px] rounded-full bg-[#ff2cab]" />
          </span>
          {showCount && (
            <span
              className="absolute -translate-x-1/2 rounded-full border border-white/25 bg-[#3b3f48]/85 px-[10px] py-[4px] text-[12.5px] font-bold text-white backdrop-blur"
              style={{ left: '50%', top: 'calc(48% + 20px)' }}
            >
              🔥 {nearbyCount(place, mode, minutes).toLocaleString()} {pick(LOCATION_COPY.nearby, lang)}
            </span>
          )}
        </>
      ) : (
        <div className="absolute inset-0 grid place-items-center text-[14px] text-white/70">{pick(LOCATION_COPY.updating, lang)}</div>
      )}
      <span className="absolute bottom-[6px] left-[8px] text-[10px] font-bold tracking-wide text-white/40">{pick(LOCATION_COPY.mapNote, lang)}</span>
    </div>
  );
}

export function SearchRow({
  place,
  lang,
  onOpen,
  onClear,
  onLocate,
  className = '',
}: {
  place: Place | undefined;
  lang: Lang;
  onOpen: () => void;
  onClear: () => void;
  onLocate: () => void;
  className?: string;
}) {
  const a11y = useA11y();
  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <button
        type="button"
        onClick={onOpen}
        className="flex h-[38px] min-w-0 flex-1 items-center gap-[10px] rounded-full pl-3 pr-2 text-left text-[15px]"
        style={{ background: 'rgba(8,23,33,0.45)', backdropFilter: 'blur(12px)', WebkitBackdropFilter: 'blur(12px)' }}
      >
        <Search size={18} className="shrink-0 text-white/90" />
        <span className={`min-w-0 flex-1 truncate ${place ? 'text-white' : 'text-white/55'}`}>
          {place ? `${pick(place.name, lang)}${lang === 'zh' ? ' · ' : ', '}${pick(place.sub, lang)}` : pick(LOCATION_COPY.search, lang)}
        </span>
        {place && (
          <span
            role="button"
            aria-label={a11y.clear}
            onClick={(e) => {
              e.stopPropagation();
              onClear();
            }}
            className="grid size-7 place-items-center text-white/80"
          >
            <Close size={15} />
          </span>
        )}
      </button>
      <button
        type="button"
        aria-label={a11y.useLocation}
        onClick={onLocate}
        className="grid size-[40px] shrink-0 place-items-center rounded-full border border-white/20 text-white"
        style={{ background: 'rgba(8,23,33,0.45)', backdropFilter: 'blur(12px)', WebkitBackdropFilter: 'blur(12px)' }}
      >
        <Crosshair size={20} />
      </button>
    </div>
  );
}

const MODES: { id: TravelMode; Icon: typeof Car }[] = [
  { id: 'walk', Icon: Footprints },
  { id: 'transit', Icon: Train },
  { id: 'drive', Icon: Car },
];

/** "1h max driving" + walk/transit/drive segmented + tick slider (pink ticks up to the thumb). */
export function TravelControls({
  mode,
  minutes,
  lang,
  onChange,
}: {
  mode: TravelMode;
  minutes: number;
  lang: Lang;
  onChange: (v: { mode: TravelMode; minutes: number }) => void;
}) {
  const a11y = useA11y();
  const TICKS = 44;
  const frac = (minutes - MIN_MINUTES) / (MAX_MINUTES - MIN_MINUTES);
  return (
    <div className="rounded-[24px] border border-white/20 px-4 pb-[14px] pt-[10px]" style={{ background: 'rgba(255,255,255,0.1)', backdropFilter: 'blur(8px)', WebkitBackdropFilter: 'blur(8px)' }}>
      <div className="flex items-center justify-between">
        <div className="flex items-baseline gap-[6px]">
          <span className="text-[20px] font-bold leading-6">{formatMinutes(minutes, lang)}</span>
          <span className="text-[14px] text-white/85">{lang === 'zh' ? `以内 · ${MODE_COPY[mode].zh}` : `max ${MODE_COPY[mode].en}`}</span>
        </div>
        <div className="flex h-[42px] items-center gap-[2px] rounded-full border border-white/25 p-[3px]" style={{ background: 'rgba(8,23,33,0.25)' }}>
          {MODES.map(({ id, Icon }) => (
            <button
              key={id}
              type="button"
              aria-label={pick(MODE_COPY[id], lang)}
              aria-pressed={mode === id}
              onClick={() => onChange({ mode: id, minutes })}
              className={`grid size-[34px] place-items-center rounded-full transition-colors ${mode === id ? 'bg-white text-[#ff2cab]' : 'text-white/85'}`}
            >
              <Icon size={18} />
            </button>
          ))}
        </div>
      </div>
      <div className="relative mt-[10px] h-[26px]">
        <div className="absolute inset-x-0 top-1/2 flex -translate-y-1/2 justify-between">
          {Array.from({ length: TICKS }, (_, i) => (
            <span key={i} className="block h-[14px] w-[2px] rounded-full" style={{ background: i / (TICKS - 1) <= frac ? '#ff7fd0' : 'rgba(255,255,255,0.3)' }} />
          ))}
        </div>
        <span
          className="pointer-events-none absolute top-0 h-[26px] w-[40px] -translate-x-1/2 rounded-full border border-white/40"
          style={{ left: `calc(20px + ${frac} * (100% - 40px))`, background: 'rgba(255,255,255,0.18)', backdropFilter: 'blur(6px)', WebkitBackdropFilter: 'blur(6px)' }}
        />
        <input
          type="range"
          aria-label={a11y.maxTravel}
          min={MIN_MINUTES}
          max={MAX_MINUTES}
          step={5}
          value={minutes}
          onChange={(e) => onChange({ mode, minutes: Number(e.target.value) })}
          className="absolute inset-0 size-full cursor-pointer opacity-0"
        />
      </div>
    </div>
  );
}

export function SearchSheet({ open, lang, onClose, onPick }: { open: boolean; lang: Lang; onClose: () => void; onPick: (id: string) => void }) {
  const a11y = useA11y();
  const [q, setQ] = useState('');
  const [results, setResults] = useState<Place[]>([]);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (open) requestAnimationFrame(() => input.current?.focus());
    else setQ('');
  }, [open]);
  useEffect(() => {
    let live = true;
    void searchPlaces(q, lang).then((r) => live && setResults(r));
    return () => {
      live = false;
    };
  }, [q, lang]);

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="absolute inset-x-0 bottom-0 top-[40px] z-50 overflow-hidden rounded-t-[28px] border border-white/10"
          style={{ background: 'rgba(10,25,40,0.82)', backdropFilter: 'blur(24px)', WebkitBackdropFilter: 'blur(24px)' }}
          initial={{ y: '100%' }}
          animate={{ y: 0 }}
          exit={{ y: '100%' }}
          transition={{ type: 'spring', damping: 32, stiffness: 320 }}
        >
          <div className="flex items-center gap-3 px-4 pb-3 pt-3">
            <button type="button" aria-label={a11y.close} onClick={onClose} className="grid size-9 place-items-center text-white">
              <ChevronDown size={22} />
            </button>
            <div className="flex h-[44px] flex-1 items-center gap-2 rounded-full bg-white/12 px-4">
              <Search size={18} className="text-white/85" />
              <input
                ref={input}
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={pick(LOCATION_COPY.search, lang)}
                className="ob-input min-w-0 flex-1 bg-transparent text-[16px] text-white outline-none"
              />
              {q && (
                <button type="button" aria-label={a11y.clear} onClick={() => setQ('')} className="text-white/80">
                  <Close size={15} />
                </button>
              )}
            </div>
          </div>
          <div className="h-px bg-white/10" />
          <button
            type="button"
            onClick={() => onPick(CURRENT_PLACE_ID)}
            className="flex w-full items-center gap-3 px-5 py-[14px] text-left text-[15px] text-[#8fb4ff]"
          >
            <span className="grid size-9 place-items-center rounded-full bg-[#2f4f86]/70">
              <Crosshair size={18} />
            </span>
            {pick(LOCATION_COPY.current, lang)}
          </button>
          {results.map((pl) => (
            <button key={pl.id} type="button" onClick={() => onPick(pl.id)} className="flex w-full items-center gap-3 px-5 py-[10px] text-left">
              <span className="grid size-9 shrink-0 place-items-center rounded-full bg-white/10 text-white/85">
                <MapPin size={17} />
              </span>
              <span>
                <span className="block text-[15px] font-bold leading-5 text-white">{pick(pl.name, lang)}</span>
                <span className="block text-[13px] leading-[18px] text-white/60">{pick(pl.sub, lang)}</span>
              </span>
            </button>
          ))}
          {q.trim() && results.length === 0 && <p className="px-5 py-4 text-[14px] text-white/60">{pick(LOCATION_COPY.noResults, lang)}</p>}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

export function WhySheet({ open, lang, onClose }: { open: boolean; lang: Lang; onClose: () => void }) {
  return (
    <AnimatePresence>
      {open && (
        <motion.div className="absolute inset-0 z-50 flex items-end bg-black/40 p-4" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} onClick={onClose}>
          <motion.div
            role="dialog"
            aria-modal
            className="w-full rounded-[28px] bg-white/95 p-6 text-ink"
            initial={{ y: 40 }}
            animate={{ y: 0 }}
            exit={{ y: 40 }}
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-2 text-[18px] font-bold">{pick(LOCATION_COPY.whyTitle, lang)}</div>
            <p className="text-[15px] leading-[22px]">{pick(LOCATION_COPY.whyBody, lang)}</p>
            <button type="button" className="mt-5 h-12 w-full rounded-full bg-ink text-[16px] font-bold text-white" onClick={onClose}>
              {pick(LOCATION_COPY.gotIt, lang)}
            </button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/** Onboarding layout (18-location-ready.png): map card with the search row under the map, then the travel card. */
export function LocationInput({
  value,
  lang,
  onChange,
  onSearchOpenChange,
}: {
  value: Partial<LocationValue> | undefined;
  lang: Lang;
  onChange: (v: LocationValue | undefined) => void;
  onSearchOpenChange: (open: boolean) => void;
}) {
  const place = placeById(value?.placeId);
  const mode = value?.mode ?? DEFAULT_LOCATION.mode;
  const minutes = value?.minutes ?? DEFAULT_LOCATION.minutes;
  return (
    <div className="flex flex-col gap-[14px]">
      <div className="rounded-[28px] border border-white/15 p-3" style={{ background: 'rgba(8,23,33,0.32)', backdropFilter: 'blur(14px)', WebkitBackdropFilter: 'blur(14px)' }}>
        <MapCanvas place={place} mode={mode} minutes={minutes} lang={lang} className="h-[376px] rounded-[20px]" />
        <SearchRow
          className="mt-3"
          place={place}
          lang={lang}
          onOpen={() => onSearchOpenChange(true)}
          onClear={() => onChange(undefined)}
          onLocate={() => onChange({ placeId: CURRENT_PLACE_ID, mode, minutes })}
        />
      </div>
      <TravelControls mode={mode} minutes={minutes} lang={lang} onChange={(v) => place && onChange({ placeId: place.id, ...v })} />
    </div>
  );
}
