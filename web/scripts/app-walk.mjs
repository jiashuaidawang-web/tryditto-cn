// Headless walk of the logged-in app: end of onboarding (school email → … → /home), every logged-in page,
// then the full match loop through the demo panel. Screenshots go to compare/app/ours/<lang>/.
//
// usage: LANG_PARAM=en|zh node scripts/app-walk.mjs   (BASE=http://127.0.0.1:5175 by default)
import { createRequire } from 'node:module';
import { mkdirSync, rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.env.BASE ?? 'http://127.0.0.1:5175';
const LANG = process.env.LANG_PARAM ?? 'en';
const ESCAPE = process.env.ESCAPE === '1';
const OUT = join(ROOT, 'compare', 'app', 'ours', ESCAPE ? `${LANG}-escape` : LANG);
const L = (en, zh) => (LANG === 'en' ? en : zh);
const q = (extra = '') => `?lang=${LANG}${extra}`;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const seed = {
  name: L('Alex', '林晓'),
  gender: 'man',
  expectedGender: ['women'],
  birthday: '2004-01-15',
  hobbies: ['cooking', 'music', 'travel'],
  cookingStyle: ['italian'],
  cookingSignatureDish: L('Mushroom risotto', '番茄炒蛋'),
  musicSound: ['pop'],
  musicNowPlaying: L('Phoebe Bridgers', '陈绮贞'),
  travelStyle: ['backpacking'],
  travelNext: L('Kyoto', '大理'),
  ethnicity: ['eastAsian'],
  expectedEthnicity: ['noPreference'],
  expectedAgeRange: [18, 26],
  height: { cm: 183, unit: 'cm' },
  heightRange: { any: true, min: 168, max: 188 },
  isStudent: 'yes',
  schoolEmail: L('alex.chen@stanford.edu', 'linxiao@pku.edu.cn'),
};

if (process.env.SKIP_ONB !== '1') rmSync(OUT, { recursive: true, force: true });
mkdirSync(OUT, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('response', (r) => r.status() >= 400 && errors.push(`${r.status()} ${r.url()}`));

const shot = async (name, wait = 900) => {
  await sleep(wait);
  await page.screenshot({ path: join(OUT, `${name}.png`) });
  console.log('  ✓', name);
};
/** Scroll the tallest scroll container by `dy` (pages scroll inside `.ob-scroll`, not the window). */
const scrollBy = (dy) =>
  page.evaluate((dy) => {
    const els = [...document.querySelectorAll('*')].filter((e) => e.scrollHeight > e.clientHeight + 4 && /(auto|scroll)/.test(getComputedStyle(e).overflowY));
    els.sort((a, b) => b.clientHeight - a.clientHeight);
    if (els[0]) els[0].scrollTop += dy;
  }, dy);
const btn = (name) => page.getByRole('button', { name, exact: false }).first();
const closeSheet = async () => {
  await page.keyboard.press('Escape');
  await sleep(500);
};

/** Two distinct, decodable PNG "photos" drawn on a canvas (abstract shapes, not people). */
const makePhotos = () =>
  page.evaluate(() =>
    ['#ff8fb8', '#7fb5ff'].map((c, i) => {
      const cv = document.createElement('canvas');
      cv.width = 600;
      cv.height = 800;
      const g = cv.getContext('2d');
      const grad = g.createLinearGradient(0, 0, 600, 800);
      grad.addColorStop(0, c);
      grad.addColorStop(1, '#203040');
      g.fillStyle = grad;
      g.fillRect(0, 0, 600, 800);
      g.fillStyle = 'rgba(255,255,255,.7)';
      g.beginPath();
      g.arc(300, 330 + i * 40, 150, 0, Math.PI * 2);
      g.fill();
      return cv.toDataURL('image/png').split(',')[1];
    }),
  );

async function currentStep() {
  return page.evaluate(() => {
    const els = [...document.querySelectorAll('[data-step]')];
    return els.length ? els[els.length - 1].getAttribute('data-step') : null;
  });
}

// ─── 1. End of onboarding ────────────────────────────────────────────────────────────────────────────────
if (process.env.SKIP_ONB === '1') {
  await page.goto(`${BASE}/home${q('&demo=1')}`);
  await sleep(1500);
} else {
console.log(`[${LANG}${ESCAPE ? ' escape' : ''}] onboarding tail`);
await page.goto(`${BASE}/${q()}`);
await page.evaluate((seed) => {
  localStorage.clear();
  localStorage.setItem('jym-onboarding-v1', JSON.stringify({ state: { answers: seed, currentId: 'schoolEmail', furthestId: 'schoolEmail', completed: false }, version: 0 }));
}, seed);
await page.goto(`${BASE}/onboarding${q('&welcome=0')}`);

const order = [];
let prev = null;
for (let guard = 0; guard < 40; guard++) {
  let step = await currentStep();
  const t0 = Date.now();
  while ((step === prev || step === null) && Date.now() - t0 < 6000) {
    if (page.url().includes('/home')) break;
    await sleep(50);
    step = await currentStep();
  }
  if (page.url().includes('/home')) break;
  if (step === prev) throw new Error(`stuck after ${prev}`);
  prev = step;
  order.push(step);
  await shot(`onb-${String(order.length).padStart(2, '0')}-${step}`, 2200);

  if (step === 'schoolEmail' && ESCAPE) {
    const esc = page.getByText(L("I don't have a school email", '我没有学校邮箱'), { exact: false }).first();
    if (await esc.count()) await esc.click();
    else await page.locator('button:has-text("school"), button:has-text("学校")').last().click();
    continue;
  }
  if (step === 'emailVerify') await page.locator('input').last().fill('123456');
  if (step === 'personalEmail') await page.locator('input').last().fill('alex.chen@gmail.com');
  if (step === 'photos') {
    const data = await makePhotos();
    await page.locator('input[type=file]').first().setInputFiles(data.map((b, i) => ({ name: `p${i}.png`, mimeType: 'image/png', buffer: Buffer.from(b, 'base64') })));
    await shot(`onb-${String(order.length).padStart(2, '0')}-photos-filled`, 1200);
  }
  if (step === 'location') {
    await page.getByRole('button', { name: 'Use my current location' }).click();
    await shot(`onb-${String(order.length).padStart(2, '0')}-location-filled`, 1200);
  }
  if (step === 'datingHelp') {
    await page.getByRole('checkbox').first().click();
    if (!ESCAPE) {
      await shot('onb-zz-datingHelp-done', 700);
      await shot('onb-zz-heart', 1600);
      await page.waitForURL(/\/home/, { timeout: 20000 });
      break;
    }
  }
  if (step === 'emailVerify' && order.includes('personalEmail')) {
    await sleep(500);
    await page.locator('[data-action="send"][aria-disabled="false"]').last().click();
    await shot('onb-zz-final-done', 700);
    await shot('onb-zz-heart', 1600);
    await page.waitForURL(/\/home/, { timeout: 20000 });
    break;
  }
  await sleep(500);
  const next = page.locator('[data-action="next"][aria-disabled="false"], [data-action="send"][aria-disabled="false"]').last();
  if (!(await next.count())) {
    const radio = page.getByRole('radio');
    const box = page.getByRole('checkbox');
    if (await radio.count()) await radio.first().click();
    else if (await box.count()) await box.first().click();
    else if (await page.locator('textarea').count()) await page.locator('textarea').last().fill(L('Kindness and curiosity', '真诚，好奇心'));
    else if (await page.locator('input:not([type=file]):not([type=range])').count())
      await page.locator('input:not([type=file]):not([type=range])').last().fill(L('test', '测试'));
    await sleep(500);
  }
  if (await next.count()) await next.click({ timeout: 3000 });
}
console.log('  order:', order.join(' → '));
await page.waitForURL(/\/home/, { timeout: 20000 });
await shot('20-home-arrival', 2500);

if (ESCAPE) {
  await browser.close();
  if (errors.length) console.log('ERRORS', errors);
  process.exit(0);
}
}

// ─── 2. Logged-in pages ─────────────────────────────────────────────────────────────────────────────────
console.log('pages');
const visit = async (path, name, wait = 1600) => {
  await page.goto(`${BASE}${path}${q()}`).catch(() => page.goto(`${BASE}${path}${q()}`));
  await shot(name, wait);
};
await visit('/home', '01-home', 2500);
await page.locator('[data-action="menu"]').click();
await shot('12-menu');
await page.locator('[data-action="menu"]').click();
await btn(L('Invite', '邀请')).click();
await shot('11-invite');
await closeSheet();
await btn(L('Message Ditto', '给见一面发消息')).click();
await shot('01b-alerts-sheet');
await closeSheet();

await visit('/where', '02-where');
await visit('/you', '03-you');
await visit('/edit-profile', '04-edit-profile');
await scrollBy(700);
await shot('04b-edit-profile-scrolled', 500);
await scrollBy(900);
await shot('04c-edit-profile-scrolled', 500);
await scrollBy(-5000);
await page.locator(`[aria-label="${L('Edit photos', '编辑照片')}"]`).first().click().catch(() => {});
await shot('07-edit-photos');
await visit('/onboarding/edit/intention', '05-edit-looking-for');
await visit('/onboarding/edit/name', '06-edit-name');
await visit('/onboarding/edit/hobbyRanking', '06b-edit-hobbies');
await visit('/deep-info', '09-deep-info');
await visit('/blocklist', '10-blocklist');
await btn(L('Add block', '添加屏蔽')).click();
await shot('10b-blocklist-add');
const sheet = page.getByRole('dialog');
await sheet.locator('input').first().fill(L('Jamie from class', '高中同桌'));
await sheet.locator('input').nth(1).fill('jamie@example.com');
await shot('10c-blocklist-add-filled', 500);
await sheet.getByRole('button', { name: L('Block', '屏蔽'), exact: true }).click();
await shot('10d-blocklist-row', 1200);
await visit('/account', '13-account');

// ─── 3. Match loop through the demo panel ───────────────────────────────────────────────────────────────
console.log('match loop');
const demoChip = async (en, zh) => {
  await page.getByRole('button', { name: 'Open demo controls' }).click();
  await sleep(500);
  await page.getByRole('dialog').getByRole('button', { name: L(en, zh), exact: false }).first().click();
  await sleep(900);
};
await page.goto(`${BASE}/home${q('&demo=1')}`);
await shot('30-home-demo-fab', 2200);
await page.getByRole('button', { name: 'Open demo controls' }).click();
await shot('31-demo-panel', 700);
await closeSheet();

await demoChip('Match revealed', '匹配揭晓');
await shot('32-home-revealed', 1600);
await page.getByRole('button', { name: L('Meet', '看看'), exact: false }).first().click();
await shot('33-reveal', 2200);
await scrollBy(640);
await shot('33b-reveal-why', 600);
await scrollBy(640);
await shot('33c-reveal-plan', 600);
await scrollBy(3000);
const slots = page.locator('button[aria-pressed]');
const ns = await slots.count();
for (let i = 0; i < Math.min(2, ns); i++) await slots.nth(i).click();
await shot('33d-reveal-slots-picked', 600);
await btn(L("I'm in", '我想见')).click();
await shot('34-home-awaiting-partner', 1800);
await demoChip('They pick a time', '对方选好时间');
await shot('35-home-confirmed', 1800);
await demoChip('Open date day', '进入约会当天');
await shot('36-home-date-day', 1800);
await btn(L('Date details', '查看约会详情')).click();
await shot('37-date', 1800);
await btn(L("I'm here", '我到了')).click();
await shot('37b-date-checked-in', 1000);
await scrollBy(700);
await shot('37c-date-scrolled', 600);
await scrollBy(1500);
await shot('37d-date-bottom', 600);
await btn(L('Report a problem', '举报 / 求助')).click();
await shot('37e-report-sheet', 800);
await closeSheet();
await btn(L("We've met", '见完了')).click();
await shot('38-feedback', 1800);
await btn(L('Yes, we met', '见到了')).click();
await page.getByRole('radio', { name: '5', exact: true }).click();
await btn(L('Yes, I’d love to', '想！')).click();
await page.getByRole('button', { name: L('Yes', '是'), exact: true }).last().click();
await page.locator('textarea').last().fill(L('Great conversation about music.', '聊音乐聊得很开心。'));
await shot('38b-feedback-filled', 600);
await scrollBy(2000);
await shot('38c-feedback-bottom', 600);
await btn(L('Send feedback', '提交反馈')).click();
await shot('39-home-awaiting-feedback', 1800);
await demoChip('They submit feedback', '对方提交反馈');
await shot('40-home-mutual', 2600);

await demoChip('Not mutual', '未双向');
await shot('41-home-closed', 1600);
await demoChip('Empty week', '空周');
await shot('42-home-empty-week', 1600);
await demoChip('Reported', '已举报');
await shot('43-home-reported', 1600);

await demoChip('Match revealed', '匹配揭晓');
await page.getByRole('button', { name: L('Meet', '看看'), exact: false }).first().click();
await sleep(1500);
await btn(L('Not this time', '这次算了')).click();
await shot('44-decline-sheet', 800);
await page.getByRole('dialog').locator('button[aria-pressed]').first().click();
await shot('44b-decline-reason', 400);
await btn(L('Pass on this match', '跳过这次匹配')).click();
await shot('45-home-declined', 1800);

await page.goto(`${BASE}/demo${q()}`);
await shot('46-demo-page', 1200);

await browser.close();
if (errors.length) console.log('ERRORS\n ', [...new Set(errors)].join('\n  '));
