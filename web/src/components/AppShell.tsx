import type { ReactNode } from 'react';
import { useLang } from '../i18n/lang';

/** 390×844 mobile frame (full-bleed on phones, centered card on desktop) with the sky/sea background. */
export function AppShell({ children }: { children: ReactNode }) {
  const lang = useLang();
  return (
    <div className="app-root" lang={lang === 'zh' ? 'zh-CN' : 'en'}>
      <main className="app-frame">
        <div className="ditto-bg" aria-hidden />
        {children}
      </main>
    </div>
  );
}
