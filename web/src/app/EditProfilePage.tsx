import { useState } from 'react';
import { getProfile, setPhotos, uploadPhotos } from '../api';
import { PhotoGrid } from '../components/PhotoGrid';
import { Pencil } from '../components/icons';
import { pick, t, useLang, type Lang } from '../i18n/lang';
import type { Answers } from '../onboarding/types';
import { ABOUT_ROWS, TYPE_ROWS, editAria, formatAnswer, rowsFor, type FieldRow } from './profileFields';
import { AppScreen, ProfileRow, TopBar, useGo, useLive } from './ui';

const C = {
  title: t('Profile', '我的资料'),
  back: t('Back to profile', '返回'),
  about: t('Tell Ditto about yourself', '告诉见一面你是谁'),
  type: t('Tell Ditto your type', '告诉见一面你的类型'),
  pics: t('Pics of your vibe', '能代表你 vibe 的照片'),
  yourPhotos: t('Your photos', '你的照片'),
  editPhotos: t('Edit photos', '编辑照片'),
  minPhotos: t('Keep at least 2 photos so we can match you.', '至少保留 2 张照片，才能帮你匹配。'),
};

export function SectionTitle({ children }: { children: string }) {
  return <h2 className="mb-[12px] px-5 text-[14px] font-medium leading-5 text-white [text-shadow:0_1px_4px_rgba(0,0,0,0.25)]">{children}</h2>;
}

export function RowsCard({ rows, answers, lang, onEdit }: { rows: FieldRow[]; answers: Answers; lang: Lang; onEdit: (row: FieldRow) => void }) {
  return (
    <div className="app-card mx-4 py-1">
      {rows.map((row) => (
        <ProfileRow
          key={row.field}
          id={row.field}
          label={pick(row.label, lang)}
          value={formatAnswer(row, answers, lang)}
          ariaLabel={editAria(row, lang)}
          locked={row.locked}
          onClick={() => {
            if (!row.locked) onEdit(row);
          }}
        />
      ))}
    </div>
  );
}

/** `/edit-profile` (04-edit-profile.png, 07-edit-photos.png, 13 §2). */
export function EditProfilePage() {
  const lang = useLang();
  const go = useGo();
  const profile = useLive(getProfile);
  const [editingPhotos, setEditingPhotos] = useState(false);
  const answers = profile?.answers ?? {};
  const photos = profile?.photos ?? [];
  const edit = (row: FieldRow) => go(`/onboarding/edit/${row.field}`);

  return (
    <AppScreen>
      <TopBar title={pick(C.title, lang)} onBack={() => go('/you')} backLabel={pick(C.back, lang)} />
      <div className="ob-scroll absolute inset-x-0 bottom-0 top-[64px]">
        <div className="pb-10 pt-[10px]">
          <SectionTitle>{pick(C.about, lang)}</SectionTitle>
          <RowsCard rows={rowsFor(ABOUT_ROWS, answers, lang)} answers={answers} lang={lang} onEdit={edit} />

          <div className="h-[30px]" />
          <SectionTitle>{pick(C.type, lang)}</SectionTitle>
          <RowsCard rows={rowsFor(TYPE_ROWS, answers, lang)} answers={answers} lang={lang} onEdit={edit} />

          <div className="h-[30px]" />
          <SectionTitle>{pick(C.pics, lang)}</SectionTitle>
          <div className="app-card mx-4 px-5 pb-5 pt-[18px]">
            <div className="mb-[18px] flex items-center justify-between">
              <span className="text-[12px] leading-4 text-white/90">{pick(C.yourPhotos, lang)}</span>
              {!editingPhotos && (
                <button type="button" aria-label={pick(C.editPhotos, lang)} onClick={() => setEditingPhotos(true)} className="-m-2 p-2 text-white/90">
                  <Pencil size={19} strokeWidth={1.7} />
                </button>
              )}
            </div>
            {editingPhotos ? (
              <>
                <PhotoGrid variant="plain" urls={photos} onChange={(urls) => void setPhotos(urls)} onAddFiles={(files) => void uploadPhotos(files)} />
                {photos.length < 2 && <p className="mt-3 text-[13px] leading-[18px] text-[#ffd0e8]">{pick(C.minPhotos, lang)}</p>}
              </>
            ) : (
              <button type="button" onClick={() => setEditingPhotos(true)} className="flex gap-3" aria-label={pick(C.editPhotos, lang)}>
                {photos.map((url, i) => (
                  <span key={i} className="block size-[95px] rounded-[16px] bg-cover bg-center" style={{ backgroundImage: `url("${url}")` }} />
                ))}
              </button>
            )}
          </div>
        </div>
      </div>
    </AppScreen>
  );
}
