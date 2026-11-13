// M1 end-to-end check against the real API (jar on :8080, `npm run dev` proxying /api):
//   1. log in with the demo code, answer the whole questionnaire, upload photos, reach /home;
//   2. in a FRESH browser context log in with the same phone: answers, profile and photos come back from the server;
//   3. the API refuses an under-18 birthday.
// Screenshots go to compare/ours/e2e-real/.
//
// usage: node scripts/e2e-real.mjs   (BASE=http://127.0.0.1:5173, CODE=888888 by default)
import { createRequire } from 'node:module';
import { mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.env.BASE ?? 'http://127.0.0.1:5173';
const CODE = process.env.CODE ?? '888888';
const OUT = join(ROOT, 'compare', 'ours', 'e2e-real');
const PHONE = `139${String(Date.now()).slice(-8)}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const check = (ok, what) => {
  console.log(`  ${ok ? '✓' : '✗'} ${what}`);
  if (!ok) process.exitCode = 1;
};

rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });

async function newPage(tag) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(`pageerror ${e.message}`));
  page.on('response', (r) => r.status() >= 400 && r.url().includes('/api/') && errors.push(`${r.status()} ${r.request().method()} ${r.url()}`));
  const shot = async (name, wait = 800) => {
    await sleep(wait);
    await page.screenshot({ path: join(OUT, `${tag}-${name}.png`) });
  };
  return { ctx, page, errors, shot };
}

async function login(page) {
  await page.goto(`${BASE}/login?lang=zh`);
  await page.locator('input[type=tel]').fill(PHONE);
  await page.locator('.lg-send').click();
  await page.getByText('验证码已发送', { exact: false }).or(page.locator('.lg-send:disabled')).first().waitFor({ timeout: 5000 });
  await page.locator('input[autocomplete=one-time-code], input[inputmode=numeric][type=text]').last().fill(CODE);
  await page.locator('input[type=checkbox]').check();
  await page.locator('.lg-submit').click();
  await page.waitForURL(/\/(onboarding|home)/, { timeout: 10000 });
}

const currentStep = (page) =>
  page.evaluate(() => {
    const els = [...document.querySelectorAll('[data-step]')];
    return els.length ? els[els.length - 1].getAttribute('data-step') : null;
  });

const makePhotos = (page) =>
  page.evaluate(() =>
    ['#ff8fb8', '#7fb5ff'].map((c, i) => {
      const cv = document.createElement('canvas');
      cv.width = 1200;
      cv.height = 1600;
      const g = cv.getContext('2d');
      const grad = g.createLinearGradient(0, 0, 1200, 1600);
      grad.addColorStop(0, c);
      grad.addColorStop(1, '#203040');
      g.fillStyle = grad;
      g.fillRect(0, 0, 1200, 1600);
      g.fillStyle = 'rgba(255,255,255,.7)';
      g.beginPath();
      g.arc(600, 660 + i * 80, 300, 0, Math.PI * 2);
      g.fill();
      return cv.toDataURL('image/png').split(',')[1];
    }),
  );

/** Server photo URLs shown on the page (as <img> or CSS background), each fetched to prove it decodes as an image. */
const serverPhotos = (page) =>
  page.evaluate(async () => {
    const urls = new Set();
    for (const el of document.querySelectorAll('*')) {
      const src = el instanceof HTMLImageElement ? el.src : getComputedStyle(el).backgroundImage;
      const m = /\/api\/photos\/\d+\/raw[^")]*/.exec(src ?? '');
      if (m) urls.add(m[0]);
    }
    const ok = [];
    for (const u of urls) {
      const r = await fetch(u);
      const blob = await r.blob();
      if (r.ok && blob.type === 'image/jpeg' && (await createImageBitmap(blob)).width > 0) ok.push(u);
    }
    return ok;
  });

const FILL = {
  name: '林晓',
  birthday: '20040115',
  schoolEmail: 'linxiao@pku.edu.cn',
  personalEmail: 'linxiao@qq.com',
  emailVerify: CODE,
};

// ─── 1. First browser: login → full questionnaire → /home ──────────────────────────────────────────────
console.log(`[1] first login as ${PHONE}, full onboarding`);
const A = await newPage('a');
await login(A.page);
check(A.page.url().includes('/onboarding'), 'new account lands on /onboarding');
const begin = A.page.getByRole('button', { name: /开始|Let's go|Start/ }).first();
if (await begin.count()) await begin.click().catch(() => {});

const order = [];
let prev = null;
for (let guard = 0; guard < 70; guard++) {
  let step = await currentStep(A.page);
  const t0 = Date.now();
  while ((step === prev || step === null) && Date.now() - t0 < 8000) {
    if (A.page.url().includes('/home')) break;
    if (step === null) {
      const go = A.page.getByRole('button', { name: /开始|Let's go|Get started/ }).first();
      if (await go.count()) await go.click().catch(() => {});
    }
    await sleep(80);
    step = await currentStep(A.page);
  }
  if (A.page.url().includes('/home')) break;
  if (step === prev) {
    await A.shot(`stuck-${prev}`, 0);
    throw new Error(`stuck after ${prev}; errors: ${A.errors.join(' | ')}`);
  }
  prev = step;
  order.push(step);
  await sleep(1300);

  if (FILL[step]) await A.page.locator('input:not([type=file]):not([type=range])').last().fill(FILL[step]);
  if (step === 'photos') {
    const data = await makePhotos(A.page);
    await A.page
      .locator('input[type=file]')
      .first()
      .setInputFiles(data.map((b, i) => ({ name: `p${i}.png`, mimeType: 'image/png', buffer: Buffer.from(b, 'base64') })));
    const t1 = Date.now();
    while ((await serverPhotos(A.page)).length < 2) {
      if (Date.now() - t1 > 20000) throw new Error(`photos never reached the server; errors: ${A.errors.join(' | ')}`);
      await sleep(300);
    }
    console.log(`  photos uploaded and served back in ${Date.now() - t1} ms`);
    await A.shot('photos-uploaded', 300);
  }
  if (step === 'location') await A.page.getByRole('button', { name: 'Use my current location' }).click();
  if (step === 'height') {
    await A.page.getByRole('slider', { name: 'Height' }).focus();
    for (let k = 0; k < 8; k++) await A.page.keyboard.press('ArrowRight');
  }
  if (step === 'height') {
    await A.page.getByRole('slider', { name: 'Height' }).focus();
    for (let k = 0; k < 8; k++) await A.page.keyboard.press('ArrowRight');
  }
  if (step === 'datingHelp') {
    await A.page.getByRole('checkbox').first().click();
    await A.page.waitForURL(/\/home/, { timeout: 20000 });
    break;
  }
  await sleep(400);
  const next = A.page.locator('[aria-label="Next"][aria-disabled="false"], [aria-label="Send"][aria-disabled="false"]').last();
  for (let k = 0; k < 3 && !(await next.count()); k++) {
    const radio = A.page.getByRole('radio');
    const box = A.page.getByRole('checkbox');
    if (await radio.count()) await radio.first().click();
    else if (await box.count()) await box.nth(k).click();
    else if (await A.page.locator('textarea').count()) await A.page.locator('textarea').last().fill('真诚，好奇心');
    else if (await A.page.locator('input:not([type=file]):not([type=range])').count())
      await A.page.locator('input:not([type=file]):not([type=range])').last().fill('测试');
    await sleep(400);
  }
  if (await next.count()) await next.click({ timeout: 5000 });
  else if (step !== 'emailVerify') {
    await A.shot(`no-next-${step}`, 0);
    throw new Error(`no enabled Next on ${step}; errors: ${A.errors.join(' | ')}`);
  }
}
console.log(`  steps (${order.length}): ${order.join(' → ')}`);
await A.page.waitForURL(/\/home/, { timeout: 20000 });
await A.shot('home', 2500);
check(A.page.url().includes('/home'), 'reached /home');

const answersA = await A.page.evaluate(() => JSON.parse(localStorage.getItem('jym-onboarding-v1')).state.answers);
const tokenA = await A.page.evaluate(() => JSON.parse(localStorage.getItem('jym-auth-v1') ?? '{}').state?.token);

await A.page.goto(`${BASE}/blocklist?lang=zh`);
await sleep(1200);
await A.page.getByRole('button', { name: '添加屏蔽' }).first().click();
const sheet = A.page.getByRole('dialog');
await sheet.locator('input').first().fill('高中同桌');
await sheet.locator('input').nth(1).fill('jamie@example.com');
await sheet.getByRole('button', { name: '屏蔽', exact: true }).click();
await A.shot('blocklist', 1200);
if (A.errors.length) console.log('  first-browser API/page errors:\n   ', A.errors.join('\n    '));

// ─── 2. Fresh browser context, same phone ──────────────────────────────────────────────────────────────
console.log('[2] fresh browser context, same phone');
const B = await newPage('b');
await login(B.page);
check(B.page.url().includes('/home'), `returning user lands on /home (got ${new URL(B.page.url()).pathname})`);
await B.shot('home', 2000);
const answersB = await B.page.evaluate(() => JSON.parse(localStorage.getItem('jym-onboarding-v1')).state.answers);
// The verification code is checked, never stored; `photos` is a local count. MySQL JSON reorders object keys.
const canon = (v) =>
  JSON.stringify(v, (_, x) => (x && typeof x === 'object' && !Array.isArray(x) ? Object.fromEntries(Object.entries(x).sort()) : x));
const keysA = Object.keys(answersA).filter((k) => k !== 'photos' && k !== 'emailVerify').sort();
const missing = keysA.filter((k) => canon(answersA[k]) !== canon(answersB[k]));
check(missing.length === 0, `all ${keysA.length} answers restored from the server${missing.length ? ` — differ: ${missing.join(', ')}` : ''}`);
for (const k of missing) console.log(`    ${k}: first=${canon(answersA[k])} fresh=${canon(answersB[k])}`);

await B.page.goto(`${BASE}/edit-profile?lang=zh`);
await sleep(2000);
const body = await B.page.locator('body').innerText();
check(body.includes('林晓'), 'edit-profile shows the name typed in the first browser');
const photosB = await serverPhotos(B.page);
check(photosB.length >= 2, `edit-profile renders ${photosB.length} server photos that decode as JPEG`);
await B.shot('edit-profile', 300);

await B.page.goto(`${BASE}/blocklist?lang=zh`);
await sleep(1500);
check((await B.page.locator('body').innerText()).includes('高中同桌'), 'blocklist entry from the first browser is listed');
await B.shot('blocklist', 300);

await B.page.goto(`${BASE}/onboarding/edit/name?lang=zh`);
await sleep(1500);
await B.page.locator('input').last().fill('林晓晓');
await B.page.locator('[aria-label="Next"][aria-disabled="false"], [aria-label="Send"][aria-disabled="false"], button:has-text("完成")').last().click();
await sleep(1500);
const me = await (await fetch(`${BASE}/api/profile`, { headers: { Authorization: `Bearer ${tokenA}` } })).json();
check(me.answers?.name === '林晓晓', `profile edit in the second browser is visible to the first session (name=${me.answers?.name})`);
const api = async (path, token) => {
  const r = await fetch(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } });
  return { status: r.status, body: r.status === 200 ? await r.json() : null };
};

// Every logged-in page renders without page errors or failed API calls (the match loop is still local in M1).
const before = B.errors.length;
for (const path of ['/home', '/where', '/you', '/edit-profile', '/deep-info', '/blocklist', '/account', '/onboarding/edit/intention', '/onboarding/edit/hobbyRanking']) {
  await B.page.goto(`${BASE}${path}?lang=zh`);
  await sleep(1200);
  const at = new URL(B.page.url()).pathname;
  check(at === path, `${path} renders${at === path ? '' : ` (redirected to ${at})`}`);
}
const sweepErrors = B.errors.slice(before);
check(sweepErrors.length === 0, `page sweep: no page errors or failed API calls${sweepErrors.length ? ` — ${sweepErrors.join(' | ')}` : ''}`);

// Change the dating area on /where through the search sheet.
await B.page.goto(`${BASE}/where?lang=zh`);
await sleep(1200);
await B.page.getByRole('button', { name: /五道口/ }).first().click();
await sleep(500);
await B.page.locator('input.ob-input').last().fill('三里屯');
await B.page.getByRole('button', { name: /三里屯/ }).last().click();
await B.page.getByRole('button', { name: '保存', exact: true }).click();
await sleep(1000);
const where = (await api('/api/profile', tokenA)).body?.answers?.location;
check(where?.placeId === 'sanlitun', `/where saves the new area to the server (placeId=${where?.placeId})`);
await B.shot('where', 200);

// Deactivate in the UI: every session ends; logging back in reactivates the account.
await B.page.goto(`${BASE}/account?lang=zh`);
await sleep(1200);
await B.page.getByRole('button', { name: /停用账号/ }).first().click();
await sleep(600);
await B.page.getByRole('dialog').getByRole('button', { name: '停用账号', exact: true }).click();
await B.page.waitForURL((u) => u.pathname === '/', { timeout: 5000 });
check(true, 'deactivate returns to the landing page');
check((await api('/api/me', tokenA)).status === 401, 'deactivate revoked the first browser session too (GET /api/me → 401)');
await sleep(61000 - (Date.now() % 1000)); // SMS send cooldown for the same phone
await login(B.page);
const tokenB = await B.page.evaluate(() => JSON.parse(localStorage.getItem('jym-auth-v1') ?? '{}').state?.token);
const meB = (await api('/api/me', tokenB)).body;
check(B.page.url().includes('/home') && meB?.status === 'active', `logging back in reactivates the account (status=${meB?.status}, at ${new URL(B.page.url()).pathname})`);

const other = B.errors.filter((e) => !e.startsWith('429 '));
if (other.length) console.log('  second-browser API/page errors:\n   ', other.join('\n    '));

// ─── 3. Under-18 birthday is rejected by the API ────────────────────────────────────────────────────────
console.log('[3] under-18 birthday');
const minor = `139${String(Date.now() + 7).slice(-8)}`;
await fetch(`${BASE}/api/auth/sms/send`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phone: minor }) });
const { token } = await (
  await fetch(`${BASE}/api/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ phone: minor, code: CODE }) })
).json();
const young = new Date();
young.setFullYear(young.getFullYear() - 17);
const res = await fetch(`${BASE}/api/onboarding/answers/birthday`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  body: JSON.stringify({ value: young.toISOString().slice(0, 10) }),
});
const problem = await res.json();
check(res.status === 422 && problem.code === 'UNDER_AGE', `PUT birthday ${young.toISOString().slice(0, 10)} → ${res.status} ${problem.code}`);

await browser.close();
console.log(process.exitCode ? 'E2E FAILED' : 'E2E PASSED');
