import { expect, test } from '@playwright/test';

const admin = process.env.MATCH_LAB_ADMIN || '13800000001';
const code = process.env.MATCH_LAB_CODE || '888888';
const api = process.env.MATCH_LAB_API || 'http://127.0.0.1:8080';

test('match lab sandbox session', async ({ page, request }) => {
  const closed = await request.get(api + '/api/match-lab/overview');
  expect(closed.status()).toBe(401);

  await page.goto('/login');
  await page.locator('input[type="tel"]').fill(admin);
  await page.locator('input[autocomplete="one-time-code"]').fill(code);
  await page.locator('input[type="checkbox"]').check();
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/(onboarding|home)/);
  await page.evaluate(async () => {
    const raw = localStorage.getItem('jym-auth-v1');
    const token = raw ? JSON.parse(raw).state.token as string : '';
    await fetch('/api/match-lab/workspace/reset', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
    });
  });

  await page.goto('/match-lab');
  await expect(page.getByRole('heading', { name: '实验总览' })).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/01-overview.png', fullPage: true });
  await page.getByLabel('数据集版本').selectOption({ index: 1 });
  await expect(page.getByRole('cell', { name: /群体观察|双向评分案例|召回排序|关系推理|全局匹配|反馈事件|画像学习|生产启用/ }).first()).toBeVisible();

  await page.getByRole('link', { name: '用户画像' }).click();
  await expect(page.getByRole('heading', { name: '自我 SelfProfile' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '偏好 PreferenceProfile' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '行为 BehaviorProfile' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '记忆 MemoryProfile' })).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/02-profiles.png', fullPage: true });
  await page.getByLabel('画像名称').fill('戊二·新建');
  await page.getByRole('button', { name: '创建虚构画像' }).click();
  await expect(page.getByRole('button', { name: '戊二·新建' })).toBeVisible();
  await page.getByRole('button', { name: '保存修改' }).click();
  await expect(page.getByText(/v2/).first()).toBeVisible();

  await page.getByRole('link', { name: '双向评分' }).click();
  await page.getByLabel('用户 A').selectOption('71001');
  await page.getByLabel('用户 B').selectOption('71002');
  await page.getByRole('button', { name: '执行双向匹配' }).click();
  await expect(page.getByText('可推荐').first()).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/03-score-mutual.png', fullPage: true });
  await page.getByLabel('用户 A').selectOption('71003');
  await page.getByLabel('用户 B').selectOption('71004');
  await page.getByRole('button', { name: '执行双向匹配' }).click();
  await expect(page.getByText('拒绝推荐').first()).toBeVisible();
  await page.getByLabel('用户 A').selectOption('71001');
  await page.getByLabel('用户 B').selectOption('71005');
  await page.getByRole('button', { name: '执行双向匹配' }).click();
  await expect(page.getByText('拒绝推荐').first()).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/04-score-refused.png', fullPage: true });

  await page.getByRole('link', { name: '候选发现' }).click();
  await page.getByRole('button', { name: '执行冻结对照' }).click();
  await expect(page.getByText('FROZEN_COHORT').first()).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/05-frozen-recall.png', fullPage: true });
  await page.getByRole('button', { name: '执行沙箱候选' }).click();
  await expect(page.getByText('SYNTHETIC_SANDBOX').nth(1)).toBeVisible();
  await expect(page.getByText('NOT_APPLICABLE').first()).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/06-sandbox-recall.png', fullPage: true });

  await page.getByRole('link', { name: 'AI 关系推理' }).click();
  await page.getByRole('button', { name: '回放冻结判断' }).click();
  await expect(page.getByText('rule-echo-fixture').first()).toBeVisible();
  await expect(page.getByText('NOT_RUN').first()).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/07-reasoning.png', fullPage: true });

  const token = await page.evaluate(() => {
    const raw = localStorage.getItem('jym-auth-v1');
    return raw ? JSON.parse(raw).state.token as string : '';
  });
  const userLogin = await request.post(api + '/api/auth/login', {
    data: { phone: '13900000002', code },
  });
  expect(userLogin.ok()).toBeTruthy();
  const userToken = (await userLogin.json()).token as string;
  const denied = await request.get(api + '/api/match-lab/overview', {
    headers: { Authorization: 'Bearer ' + userToken },
  });
  expect(denied.status()).toBe(403);

  const foreign = await request.get(api + '/api/match-lab/overview', {
    headers: { Authorization: 'Bearer ' + token, 'X-Match-Lab-Workspace': 'ws-other-admin' },
  });
  expect(foreign.status()).toBe(403);
});
