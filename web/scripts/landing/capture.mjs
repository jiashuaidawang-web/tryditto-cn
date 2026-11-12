// Screenshots of the landing at the same scroll anchors as docs/research/assets/ditto/site-*.png,
// plus full-page shots, then ffmpeg side-by-sides (Ditto left, ours right).
// Usage: node scripts/landing/capture.mjs [baseUrl=http://localhost:5174] [mobile|desktop|all]
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH = '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const web = join(dirname(fileURLToPath(import.meta.url)), '../..');
const ditto = join(web, '../docs/research/assets/ditto');
const oursDir = join(web, 'compare/landing/ours');
const sbsDir = join(web, 'compare/landing/side-by-side');
mkdirSync(oursDir, { recursive: true });
mkdirSync(sbsDir, { recursive: true });

const base = process.argv[2] ?? 'http://127.0.0.1:5174';
const only = process.argv[3] ?? 'all';

/** [ditto name, anchor selector, nth match, y the anchor's top has in Ditto's shot, click first FAQ?] */
const MOBILE = [
  ['hero', null, 0, 0],
  ['how-it-works', '.lp-chip', 0, 70],
  ['how-it-works-lower', '.lp-step h3', 3, 285],
  ['photos', '.lp-chip', 1, 72],
  ['matchmaker', '.lp-match-item h3', 0, 118],
  ['great-times', '.lp-chip', 4, 668],
  ['comparison', '.lp-compare-col', 0, 128],
  ['safety', '.lp-chip', 6, 122],
  ['faq', '.lp-chip', 7, 118],
  ['faq-open', '.lp-chip', 7, 330, true],
  ['manifesto-cta', '.lp-mani-line', 0, 383],
  ['footer', '.lp-footer', 0, 340],
  ['footer-bottom', '.lp-footer', 0, 133],
];
const DESKTOP = [
  ['hero', null, 0, 0],
  ['how-it-works', '.lp-chip', 0, 101],
  ['photos', '.lp-chip', 1, 150],
  ['great-times', '.lp-chip', 3, 146],
  ['comparison', '.lp-chip', 4, 124],
  ['safety', '.lp-chip', 6, 146],
  ['faq', '.lp-chip', 7, 130],
  ['footer', '.lp-footer', 0, 460],
];

async function run(kind, viewport, shots) {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage({ viewport, deviceScaleFactor: 1 });
  await page.goto(`${base}/?lang=en`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(9500);
  // Walk the page once so every scroll reveal has fired.
  const h = await page.evaluate(() => document.documentElement.scrollHeight);
  for (let y = 0; y < h; y += viewport.height / 2) {
    await page.evaluate((v) => window.scrollTo(0, v), y);
    await page.waitForTimeout(60);
  }
  await page.waitForTimeout(900);
  for (const [name, sel, nth, y, openFaq] of shots) {
    if (openFaq) {
      await page.evaluate(() => document.querySelector('.lp-faq-q')?.click());
      await page.waitForTimeout(600);
    }
    await page.evaluate(
      ([s, n, top]) => {
        if (!s) return window.scrollTo(0, 0);
        const el = document.querySelectorAll(s)[n];
        if (el) window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - top);
      },
      [sel, nth, y],
    );
    await page.waitForTimeout(900);
    const out = join(oursDir, `${kind}-${name}.png`);
    await page.screenshot({ path: out });
    if (openFaq) await page.evaluate(() => document.querySelector('.lp-faq-q')?.click());
    const ref = join(ditto, `site-${kind}-${name}.png`);
    if (existsSync(ref)) {
      execFileSync('ffmpeg', [
        '-v', 'error', '-y', '-i', ref, '-i', out,
        '-filter_complex',
        kind === 'desktop'
          ? `[0]scale=${viewport.width}:${viewport.height}[a];[1]pad=iw:ih+16:0:16:white[b];[a][b]vstack`
          : `[0]scale=${viewport.width}:${viewport.height}[a];[1]pad=iw+16:ih:16:0:white[b];[a][b]hstack`,
        join(sbsDir, `${kind}-${name}.png`),
      ]);
    }
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(400);
  const full = join(oursDir, `${kind}-full.png`);
  await page.screenshot({ path: full, fullPage: true });
  const ref = join(ditto, `site-${kind}-full.png`);
  execFileSync('ffmpeg', [
    '-v', 'error', '-y', '-i', ref, '-i', full,
    '-filter_complex', `[0]scale=${viewport.width}:-1[a];[a][1]xstack=inputs=2:layout=0_0|w0+16_0:fill=white`,
    join(sbsDir, `${kind}-full.png`),
  ]);
  await browser.close();
}

async function fullZh(kind, viewport) {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const page = await browser.newPage({ viewport, deviceScaleFactor: 1 });
  await page.goto(`${base}/`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(9500);
  const h = await page.evaluate(() => document.documentElement.scrollHeight);
  for (let y = 0; y < h; y += viewport.height / 2) {
    await page.evaluate((v) => window.scrollTo(0, v), y);
    await page.waitForTimeout(60);
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(900);
  await page.screenshot({ path: join(oursDir, `${kind}-zh-full.png`), fullPage: true });
  await browser.close();
}

if (only !== 'desktop') await fullZh('mobile', { width: 390, height: 844 });
if (only !== 'desktop') await run('mobile', { width: 390, height: 844 }, MOBILE);
if (only !== 'mobile') await run('desktop', { width: 1440, height: 900 }, DESKTOP);
console.log('ok', oursDir, sbsDir);
