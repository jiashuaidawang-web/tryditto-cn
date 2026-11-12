import { useRef } from 'react';
import { Close, Plus } from './icons';

/**
 * Photos page grid (N3/20-a): 3×2 tiles in a dark box. Filled tiles get a grey × badge,
 * the first empty slot is a light "+" tile, the rest are dashed. UI only — files stay in memory as object URLs.
 */
export function PhotoGrid({ urls, onChange }: { urls: string[]; onChange: (urls: string[]) => void }) {
  const file = useRef<HTMLInputElement>(null);
  const slots = Array.from({ length: 6 }, (_, i) => urls[i]);

  return (
    <div className="ob-dock rounded-[24px] px-4 py-4">
      <input
        ref={file}
        type="file"
        multiple
        hidden
        accept="image/jpeg,image/png,image/webp,image/bmp,image/heic,image/heif,.heic,.heif"
        onChange={(e) => {
          const picked = Array.from(e.target.files ?? []).map((f) => URL.createObjectURL(f));
          onChange([...urls, ...picked].slice(0, 6));
          e.target.value = '';
        }}
      />
      <div className="grid grid-cols-3 gap-x-3 gap-y-3">
        {slots.map((url, i) =>
          url ? (
            <div key={i} className="relative aspect-square rounded-[16px] bg-cover bg-center" style={{ backgroundImage: `url(${url})` }}>
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
