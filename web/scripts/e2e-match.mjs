// M2 match loop against the real API (jar on :8080, `npm run dev` proxying /api).
// Covers: demo login → home card → pick a slot → partner confirms → check-in →
// both want to meet again → WeChat; decline; no overlapping slots; report;
// a real user never sees a synthetic partner. Birthday stays locked on the edit page.
//
// usage: node scripts/e2e-match.mjs
//   BASE=http://127.0.0.1:5173  API=http://127.0.0.1:8080  CODE=888888
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.env.BASE ?? 'http://127.0.0.1:5173';
const API = process.env.API ?? 'http://127.0.0.1:8080';
const CODE = process.env.CODE ?? '888888';
const OUT = join(ROOT, 'compare', 'ours', 'e2e-match');
const DEMOS = ['13800000001', '13800000002', '13800000003', '13800000004'];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const check = (ok, what) => {
  console.log(`  ${ok ? '✓' : '✗'} ${what}`);
  if (!ok) process.exitCode = 1;
};

const PNG = join(OUT, 'face.png');
mkdirSync(OUT, { recursive: true });
execFileSync('python3', ['-c', `
import struct, zlib
w = h = 64
def chunk(t, d):
    return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
raw = b''.join(b'\\x00' + bytes([255, 140, 180]) * w for _ in range(h))
png = b'\\x89PNG\\r\\n\\x1a\\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b'')
open(${JSON.stringify(PNG)}, 'wb').write(png)
`]);
const png = readFileSync(PNG);

async function api(method, path, token, body, raw) {
  const headers = {};
  if (token) headers.authorization = `Bearer ${token}`;
  if (body !== undefined && !raw) headers['content-type'] = 'application/json';
  if (raw) headers['content-type'] = raw;
  const r = await fetch(`${API}${path}`, { method, headers, body: body === undefined ? undefined : raw ? body : JSON.stringify(body) });
  const text = await r.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 300) }; }
  return { status: r.status, json };
}

async function login(phone) {
  const r = await api('POST', '/api/auth/login', null, { phone, code: CODE });
  if (r.status !== 200) throw new Error(`login ${phone} ${r.status} ${JSON.stringify(r.json)}`);
  return r.json.token;
}

async function answer(token, qid, value) {
  const r = await api('PUT', `/api/onboarding/answers/${qid}`, token, { value });
  if (r.status !== 204) throw new Error(`answer ${qid} ${r.status} ${JSON.stringify(r.json)}`);
}

async function upload(token) {
  const ticket = await api('POST', '/api/photos/upload-url', token, { kind: 'profile', contentType: 'image/png', bytes: png.length });
  if (ticket.status !== 200) throw new Error(`upload-url ${ticket.status} ${JSON.stringify(ticket.json)}`);
  const put = await api('PUT', ticket.json.url, token, png, 'image/png');
  if (put.status !== 204) throw new Error(`put photo ${put.status}`);
  const fin = await api('POST', '/api/photos/finalize', token, { uploadId: ticket.json.uploadId });
  if (fin.status !== 200) throw new Error(`finalize ${fin.status} ${JSON.stringify(fin.json)}`);
}

async function onboard(phone, gender, wants, email, name) {
  const token = await login(phone);
  const steps = [
    ['isStudent', 'no'],
    ['name', name],
    ['gender', gender],
    ['expectedGender', wants],
    ['birthday', '1986-03-02'],
    ['hobbies', ['music', 'reading']],
    ['musicSound', ['indie']],
    ['readingGenres', ['scifi']],
    ['location', { placeId: 'wudaokou', mode: 'transit', minutes: 45 }],
    ['expectedAgeRange', [35, 50]],
    ['relationshipLooking', ['serious']],
    ['matchingStyle', 'steady'],
    ['occupation', '工程师'],
    ['educationLevel', 'college'],
    ['educationImportance', 'none'],
    ['firstDateBudget', '20to50'],
    ['personalEmail', email],
    ['religion', ['buddhist']],
  ];
  for (const [q, v] of steps) await answer(token, q, v);
  const verified = await api('POST', '/api/auth/email/verify', token, { email, code: CODE });
  if (!verified.json?.verified) throw new Error(`email verify ${JSON.stringify(verified.json)}`);
  await answer(token, 'emailVerify', CODE);
  await upload(token);
  await upload(token);
  const done = await api('POST', '/api/onboarding/complete', token, undefined);
  if (done.status !== 204) throw new Error(`complete ${phone} ${done.status} ${JSON.stringify(done.json)}`);
  return token;
}

