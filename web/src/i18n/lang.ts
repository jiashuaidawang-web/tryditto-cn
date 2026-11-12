import { useSearchParams } from 'react-router';

export type Lang = 'en' | 'zh';

/** Every user-visible string carries the verbatim Ditto copy (`en`) and the localized copy (`zh`). */
export interface T {
  en: string;
  zh: string;
}

export const t = (en: string, zh: string): T => ({ en, zh });

export function parseLang(value: string | null): Lang {
  return value === 'en' ? 'en' : 'zh';
}

export function useLang(): Lang {
  const [params] = useSearchParams();
  return parseLang(params.get('lang'));
}

/** Keep `?lang=` (and other debug flags) when navigating between routes. */
export function useQuerySuffix(): string {
  const [params] = useSearchParams();
  const s = params.toString();
  return s ? `?${s}` : '';
}

export const pick = (copy: T, lang: Lang) => copy[lang];
