import { useA11y } from '../i18n/a11y';

/** 220×220 rounded tile; appears without fade once loaded (12 §2). */
export function IntroIllustration() {
  return (
    <img
      src="/brand/pigeon-preferences.jpg"
      alt=""
      aria-hidden
      draggable={false}
      className="block size-[220px] rounded-[28px] object-cover"
    />
  );
}

export function Spinner() {
  const a11y = useA11y();
  return (
    <span
      className="block size-7 rounded-full border-[2.5px] border-white/85 border-t-transparent"
      style={{ animation: 'ob-spin 0.8s linear infinite' }}
      aria-label={a11y.loading}
    />
  );
}
