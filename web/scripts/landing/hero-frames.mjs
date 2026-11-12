// Records the hero opening and grabs key frames aligned to Ditto's T0, then builds side-by-sides.
// Ditto frames (docs/research/assets/ditto-hero/*-tN.png) are named by video time since navigation;
// their title first shows at T0 = 3410ms (docs/research/06). Ours: window.__heroT0 (epoch ms).
// Usage: node scripts/landing/hero-frames.mjs [baseUrl=http://127.0.0.1:5174]
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, renameSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH = '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const web = join(dirname(fileURLToPath(import.meta.url)), '../..');
const ref = join(web, '../docs/research/assets/ditto-hero');
const outDir = join(web, 'compare/landing/hero');
const sbsDir = join(web, 'compare/landing/side-by-side');
mkdirSync(outDir, { recursive: true });
mkdirSync(sbsDir, { recursive: true });

const base = process.argv[2] ?? 'http://127.0.0.1:5174';
const DITTO_T0 = 3410;
const FRAMES = [3500, 4000, 4500, 5000, 5500, 6500, 8000, 10000, 12000, 14000, 16000, 18000, 20000];

async function record({ name, viewport, reduced, frames, lang }) {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const vidDir = join(outDir, `_video-${name}`);
  const context = await browser.newContext({
    viewport,
    deviceScaleFactor: 1,
    reducedMotion: reduced ? 'reduce' : 'no-preference',
    recordVideo: { dir: vidDir, size: viewport },
  });
  const page = await context.newPage();
  await page.goto(`${base}/?lang=${lang}`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => typeof window.__heroT0 === 'number');
  const t0 = await page.evaluate(() => window.__heroT0);
  const shots = [];
  for (const n of frames) {
    const at = t0 + (n - DITTO_T0);
    const wait = at - (await page.evaluate(() => Date.now()));
    if (wait > 0) await page.waitForTimeout(wait);
    const file = join(outDir, `${name}-t${n}.png`);
    await page.screenshot({ path: file });
    shots.push([n, file]);
  }
  const video = page.video();
  await context.close();
  await browser.close();
  if (video) {
    const p = await video.path();
    renameSync(p, join(outDir, `${name}.webm`));
    rmSync(vidDir, { recursive: true, force: true });
  }
  return shots;
}

function sideBySide(refName, shots, viewport) {
  const pairs = shots.filter(([n]) => existsSync(join(ref, `${refName}-t${n}.png`)));
  for (const [n, file] of pairs) {
    execFileSync('ffmpeg', [
      '-v', 'error', '-y', '-i', join(ref, `${refName}-t${n}.png`), '-i', file,
      '-filter_complex', `[0]scale=${viewport.width}:${viewport.height}[a];[1]pad=iw+16:ih:16:0:white[b];[a][b]hstack`,
      join(outDir, `sbs-${refName}-t${n}.png`),
    ]);
  }
  // One contact sheet per run: Ditto row on top, ours below.
  const inputs = pairs.flatMap(([n]) => ['-i', join(outDir, `sbs-${refName}-t${n}.png`)]);
  const scaled = pairs.map((_, i) => `[${i}]scale=-1:420[s${i}]`).join(';');
  const stack = pairs.map((_, i) => `[s${i}]`).join('');
  const cols = Math.ceil(pairs.length / 2);
  const layout = pairs.map((_, i) => `${i % cols === 0 ? '0' : Array.from({ length: i % cols }, (_, k) => `w${k}`).join('+')}_${i < cols ? '0' : 'h0'}`).join('|');
  execFileSync('ffmpeg', [
    '-v', 'error', '-y', ...inputs,
    '-filter_complex', `${scaled};${stack}xstack=inputs=${pairs.length}:layout=${layout}:fill=white`,
    join(sbsDir, `hero-${refName}-keyframes.png`),
  ]);
}

const mobile = { width: 390, height: 844 };
const desktop = { width: 1440, height: 900 };
const m = await record({ name: 'mobile', viewport: mobile, frames: FRAMES, lang: 'en' });
sideBySide('mobile', m, mobile);
const d = await record({ name: 'desktop', viewport: desktop, frames: [6500, 8000, 10000, 12000, 16000], lang: 'en' });
sideBySide('desktop', d, desktop);
const r = await record({ name: 'reduced-desktop', viewport: desktop, reduced: true, frames: [3500, 4500], lang: 'en' });
execFileSync('ffmpeg', [
  '-v', 'error', '-y', '-i', join(ref, 'reduced-desktop-t4500.png'), '-i', r[1][1],
  '-filter_complex', '[0]scale=1440:900[a];[1]pad=iw:ih+16:0:16:white[b];[a][b]vstack,scale=iw/2:-1',
  join(sbsDir, 'hero-reduced-desktop-t4500.png'),
]);
await record({ name: 'mobile-zh', viewport: mobile, frames: [5000, 8000, 12000], lang: 'zh' });
console.log('ok', outDir);
