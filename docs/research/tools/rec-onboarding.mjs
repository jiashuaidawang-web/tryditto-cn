// Headless recording of Ditto onboarding branches. Never submits: stops at school email,
// personal email / verify, phone, selfie, or the datingHelp page.
import { createRequire } from 'module';
import fs from 'fs';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const ROOT = '/Users/null/IdeaProjects/AI/tryditto-cn/docs/research';
const STATE = `${ROOT}/tools/.ditto-state.json`;
const VID = `${ROOT}/assets/videos/pass3/`;
const SHOTS = `${ROOT}/assets/ditto-onboarding/shots/pass3/`;

const RUNS = {
  S1: { student: true, gender: 'Man', expected: ['Women'], hobbies: ['Cooking', 'Music', 'Travel'] },
  S2: { student: true, gender: 'Woman', expected: ['Men'], hobbies: ['Reading', 'Gym', 'Gaming'] },
  S3: { student: true, gender: 'Nonbinary', expected: ['Everyone'], hobbies: ['Movies/TV', 'Art', 'Cooking'] },
  N1: { student: false, gender: 'Man', expected: ['Women'], hobbies: ['Cooking', 'Music', 'Travel'] },
  N2: { student: false, gender: 'Woman', expected: ['Men'], hobbies: ['Reading', 'Gym', 'Gaming'] },
  N3: { student: false, gender: 'Nonbinary', expected: ['Everyone'], hobbies: ['Movies/TV', 'Art', 'Cooking'] },
};
const HOBBIES = ['Cooking', 'Music', 'Travel', 'Reading', 'Gym', 'Gaming', 'Movies/TV', 'Art'];
const STOP = /school email|what's your email|verify\s*that email|6-digit|dating\s*coach|you want help with|just get me a date|what's ur number|verify your number|selfie|face verif|add (some )?photos|upload/i;

const ids = process.argv.slice(2).length ? process.argv.slice(2) : Object.keys(RUNS);
const browser = await chromium.launch({ channel: 'chrome', headless: true });

