import { useRef } from 'react';
import { Close, Plus } from './icons';

export const PHOTO_ACCEPT = 'image/jpeg,image/png,image/webp,image/bmp,image/heic,image/heif,.heic,.heif';

/**
 * Photos page grid (N3/20-a): 3×2 tiles in a dark box. Filled tiles get a grey × badge,
 * the first empty slot is a light "+" tile, the rest are dashed. Files stay local as object URLs
 * unless `onAddFiles` takes over (the api layer persists them).
 * `variant="plain"` drops the dark dock for the in-card grid on /edit-profile (07-edit-photos).
 */
export function PhotoGrid({
  urls,
  onChange,
  onAddFiles,
  variant = 'dock',
  max = 6,
}: {
  urls: string[];
  onChange: (urls: string[]) => void;
  onAddFiles?: (files: File[]) => void;
  variant?: 'dock' | 'plain';
  max?: number;
}) {
  const file = useRef<HTMLInputElement>(null);
  const slots = Array.from({ length: max }, (_, i) => urls[i]);

  return (
    <div className={variant === 'dock' ? 'ob-dock rounded-[24px] px-4 py-4' : ''}>
      <input
        ref={file}
        type="file"
        multiple
        hidden
        aria-label="Add photos"
        accept={PHOTO_ACCEPT}
        onChange={(e) => {
          const files = Array.from(e.target.files ?? []);
          if (onAddFiles) onAddFiles(files);
          else onChange([...urls, ...files.map((f) => URL.createObjectURL(f))].slice(0, max));
          e.target.value = '';
        }}
      />
      <div className="grid grid-cols-3 gap-x-3 gap-y-3">
        {slots.map((url, i) =>
          url ? (
            <div key={i} className="relative aspect-square rounded-[16px] bg-cover bg-center" style={{ backgroundImage: `url("${url}")` }}>
              <button
                type="button"
                aria-label="Remove photo"
                onClick={() => onChange(urls.filter((_, j) => j !== i))}
                className="absolute -right-[6px] -top-[6px] grid size-6 place-items-center rounded-full bg-[#8e9aa6] text-[#1d2833]"
              >
                <Close size={13} />
              </button>
            </div>
          ) : i === urls.length ? (
            <button
              key={i}
              type="button"
              aria-label="Add photo"
              onClick={() => file.current?.click()}
              className="grid aspect-square place-items-center rounded-[16px] bg-[#c6d2e0]/90 text-ink"
            >
              <Plus size={34} />
            </button>
          ) : (
            <div key={i} className="aspect-square rounded-[16px] border-2 border-dashed border-white/30" />
          ),
        )}
      </div>
    </div>
  );
}
