// Headless walk of the onboarding flow, mirroring Ditto pass3 recordings S1 / N1.
// Seeds the same drafts Ditto showed on arrival, screenshots each page ~2.6s after it enters
// (same as Ditto's NN-a.png), then performs the recorded action and presses Next/Send.
//
// usage: node scripts/walk.mjs [S1|N1|all]   (BASE=http://localhost:4173 by default)
import { createRequire } from 'node:module';
import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

process.env.PLAYWRIGHT_BROWSERS_PATH ??= '/Users/null/Library/Caches/ms-playwright';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.env.BASE ?? 'http://localhost:4173';
const LANG = process.env.LANG_PARAM ?? 'en';

const common = {
  name: 'Alex',
  gender: 'man',
  expectedGender: ['women'],
  birthday: '2004-01-15',
  hobbies: ['cooking', 'music', 'travel'],
  cookingStyle: ['italian'],
  cookingSignatureDish: 'test',
  musicSound: ['pop'],
  musicNowPlaying: 'test',
  travelStyle: ['backpacking'],
  travelNext: 'test',
  ethnicity: ['eastAsian'],
  expectedEthnicity: ['noPreference'],
  expectedAgeRange: [18, 32],
  height: { cm: 183, unit: 'cm' },
  heightRange: { any: true, min: 168, max: 188 },
};

const SCENARIOS = {
  S1: {
    seed: { ...common, isStudent: 'no', schoolEmail: 'alex.chen@gmail.com' },
    actions: { isStudent: async (p) => p.getByRole('radio').first().click() },
    stopAt: 'schoolEmail',
  },
  N1: {
    seed: {
      ...common,
      isStudent: 'yes',
      occupation: 'test role',
      firstDateBudget: 'under20',
      educationLevel: 'college',
      educationImportance: 'none',
      personalEmail: 'alex.chen@gmail.com',
    },
    actions: { isStudent: async (p) => p.getByRole('radio').nth(1).click() },
    stopAt: 'personalEmail',
  },
};

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function currentStep(page) {
  return page.evaluate(() => {
    const els = [...document.querySelectorAll('[data-step]')];
    return els.length ? els[els.length - 1].getAttribute('data-step') : null;
  });
}

async function walk(id) {
  const sc = SCENARIOS[id];
  const out = join(ROOT, 'compare', 'ours', LANG === 'en' ? id : `${id}-${LANG}`);
  mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.error(`[${id}] pageerror`, e.message));

  await page.goto(`${BASE}/`);
  await page.evaluate((seed) => {
    localStorage.setItem(
      'jym-onboarding-v1',
      JSON.stringify({ state: { answers: seed, currentId: 'isStudent', furthestId: null, completed: false }, version: 0 }),
    );
  }, sc.seed);
  await page.goto(`${BASE}/onboarding?lang=${LANG}&welcome=0`);

  const log = [];
  let n = 0;
  let prev = null;
  for (let guard = 0; guard < 60; guard++) {
    // wait for a new page to be the live one
    let step = await currentStep(page);
    const t0 = Date.now();
    while ((step === prev || step === null) && Date.now() - t0 < 5000) {
      await sleep(40);
      step = await currentStep(page);
    }
    if (step === prev) throw new Error(`[${id}] stuck after ${prev}`);
    n += 1;
    prev = step;
    await sleep(2600);
    const file = join(out, `${String(n).padStart(2, '0')}.png`);
    await page.screenshot({ path: file });
    log.push(`${String(n).padStart(2, '0')} ${step}`);
    if (step === sc.stopAt) break;
    if (sc.actions[step]) {
      await sc.actions[step](page);
      await sleep(900);
    }
    const btn = page.locator('[aria-label="Next"][aria-disabled="false"], [aria-label="Send"][aria-disabled="false"]').last();
    await btn.click({ timeout: 3000 });
  }
  console.log(`${id}:\n  ${log.join('\n  ')}`);
  await browser.close();
}

const which = process.argv[2] ?? 'all';
for (const id of which === 'all' ? Object.keys(SCENARIOS) : [which]) await walk(id);
