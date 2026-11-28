import { expect, test, type Page, type Response } from '@playwright/test';

const code = process.env.MATCH_LAB_CODE || '888888';
const api = process.env.MATCH_LAB_API || 'http://127.0.0.1:8080';

test('real onboarding writes an isolated profile and deletion closes it', async ({ page }) => {
  const phone = '139' + Math.floor(Math.random() * 1e8).toString().padStart(8, '0');
  const email = `student${Date.now()}@pku.edu.cn`;
  await login(page, phone);
  await page.goto('/onboarding?welcome=0&lang=zh');
  await walk(page, email);
  await expect(page).toHaveURL(/\/home/, { timeout: 30_000 });
  await page.screenshot({ path: 'e2e-artifacts/i01-onboarding-home.png', fullPage: true });

  const token = await tokenOf(page);
  const before = await diagnostic(token);
  expect(before.usableForV4).toBe(false);
  expect(before.reason).toBe('PROFILE_PROCESSING_NOT_GRANTED');

  const granted = await diagnostic(token, true);
  expect(granted.usableForV4).toBe(true);
  expect(granted.reason).toBe('CURRENT');
  const firstVersion = granted.profileVersion as number;
  const kept = new Set(granted.featureCodes as string[]);
  expect(kept.size).toBeGreaterThan(0);

  await page.goto('/onboarding/edit/matchingStyle?lang=zh');
  await page.locator('[data-step="matchingStyle"] button').first().click();
  await page.getByRole('button', { name: '完成' }).click();
  await expect.poll(async () => (await diagnostic(token)).profileVersion, { timeout: 20_000 }).toBeGreaterThan(firstVersion);
  const edited = await diagnostic(token);
  expect(edited.usableForV4).toBe(true);
  const still = (edited.featureCodes as string[]).filter((item) => kept.has(item));
  expect(still.length).toBeGreaterThan(0);
  await page.screenshot({ path: 'e2e-artifacts/i01-profile-edited.png', fullPage: true });

  const other = '137' + Math.floor(Math.random() * 1e8).toString().padStart(8, '0');
  await login(page, other);
  const otherToken = await tokenOf(page);
  const ungranted = await diagnostic(otherToken);
  expect(ungranted.usableForV4).toBe(false);
  expect(ungranted.reason).toBe('PROFILE_PROCESSING_NOT_GRANTED');

  const removed = await fetch(api + '/api/account', { method: 'DELETE', headers: { Authorization: 'Bearer ' + token } });
  expect(removed.status).toBe(204);
  const closed = await diagnosticStatus(token);
  expect(closed).toBe(401);
  await page.goto('/login?lang=zh');
  await page.locator('input[type="tel"]').fill(phone);
  await page.locator('input[autocomplete="one-time-code"]').fill(code);
  await page.locator('input[type="checkbox"]').check();
  await page.locator('form button[type="submit"]').click();
  await expect(page.getByText('该账号正在注销，无法登录')).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/i01-account-closed.png', fullPage: true });
});

async function login(page: Page, phone: string) {
  await page.goto('/login?lang=zh');
  await page.locator('input[type="tel"]').fill(phone);
  await page.locator('input[autocomplete="one-time-code"]').fill(code);
  await page.locator('input[type="checkbox"]').check();
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/(onboarding|home)/);
}

async function walk(page: Page, email: string) {
  const png = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64');
  let previous = '';
  let photosSent = false;
  let last = '';
  for (let stepNo = 0; stepNo < 60; stepNo++) {
    if (/\/home/.test(page.url())) return;
    if (/\/voice/.test(page.url())) {
      await leaveOptionalVoice(page);
      return;
    }
    const step = page.locator('[data-step]:visible').last();
    const visible = await step.waitFor({ state: 'visible', timeout: 8_000 }).then(() => true).catch(() => false);
    if (!visible) {
      await page.waitForURL(/\/(voice|home)/, { timeout: 15_000 });
      continue;
    }
    const id = (await step.getAttribute('data-step')) ?? '';
    last = id || last;
    const same = id !== '' && id === previous;
    previous = id;
    if (same) await page.waitForTimeout(500);
    const action = page.locator('[data-action="next"], [data-action="send"]').last();
    const open = async () => (await action.count()) > 0 && (await action.getAttribute('aria-disabled')) === 'false';
    if (id === 'photos' && !photosSent) {
      photosSent = true;
      let finalized = 0;
      const done = new Promise<void>((resolve, reject) => {
        const timer = setTimeout(() => reject(new Error('photo upload did not finish')), 25_000);
        const onResponse = (response: Response) => {
          if (!response.url().includes('/api/photos/finalize') || !response.ok()) return;
          finalized += 1;
          if (finalized >= 2) {
            clearTimeout(timer);
            page.off('response', onResponse);
            resolve();
          }
        };
        page.on('response', onResponse);
      });
      await page.locator('input[type="file"]').setInputFiles([
        { name: 'a.png', mimeType: 'image/png', buffer: png },
        { name: 'b.png', mimeType: 'image/png', buffer: png },
      ]);
      await done;
    } else if (!same || !(await open())) {
      await answerStep(step, id, email);
    }
    if (await open()) await action.click();
    await page.waitForTimeout(300);
  }
  throw new Error(`onboarding did not finish at ${last}`);
}

