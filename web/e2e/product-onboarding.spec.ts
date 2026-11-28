import { expect, test, type Page } from '@playwright/test';

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
  for (let stepNo = 0; stepNo < 40; stepNo++) {
    if (/\/home/.test(page.url())) return;
    const step = page.locator('[data-step]').first();
    await step.waitFor({ state: 'visible', timeout: 20_000 });
    const id = await step.getAttribute('data-step');
    if (!id || id === previous) {
      await page.waitForTimeout(400);
    }
    previous = id ?? previous;
    if (id === 'name') await step.locator('input').fill('测试');
    else if (id === 'birthday') await step.locator('input').pressSequentially('20040501');
    else if (id === 'schoolEmail' || id === 'personalEmail') await step.locator('input').fill(email);
    else if (id === 'emailVerify') await step.locator('input').fill(code);
    else if (id === 'photos') {
      await page.locator('input[type="file"]').setInputFiles([
        { name: 'a.png', mimeType: 'image/png', buffer: png },
        { name: 'b.png', mimeType: 'image/png', buffer: png },
      ]);
    } else if (id === 'location') {
      await page.getByRole('button', { name: '使用当前位置' }).click();
    } else if (id === 'isStudent') {
      await step.getByRole('button', { name: '是的，我是学生' }).click();
    } else if (id === 'gender') {
      await step.getByRole('button', { name: '男生' }).click();
    } else if (id === 'expectedGender') {
      await step.getByRole('button', { name: '女生' }).click();
    } else {
      const choice = step.getByRole('button').first();
      if (await choice.count()) await choice.click();
    }
    const action = page.locator('[data-action="next"], [data-action="send"]').last();
    if (await action.count() && await action.getAttribute('aria-disabled') === 'false') {
      await action.click();
    }
    await page.waitForTimeout(250);
  }
  throw new Error('onboarding did not finish');
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
