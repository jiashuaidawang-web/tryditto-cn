import { useRef } from 'react';
import { getProfile, setPhotos, uploadPhotos } from '../api';
import { PHOTO_ACCEPT } from '../components/PhotoGrid';
import { Close, Plus } from '../components/icons';
import { pick, t, useLang } from '../i18n/lang';
import { RowsCard } from './EditProfilePage';
import { DEEP_ROWS, rowsFor } from './profileFields';
import { AppScreen, TopBar, useGo, useLive } from './ui';

const C = {
  title: t('Deep Info', '深度信息'),
  back: t('Back to profile', '返回'),
  look: t("The look you're into", '你喜欢的长相'),
  lookBody: t('Drop a celeb crush or someone whose vibe you love', '放一张明星或你很喜欢的 vibe 的照片'),
  lookNote: t('Only used to match you. Nobody else sees these.', '只用于匹配，任何人都看不到。'),
};

/**
 * `/deep-info` (09-deep-info.png, 13 §5): religion / politics rows + "The look you're into" (idealDateImages).
 * zh drops the religion & politics rows (08 §08: 中国版删掉宗教和政治), leaving the ideal-look card.
 */
export function DeepInfoPage() {
  const lang = useLang();
  const go = useGo();
  const profile = useLive(getProfile);
  const file = useRef<HTMLInputElement>(null);
  const answers = profile?.answers ?? {};
  const ideal = profile?.idealPhotos ?? [];
  const rows = rowsFor(DEEP_ROWS, answers, lang);

  return (
    <AppScreen>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/you')} backLabel={pick(C.back, lang)} />
      <div className="ob-scroll absolute inset-x-0 bottom-0 top-[80px] pb-10">
        {rows.length > 0 && (
          <div className="mb-[33px]">
            <RowsCard rows={rows} answers={answers} lang={lang} onEdit={(row) => go(`/onboarding/edit/${row.field}`)} />
          </div>
        )}
        <div className="app-card mx-4 px-4 py-6">
          <h2 className="text-[16px] font-normal leading-6">{pick(C.look, lang)}</h2>
          <p className="mt-[6px] text-[14px] leading-5 text-white/90">{pick(C.lookBody, lang)}</p>
          <input
            ref={file}
            id="photo"
            type="file"
            multiple
            hidden
            aria-label="Add photos"
            accept={PHOTO_ACCEPT}
            onChange={(e) => {
              void uploadPhotos(Array.from(e.target.files ?? []), 'idealPhotos');
              e.target.value = '';
            }}
          />
          <div className="mt-[18px] flex flex-wrap gap-3">
            {ideal.map((url, i) => (
              <div key={i} className="relative size-[98px] rounded-[16px] bg-cover bg-center" style={{ backgroundImage: `url("${url}")` }}>
                <button
                  type="button"
                  aria-label="Remove photo"
                  onClick={() => void setPhotos(ideal.filter((_, j) => j !== i), 'idealPhotos')}
                  className="absolute -right-[6px] -top-[6px] grid size-6 place-items-center rounded-full bg-[#8e9aa6] text-[#1d2833]"
                >
                  <Close size={13} />
                </button>
              </div>
            ))}
            {ideal.length < 6 && (
              <button type="button" aria-label="Add photos" onClick={() => file.current?.click()} className="grid size-[98px] place-items-center rounded-[16px] bg-white/70 text-ink">
                <Plus size={40} strokeWidth={1.6} />
              </button>
            )}
          </div>
          {lang === 'zh' && <p className="mt-4 text-[12px] leading-4 text-white/70">{pick(C.lookNote, lang)}</p>}
        </div>
      </div>
    </AppScreen>
  );
}
