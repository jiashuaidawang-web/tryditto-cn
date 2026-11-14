import { useEffect, useRef } from 'react';

const BAYER8 = [
  0, 32, 8, 40, 2, 34, 10, 42, 48, 16, 56, 24, 50, 18, 58, 26, 12, 44, 4, 36, 14, 46, 6, 38, 60, 28, 52, 20, 62, 30, 54, 22, 3, 35, 11,
  43, 1, 33, 9, 41, 51, 19, 59, 27, 49, 17, 57, 25, 15, 47, 7, 39, 13, 45, 5, 37, 63, 31, 55, 23, 61, 29, 53, 21,
];
const clamp = (v: number) => Math.min(255, Math.max(0, v));

/* tryditto.com `DitheredImage` variant "fine": luminance in 12 steps through an 8×8 Bayer matrix, hue kept by scaling RGB. */
function ditherFine({ data, width, height }: ImageData) {
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      const r = data[i]!;
      const g = data[i + 1]!;
      const b = data[i + 2]!;
      const lum = 0.299 * r + 0.587 * g + 0.114 * b;
      const m = (BAYER8[(y & 7) * 8 + (x & 7)]! + 0.5) / 64;
      const level = clamp((255 * Math.min(11, Math.floor((lum / 255) * 11 + m))) / 11 + (m - 0.5) * 43.35);
      if (lum < 1) {
        data[i] = data[i + 1] = data[i + 2] = Math.round(level);
      } else {
        const k = level / lum;
        data[i] = Math.round(clamp(r * k));
        data[i + 1] = Math.round(clamp(g * k));
        data[i + 2] = Math.round(clamp(b * k));
      }
    }
  }
}

/** Cover-fit `src` at CSS-pixel resolution, dithered. The CSS background shows until the first draw. */
export function DitheredImage({ src, position = 'center', className }: { src: string; position?: 'center' | 'bottom'; className?: string }) {
  const ref = useRef<HTMLCanvasElement>(null);
  useEffect(() => {
    const canvas = ref.current;
    if (!canvas) return;
    const img = new Image();
    let raf = 0;
    let dead = false;
    const draw = () => {
      if (dead || !img.complete || !img.naturalWidth) return;
      const w = Math.max(1, Math.round(canvas.clientWidth));
      const h = Math.max(1, Math.round(canvas.clientHeight));
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      if (!ctx) return;
      const k = Math.max(w / img.naturalWidth, h / img.naturalHeight);
      const dw = img.naturalWidth * k;
      const dh = img.naturalHeight * k;
      ctx.drawImage(img, (w - dw) / 2, position === 'bottom' ? h - dh : (h - dh) / 2, dw, dh);
      const pixels = ctx.getImageData(0, 0, w, h);
      ditherFine(pixels);
      ctx.putImageData(pixels, 0, 0);
    };
    const schedule = () => {
      cancelAnimationFrame(raf);
      raf = requestAnimationFrame(draw);
    };
    img.onload = schedule;
    img.src = src;
    const ro = new ResizeObserver(schedule);
    ro.observe(canvas);
    return () => {
      dead = true;
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, [src, position]);
  return (
    <canvas
      ref={ref}
      className={className}
      aria-hidden
      style={{ backgroundImage: `url(${src})`, backgroundPosition: position, backgroundRepeat: 'no-repeat', backgroundSize: 'cover' }}
    />
  );
}

export interface SectionBg {
  d: string;
  m: string;
  /** Pixel-dithered canvas (home groups 2–5); otherwise a plain image under the soft-light grain. */
  dither?: boolean;
}

/** Ditto's section background: mobile art below 480px (anchored to the bottom), desktop art above. */
export function SectionBackground({ bg }: { bg: SectionBg }) {
  if (bg.dither) {
    return (
      <>
        <DitheredImage src={bg.m} position="bottom" className="lp-bg is-m" />
        <DitheredImage src={bg.d} className="lp-bg is-d" />
      </>
    );
  }
  return (
    <>
      <div className="lp-bg is-m" style={{ backgroundImage: `url(${bg.m})` }} />
      <div className="lp-bg is-d" style={{ backgroundImage: `url(${bg.d})` }} />
      <div className="lp-grain" />
    </>
  );
}