const browser = await chromium.launch({ channel: 'chrome', headless: true });

async function newPage(tag) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(`pageerror ${e.message}`));
  page.on('response', (r) => r.status() >= 400 && r.url().includes('/api/') && errors.push(`${r.status()} ${r.request().method()} ${r.url()}`));
  const shot = async (name) => {
    await sleep(400);
    await page.screenshot({ path: join(OUT, `${tag}-${name}.png`) });
  };
  return { ctx, page, errors, shot };
}

async function uiLogin(page, phone) {
  await page.goto(`${BASE}/login?lang=zh`);
  await page.locator('input[type=tel]').fill(phone);
  await page.locator('.lg-send').click();
  await page.getByText('验证码已发送', { exact: false }).or(page.locator('.lg-send:disabled')).first().waitFor({ timeout: 8000 });
  await page.locator('input[autocomplete=one-time-code], input[inputmode=numeric][type=text]').last().fill(CODE);
  await page.locator('input[type=checkbox]').check();
  await page.locator('.lg-submit').click();
  await page.waitForURL(/\/(onboarding|home)/, { timeout: 15000 });
}

async function demoClick(page, label) {
  await page.goto(`${BASE}/demo?lang=zh&demo=1`);
  const chip = page.getByRole('button', { name: label, exact: true });
  await chip.waitFor({ timeout: 20000 });
  // The chip renders disabled until GET /api/matches/current returns. Over the
  // test-DB tunnel that can take a few seconds, so wait until it enables.
  const start = Date.now();
  while (await chip.isDisabled()) {
    if (Date.now() - start > 20000) throw new Error(`demo chip stayed disabled: ${label}`);
    await sleep(400);
  }
  await chip.click();
  // Partner actions write the match and then navigate. The test DB is reached
  // through a tunnel, so the POST can take well over 10s.
  await page.waitForURL(/\/home/, { timeout: 45000 });
}

