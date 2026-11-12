// Login smoke test: node scripts/landing/login-flow.mjs [baseUrl=http://127.0.0.1:5174]
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';

process.env.PLAYWRIGHT_BROWSERS_PATH = '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const base = process.argv[2] ?? 'http://127.0.0.1:5174';
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });

await page.goto(`${base}/`);
const cta = page.locator('.lp-hero-cta');
assert.equal(await cta.getAttribute('href'), '/login');

await page.goto(`${base}/login`);
const send = page.locator('.lg-send');
await page.fill('input[type=tel]', '1234');
await send.click();
assert.match(await page.locator('.lg-msg').innerText(), /手机号/);

await page.fill('input[type=tel]', '13800138000');
await send.click();
await page.waitForFunction(() => /\d+s/.test(document.querySelector('.lg-send')?.textContent ?? ''));
const first = await send.innerText();
assert.ok(await send.isDisabled(), 'send disabled during countdown');
await page.waitForTimeout(2100);
const later = await send.innerText();
assert.ok(parseInt(later) < parseInt(first), `countdown ticks ${first} -> ${later}`);

await page.fill('input[autocomplete=one-time-code]', '123456');
await page.click('.lg-submit');
assert.match(await page.locator('.lg-msg').innerText(), /协议|agree/i, 'agreement required');
await page.check('.lg-agree input');
await page.click('.lg-submit');
await page.waitForFunction(() => document.querySelector('.lg-msg')?.textContent);
assert.ok(page.url().endsWith('/login'), 'wrong code stays');

await page.fill('input[autocomplete=one-time-code]', '888888');
await page.click('.lg-submit');
await page.waitForURL(/\/onboarding/);
const stored = await page.evaluate(() => localStorage.getItem('jym-auth-v1'));
assert.match(stored, /13800138000/);

await page.goto(`${base}/`);
assert.equal(await page.locator('.lp-hero-cta').getAttribute('href'), '/onboarding');
console.log('cta after login:', await page.locator('.lp-hero-cta').innerText(), '| header:', await page.locator('.lp-header').innerText());
await browser.close();
console.log('login flow ok');
