import { useEffect, useState } from 'react';
import { CURRENT_PLACE_ID, getProfile, placeById, saveWhere } from '../api';
import { DEFAULT_LOCATION, MapCanvas, SearchRow, SearchSheet, TravelControls } from '../components/LocationPicker';
import { pick, t, useLang } from '../i18n/lang';
import type { LocationValue } from '../onboarding/types';
import { AppScreen, DisplayTitle, useLive } from './ui';

const C = {
  title: t('Where would you date?', '你想在哪约会？'),
  saved: t('Saved', '已保存'),
  save: t('Save', '保存'),
};

/** `/where` (02-where.png): map card with search inside, travel card, `Saved` until something changes (08 §02). */
export function WherePage() {
  const lang = useLang();
  const profile = useLive(getProfile);
  const stored = profile?.answers.location as LocationValue | undefined;
  const [draft, setDraft] = useState<Partial<LocationValue> | undefined>(undefined);
  const [searchOpen, setSearchOpen] = useState(false);

  useEffect(() => {
    if (profile && draft === undefined) setDraft(stored ?? { placeId: CURRENT_PLACE_ID, ...DEFAULT_LOCATION });
  }, [profile]); // eslint-disable-line react-hooks/exhaustive-deps

  const place = placeById(draft?.placeId);
  const mode = draft?.mode ?? DEFAULT_LOCATION.mode;
  const minutes = draft?.minutes ?? DEFAULT_LOCATION.minutes;
  const dirty = !!draft && (!stored || stored.placeId !== draft.placeId || stored.mode !== mode || stored.minutes !== minutes);
  const canSave = dirty && !!place;

  return (
    <AppScreen tab="where">
      <DisplayTitle text={pick(C.title, lang)} className="absolute left-5 top-[24px] text-[31px] leading-[38px] text-white" stagger={0.02} />

      <div className="absolute inset-x-3 top-[92px] h-[478px] overflow-hidden rounded-[28px] border border-white/15 shadow-[0_8px_24px_rgba(0,0,0,0.2)]">
        <MapCanvas place={place} mode={mode} minutes={minutes} lang={lang} showCount className="h-full w-full" />
        <SearchRow
          className="absolute inset-x-3 bottom-3"
          place={place}
          lang={lang}
          onOpen={() => setSearchOpen(true)}
          onClear={() => setDraft((d) => ({ ...d, placeId: undefined }))}
          onLocate={() => setDraft((d) => ({ ...d, placeId: CURRENT_PLACE_ID }))}
        />
      </div>

      <div className="absolute inset-x-3 top-[588px]">
        <TravelControls mode={mode} minutes={minutes} lang={lang} onChange={(v) => setDraft((d) => ({ ...d, ...v }))} />
      </div>

      <button
        type="button"
        disabled={!canSave}
        onClick={() => {
          if (!place) return;
          const v: LocationValue = { placeId: place.id, mode, minutes };
          void saveWhere(v);
        }}
        className={`absolute inset-x-3 top-[708px] h-12 rounded-full text-[16px] font-bold transition-colors ${
          canSave ? 'bg-white/95 text-ink' : 'app-glass text-white/55'
        }`}
      >
        {pick(canSave ? C.save : C.saved, lang)}
      </button>

      <SearchSheet
        open={searchOpen}
        lang={lang}
        onClose={() => setSearchOpen(false)}
        onPick={(placeId) => {
          setDraft((d) => ({ ...d, placeId }));
          setSearchOpen(false);
        }}
      />
    </AppScreen>
  );
}