for (const id of ids) {
  const cfg = RUNS[id];
  const dir = `${SHOTS}${id}/`;
  fs.mkdirSync(dir, { recursive: true });
  const context = await browser.newContext({
    storageState: STATE,
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 2,
    recordVideo: { dir: VID, size: { width: 390, height: 844 } },
  });
  const page = await context.newPage();
  const t0 = Date.now();
  const marks = [];
  const mark = (label, extra = {}) => marks.push({ t: +((Date.now() - t0) / 1000).toFixed(2), label, ...extra });
  const text = () => page.evaluate(() => document.body.innerText.replace(/\n(.)(?=\n)/g, '$1').replace(/\n+/g, ' | ').replace(/[\u00a0\u2009\u200b]/g, ' ').slice(0, 400));
  const nextState = () => page.evaluate(() => {
    const b = [...document.querySelectorAll('button')].find(x => ['Next', 'Send', 'Done'].includes(x.getAttribute('aria-label') || ''));
    return b ? { label: b.getAttribute('aria-label'), enabled: !b.disabled && b.getAttribute('aria-disabled') !== 'true' } : null;
  });
  const clickNext = async () => {
    const s = await nextState();
    if (!s || !s.enabled) return false;
    try { await page.getByRole('button', { name: s.label, exact: true }).first().click({ timeout: 5000 }); } catch { return false; }
    return true;
  };
  const optionButtons = () => page.evaluate(() => [...document.querySelectorAll('button')]
    .filter(b => { const r = b.getBoundingClientRect(); return r.width > 0 && r.height > 0 && r.top < innerHeight && r.bottom > 0; })
    .map((b, i) => ({ i, t: b.innerText.replace(/\s+/g, ' ').trim(), sel: /ob-selected/.test(b.className) || b.getAttribute('aria-pressed') === 'true' })));
  const clickByLabel = async (label) => {
    const loc = page.locator('button', { hasText: new RegExp(`^\\S*\\s*${label.replace(/[/.()?]/g, '\\$&')}$`) });
    if (!(await loc.count())) return false;
    try { await loc.first().click({ timeout: 4000 }); } catch { return false; }
    await page.waitForTimeout(700);
    return true;
  };
  const choiceButtons = () => page.evaluate(() => [...document.querySelectorAll('button.relative.flex.min-h-12')]
    .filter(b => { const r = b.getBoundingClientRect(); return r.width > 0 && r.height > 0; })
    .map(b => ({ t: b.innerText.replace(/\s+/g, ' ').trim(), sel: /ob-selected/.test(b.className) || getComputedStyle(b).backgroundColor === 'rgb(228, 0, 137)', dis: b.getAttribute('aria-disabled') === 'true' })));
  const setMulti = async (target) => {
    for (const o of await choiceButtons()) {
      const lbl = target.find(x => o.t.endsWith(x));
      if (o.sel && !lbl) await clickByLabel(o.t.replace(/^\S+\s+/, '') || o.t);
    }
    for (const lbl of target) {
      const o = (await choiceButtons()).find(x => x.t.endsWith(lbl));
      if (o && !o.sel) await clickByLabel(lbl);
    }
  };

  await page.goto('https://app.tryditto.com/onboarding');
  await page.waitForTimeout(4000);
  mark('loaded', { text: await text() });

  for (let k = 0; k < 70; k++) {
    if (/currently a student/i.test(await text())) break;
    const back = page.getByRole('button', { name: 'Back', exact: true });
    if (!(await back.count())) break;
    await back.first().click();
    await page.waitForTimeout(900);
  }
  await page.waitForTimeout(2500);
  mark('START', { text: await text() });

  let n = 0;
  try {
  for (let step = 0; step < 50; step++) {
    n++;
    const p = String(n).padStart(2, '0');
    await page.waitForTimeout(2600);
    const t = await text();
    mark(`page ${p}`, { text: t });
    await page.screenshot({ path: `${dir}${p}-a.png` });
    if (STOP.test(t)) { mark('STOP', { text: t }); break; }

    let act = '';
    if (/currently a student/i.test(t)) {
      act = await clickByLabel(cfg.student ? "yes, i'm a student" : 'nope, not a student') ? 'isStudent' : 'isStudent?';
    } else if (/what's your gender/i.test(t)) {
      await clickByLabel(cfg.gender); act = `gender=${cfg.gender}`;
    } else if (/looking to date|wanna date/i.test(t)) {
      await setMulti(cfg.expected); act = `expected=${cfg.expected}`;
    } else if (/hobbies/i.test(t) && /Cooking/.test(t)) {
      await setMulti(cfg.hobbies); act = `hobbies=${cfg.hobbies}`;
    } else {
      const s = await nextState();
      if (!s || !s.enabled) {
        const inputs = page.locator('input:visible:not([type=file]):not([placeholder="Other"]), textarea:visible');
        const ic = await inputs.count();
        if (ic) {
          for (let i = 0; i < ic; i++) { const el = inputs.nth(i); if (!(await el.inputValue())) { await el.fill('test'); act += 'fill '; } }
        } else {
          const opts = page.locator('button.relative.flex.min-h-12:visible');
          if (await opts.count()) { await opts.first().click({ timeout: 4000 }).catch(() => {}); act = 'opt1'; }
        }
      } else act = 'prefilled';
    }
    await page.waitForTimeout(1000);
    await page.screenshot({ path: `${dir}${p}-b.png` });
    const t2 = await text();
    if (STOP.test(t2)) { mark('STOP-after-action', { text: t2, act }); break; }
    if (t2.slice(0, 60) !== t.slice(0, 60) && !/hobbies|gender|date/i.test(act)) { mark('auto-advanced', { act }); n--; continue; }
    if (!(await clickNext())) { mark('NO-NEXT', { act, text: t2 }); break; }
    mark(`next ${p}`, { act });
  }
  } catch (e) { mark('ERROR', { msg: String(e.message).split('\n')[0] }); }

  await page.waitForTimeout(1500);
  const v = page.video();
  await context.close();
  const src = await v.path();
  const dst = `${VID}${id}-raw.webm`;
  fs.renameSync(src, dst);
  fs.writeFileSync(`${VID}${id}-log.json`, JSON.stringify({ id, cfg, marks }, null, 1));
  const start = marks.find(m => m.label === 'START');
  console.log(id, 'video', dst, 'start', start && start.t, 'pages', n, 'last', marks[marks.length - 1].label);
}
await browser.close();