async function answerStep(step: ReturnType<Page['locator']>, id: string, email: string) {
  if (id === 'name') {
    await step.locator('input').fill('测试');
    return;
  }
  if (id === 'birthday') {
    const current = await step.locator('input').inputValue();
    if (!current.includes('2004')) await step.locator('input').pressSequentially('20040501');
    return;
  }
  if (id === 'schoolEmail' || id === 'personalEmail') {
    await step.locator('input').fill(email);
    return;
  }
  if (id === 'emailVerify') {
    await step.locator('input').fill(code);
    return;
  }
  if (id === 'location') {
    const locate = step.locator('button[aria-label="使用当前位置"]');
    await locate.evaluate((el) => el.scrollIntoView({ block: 'center' }));
    await locate.click();
    return;
  }
  if (id === 'isStudent') {
    await clickChoice(step, 'radio', '是的，我是学生');
    return;
  }
  if (id === 'gender') {
    await clickChoice(step, 'radio', '男生');
    return;
  }
  if (id === 'expectedGender') {
    await clickChoice(step, 'checkbox', '女生');
    return;
  }
  const checked = step.locator('[aria-checked="true"]');
  const choice = step.locator('[role="radio"], [role="checkbox"]').first();
  if ((await choice.count()) && (await checked.count()) === 0) {
    await choice.click();
    return;
  }
  const area = step.locator('textarea');
  if ((await area.count()) && (await area.inputValue()) === '') {
    await area.fill('喜欢在校园里散步');
    return;
  }
  const text = step.locator('input:not([type="file"])');
  if ((await text.count()) && (await text.first().inputValue()) === '') {
    await text.first().fill('红烧肉');
    return;
  }
  const slider = step.locator('[role="slider"]');
  if (await slider.count()) await slider.first().press('ArrowRight');
}

async function clickChoice(step: ReturnType<Page['locator']>, role: 'radio' | 'checkbox', name: string) {
  const choice = step.getByRole(role, { name });
  if ((await choice.getAttribute('aria-checked')) !== 'true') await choice.click();
}

async function leaveOptionalVoice(page: Page) {
  const stop = page.getByRole('button', { name: '先不答了' });
  await stop.waitFor({ state: 'visible', timeout: 20_000 }).catch(() => undefined);
  if (await stop.isVisible().catch(() => false)) await stop.click();
  if (/\/home/.test(page.url())) return;
  const home = page.getByRole('button', { name: '回首页' });
  if (await home.isVisible().catch(() => false)) await home.click();
  await expect(page).toHaveURL(/\/home/, { timeout: 20_000 });
}

async function tokenOf(page: Page) {
  return page.evaluate(() => {
    const raw = localStorage.getItem('jym-auth-v1');
    return raw ? (JSON.parse(raw).state.token as string) : '';
  });
}

async function diagnostic(token: string, grant = false) {
  const response = await fetch(api + '/api/internal/profile-diagnostic' + (grant ? '/grant' : ''), {
    method: grant ? 'POST' : 'GET',
    headers: { Authorization: 'Bearer ' + token },
  });
  expect(response.status).toBe(200);
  return response.json() as Promise<Record<string, unknown>>;
}

async function diagnosticStatus(token: string) {
  const response = await fetch(api + '/api/internal/profile-diagnostic', {
    headers: { Authorization: 'Bearer ' + token },
  });
  return response.status;
}
