// Ad-hoc probe: node scripts/landing/probe.mjs <path> <w> <h> <selector> [offsetTop=80] [out=/tmp/lp/probe.png]
import { createRequire } from 'node:module';

process.env.PLAYWRIGHT_BROWSERS_PATH = '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const [path = '/?lang=en', w = '390', h = '844', sel = '', top = '80', out = '/tmp/lp/probe.png'] = process.argv.slice(2);
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: +w, height: +h } });
page.on('console', (m) => m.type() === 'error' && console.log('console:', m.text()));
page.on('response', (r) => r.status() >= 400 && console.log('http', r.status(), r.url()));
await page.goto(`http://127.0.0.1:5174${path}`, { waitUntil: 'networkidle' });
await page.waitForTimeout(1500);
if (sel) {
  const info = await page.evaluate(
    async ([s, t]) => {
      const el = document.querySelector(s);
      if (!el) return 'missing';
      window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - +t);
      await new Promise((r) => setTimeout(r, 1200));
      return [...el.parentElement.children].map((c) => {
        const r = c.getBoundingClientRect();
        return `${c.className} ${Math.round(r.x)},${Math.round(r.y)} ${Math.round(r.width)}x${Math.round(r.height)} op=${getComputedStyle(c).opacity}`;
      });
    },
    [sel, top],
  );
  console.log(info);
}
await page.screenshot({ path: out });
await browser.close();
