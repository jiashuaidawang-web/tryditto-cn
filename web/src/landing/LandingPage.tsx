import { useEffect } from 'react';
import { useLang } from '../i18n/lang';
import { BRAND } from './copy';
import { Footer } from './Footer';
import { Hero } from './Hero';
import { LandingHeader } from './LandingHeader';
import { Comparison, Faq, GreatTimes, Group, HowItWorks, Manifesto, Matchmaker, RealDates, Safety } from './Sections';
import './landing.css';

export function LandingPage() {
  const lang = useLang();

  useEffect(() => {
    document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
    document.title = lang === 'zh' ? `${BRAND.zh} · 下周三，去见一面` : `${BRAND.en} · get a date next wednesday`;
  }, [lang]);

  return (
    <div className="lp" lang={lang === 'zh' ? 'zh-CN' : 'en'}>
      <svg width="0" height="0" style={{ position: 'absolute' }} aria-hidden>
        <filter id="lp-rough">
          <feTurbulence type="fractalNoise" baseFrequency="0.06" numOctaves="2" seed="7" />
          <feDisplacementMap in="SourceGraphic" scale="2.2" />
        </filter>
      </svg>
      <LandingHeader />
      <main>
        <Hero />
        <Group bg="lp-how-bg">
          <HowItWorks />
        </Group>
        <Group bg="lp-real-bg" zig>
          <RealDates />
          <Matchmaker />
        </Group>
        <Group bg="lp-night-bg" zig>
          <GreatTimes />
          <Comparison />
        </Group>
        <Group bg="lp-safe-bg" zig>
          <Safety />
          <Faq />
        </Group>
        <Group bg="lp-manifesto-bg" zig>
          <Manifesto />
        </Group>
      </main>
      <Footer />
      <div className="lp-dither" aria-hidden />
    </div>
  );
}