try {
  console.log('[0] load synthetics, onboard two real users, match now');
  const admin = await login(DEMOS[0]);
  const loaded = await api('POST', '/api/admin/synthetic/load', admin, {});
  check(loaded.status === 200, `synthetic load ${loaded.status} ${JSON.stringify(loaded.json)}`);
  // Drop leftover demo/synthetic matches so a dirty test DB cannot pin a demo
  // account to an older open match. Real users' rows are not touched.
  const reset = await api('POST', '/api/admin/demo/reset', admin, {});
  check(reset.status === 200, `demo reset ${reset.status} ${JSON.stringify(reset.json)}`);
  const stamp = String(Date.now()).slice(-8);
  const manPhone = `139${stamp}`;
  const womanPhone = `136${stamp}`;
  await onboard(manPhone, 'man', ['women'], `real-man-${stamp}@qq.com`, '阿实测');
  await onboard(womanPhone, 'woman', ['men'], `real-woman-${stamp}@qq.com`, '阿真测');

  const run = await api('POST', '/api/admin/match-runs', admin, { publish: true });
  console.log(`  match-now ${run.status} millis=${run.json?.millis} runs=${JSON.stringify(run.json?.runs)}`);
  check(run.status === 200, 'match-now accepted');
  check(typeof run.json?.millis === 'number' && run.json.millis < 10000, `match-now < 10s (${run.json?.millis} ms)`);

  const weeks = await api('GET', '/api/admin/weeks', admin);
  const pairs = [];
  for (const w of weeks.json ?? []) {
    const list = await api('GET', `/api/admin/matches?weekId=${w.id}`, admin);
    for (const p of list.json ?? []) pairs.push(p);
  }
  const mixed = pairs.filter((p) => {
    const a = p.lowCohort;
    const b = p.highCohort;
    return (a === 'synthetic' && b === 'synthetic') || (a === 'real' && b !== 'real') || (b === 'real' && a !== 'real');
  });
  check(mixed.length === 0, `no real/synthetic or synthetic/synthetic pairs (${pairs.length} pairs, ${mixed.length} mixed)`);

  const demoCards = {};
  for (const phone of DEMOS) {
    const token = phone === DEMOS[0] ? admin : await login(phone);
    const card = await api('GET', '/api/matches/current', token);
    const stage = card.json?.match?.stage;
    const id = card.json?.match?.id;
    demoCards[phone] = { token, stage, id, partner: card.json?.partner?.id ?? null };
    console.log(`  demo ${phone} stage=${stage} match=${id} partner=${demoCards[phone].partner}`);
    check(stage && stage !== 'waiting' && stage !== 'emptyWeek' && id !== '0', `${phone} has a match (${stage})`);
  }

  const man = await login(manPhone);
  const manCard = await api('GET', '/api/matches/current', man);
  const partnerId = manCard.json?.partner?.id ?? null;
  let partnerCohort = null;
  if (partnerId) {
    const hit = pairs.find((p) => p.lowHandle === partnerId || p.highHandle === partnerId || `u${p.lowId}` === partnerId || `u${p.highId}` === partnerId);
    partnerCohort = !hit ? 'missing' : (hit.lowHandle === partnerId || `u${hit.lowId}` === partnerId ? hit.lowCohort : hit.highCohort);
  }
  console.log(`  real user partner=${partnerId ?? 'none'} cohort=${partnerCohort ?? 'n/a'}`);
  check(partnerId == null || partnerCohort === 'real', 'real user does not see a synthetic partner');

  console.log('[1] real user home shows only a real partner, if any');
  const real = await newPage('real');
  await uiLogin(real.page, manPhone);
  check(real.page.url().includes('/home'), 'real user lands on /home');
  if (partnerId && manCard.json?.partner?.name) {
    const name = real.page.getByText(manCard.json.partner.name, { exact: false }).first();
    await name.waitFor({ timeout: 20000 });
    check(true, 'home shows the real partner name');
  } else {
    await real.page.getByText(/这周|等待|先不凑合/).first().waitFor({ timeout: 20000 });
    check(true, 'real user with no match sees an empty week');
  }
  await real.shot('home');
  check(real.errors.filter((e) => !e.includes('/api/')).length === 0, `no page errors (${real.errors.slice(0, 4).join('; ')})`);
  await real.ctx.close();

  console.log('[2] demo 01: slots → partner confirms → check-in → feedback → WeChat');
  const A = await newPage('happy');
  await uiLogin(A.page, DEMOS[0]);
  check(A.page.url().includes('/home'), 'demo login lands on /home');
  await A.page.goto(`${BASE}/edit-profile?lang=zh`);
  const birthdayTag = await A.page.locator('#application-summary-birthday').evaluate((el) => el.tagName);
  check(birthdayTag === 'DIV', 'birthday row is not editable');
  await A.page.goto(`${BASE}/home?lang=zh`);
  await A.page.getByRole('button', { name: /看看/ }).waitFor({ timeout: 20000 });
  await A.page.getByRole('button', { name: /看看/ }).click();
  await A.page.waitForURL(/\/reveal\//, { timeout: 20000 });
  await A.page.getByRole('button', { name: /周[一二三四五六日]/ }).first().click();
  await A.page.getByRole('button', { name: '我想见' }).click();
  await A.page.waitForURL(/\/home/, { timeout: 20000 });
  await A.page.getByText('选时间').first().waitFor({ timeout: 20000 });
  check(true, 'waiting for the partner to pick a time');
  await demoClick(A.page, '对方选好时间');
  await A.page.getByRole('button', { name: '查看约会详情' }).waitFor({ timeout: 20000 });
  check(true, 'both confirmed');
  await demoClick(A.page, '进入约会当天');
  await A.page.getByRole('button', { name: '查看约会详情' }).click();
  await A.page.waitForURL(/\/match\/.+\/date/, { timeout: 10000 });
  await A.page.getByRole('button', { name: /我到了/ }).click();
  await A.page.getByText(/已签到/).waitFor({ timeout: 10000 });
  check(true, 'checked in');
  await A.shot('checked-in');
  await demoClick(A.page, '约会结束 → 反馈');
  await A.page.getByRole('button', { name: '填写反馈' }).click();
  await A.page.waitForURL(/\/feedback/, { timeout: 10000 });
  await A.page.getByRole('button', { name: '见到了' }).click();
  await A.page.getByRole('radio', { name: '5' }).click();
  await A.page.getByRole('button', { name: /想！/ }).click();
  await A.page.getByRole('button', { name: '是', exact: true }).click();
  await A.page.getByRole('button', { name: '提交反馈' }).click();
  await A.page.waitForURL(/\/home/, { timeout: 10000 });
  await demoClick(A.page, '对方提交反馈');
  await A.page.locator('.font-mono').waitFor({ timeout: 20000 });
  const wechat = (await A.page.locator('.font-mono').innerText()).trim();
  check(wechat.length > 1, `WeChat shown (${wechat})`);
  await A.shot('wechat');
  check(A.errors.length === 0, `happy path has no API errors (${A.errors.slice(0, 6).join('; ')})`);
  await A.ctx.close();

  const happyId = demoCards[DEMOS[0]].id;
  const others = DEMOS.slice(1);
  const fresh = [];
  for (const phone of others) {
    if (demoCards[phone].id === happyId) {
      const jumped = await api('POST', '/api/demo/match/stage', demoCards[phone].token, { stage: 'revealed' });
      check(jumped.status === 204, `${phone} gets a fresh reveal after sharing the happy-path match`);
      const card = await api('GET', '/api/matches/current', demoCards[phone].token);
      demoCards[phone].id = card.json?.match?.id;
      demoCards[phone].stage = card.json?.match?.stage;
    }
    fresh.push(phone);
  }
  const [declinePhone, disjointPhone, reportPhone] = fresh;

  console.log(`[3] ${declinePhone}: decline`);
  const B = await newPage('decline');
  await uiLogin(B.page, declinePhone);
  await B.page.getByRole('button', { name: /看看/ }).waitFor({ timeout: 20000 });
  await B.page.getByRole('button', { name: /看看/ }).click();
  await B.page.waitForURL(/\/reveal\//, { timeout: 10000 });
  await B.page.getByRole('button', { name: '这次算了' }).click();
  await B.page.getByRole('button', { name: '这周没时间' }).click();
  await B.page.getByRole('button', { name: '跳过这次匹配' }).click();
  await B.page.waitForURL(/\/home/, { timeout: 20000 });
  await B.page.getByText('收到').first().waitFor({ timeout: 20000 });
  check(true, 'declined card');
  await B.shot('home');
  check(B.errors.length === 0, `decline has no API errors (${B.errors.slice(0, 4).join('; ')})`);
  await B.ctx.close();

  console.log(`[4] ${disjointPhone}: no overlapping slots`);
  const C = await newPage('disjoint');
  await uiLogin(C.page, disjointPhone);
  await C.page.getByRole('button', { name: /看看/ }).waitFor({ timeout: 20000 });
  await C.page.getByRole('button', { name: /看看/ }).click();
  await C.page.waitForURL(/\/reveal\//, { timeout: 10000 });
  await C.page.getByRole('button', { name: /周[一二三四五六日]/ }).first().click();
  await C.page.getByRole('button', { name: '我想见' }).click();
  await C.page.waitForURL(/\/home/, { timeout: 10000 });
  const disjoint = await api('POST', '/api/demo/match/partner-action', demoCards[disjointPhone].token, { action: 'disjoint' });
  check(disjoint.status === 204, `partner picks a different slot (${disjoint.status} ${JSON.stringify(disjoint.json)})`);
  await C.page.reload();
  await C.page.getByText('认真赴约').waitFor({ timeout: 10000 });
  check(true, 'no overlap closes as a friendly end');
  await C.shot('home');
  await C.ctx.close();

  console.log(`[5] ${reportPhone}: report`);
  const stage = await api('POST', '/api/demo/match/stage', demoCards[reportPhone].token, { stage: 'dateDay' });
  check(stage.status === 204, `jump to date day (${stage.status} ${JSON.stringify(stage.json)})`);
  const D = await newPage('report');
  await uiLogin(D.page, reportPhone);
  await D.page.getByRole('button', { name: '查看约会详情' }).click();
  await D.page.waitForURL(/\/match\/.+\/date/, { timeout: 10000 });
  await D.page.getByRole('button', { name: '举报 / 求助' }).click();
  await D.page.getByRole('button', { name: '骚扰或威胁' }).click();
  await D.page.getByRole('button', { name: '提交举报' }).click();
  await D.page.waitForURL(/\/home/, { timeout: 20000 });
  await D.page.getByText('告诉我们').first().waitFor({ timeout: 20000 });
  check(true, 'reported card');
  await D.shot('home');
  check(D.errors.length === 0, `report has no API errors (${D.errors.slice(0, 4).join('; ')})`);
  await D.ctx.close();
} catch (e) {
  console.error(e);
  process.exitCode = 1;
} finally {
  await browser.close();
  if (process.exitCode) process.exit(process.exitCode);
}
