import { expect, test } from '@playwright/test';

const admin = process.env.MATCH_LAB_ADMIN || '13800000001';
const code = process.env.MATCH_LAB_CODE || '888888';
const api = process.env.MATCH_LAB_API || 'http://127.0.0.1:8080';

test('strategy replay and safety stay on sandbox evidence', async ({ page, request }) => {
  await page.goto('/login');
  await page.locator('input[type="tel"]').fill(admin);
  await page.locator('input[autocomplete="one-time-code"]').fill(code);
  await page.locator('input[type="checkbox"]').check();
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/(onboarding|home)/);
  await page.evaluate(async () => {
    const raw = localStorage.getItem('jym-auth-v1');
    const token = raw ? JSON.parse(raw).state.token as string : '';
    await fetch('/api/match-lab/workspace/reset', { method: 'POST', headers: { Authorization: 'Bearer ' + token } });
  });

  await page.goto('/match-lab/global');
  await page.getByRole('button', { name: '执行沙箱全局匹配' }).click();
  await expect(page.getByText('输入摘要').first()).toBeVisible();
  await page.locator('select[aria-label="全局目标"]').selectOption('G1');
  await page.getByRole('button', { name: '执行沙箱全局匹配' }).click();
  await expect(page.getByText('输入摘要').first()).toBeVisible();

  await page.goto('/match-lab/strategy');
  await expect(page.getByRole('heading', { name: '策略学习' })).toBeVisible();
  await expect(page.getByText('观察 128')).toBeVisible();
  await expect(page.getByRole('cell', { name: '未知或过期', exact: true })).toBeVisible();
  await expect(page.locator('p.lab-note', { hasText: '没有记成负例' })).toBeVisible();
  await expect(page.getByText('NOT_IDENTIFIED').first()).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/11-strategy.png', fullPage: true });
  await page.getByRole('button', { name: '沙箱观察' }).click();
  await expect(page.locator('p.lab-note', { hasText: '沙箱反馈太少，不训练有效模型。' })).toBeVisible();
  await expect(page.getByText('INSUFFICIENT').first()).toBeVisible();
  await page.getByRole('button', { name: '尝试激活策略' }).click();
  await expect(page.getByText('STRATEGY_NOT_ACTIVATED')).toBeVisible();

  await page.goto('/match-lab/replay');
  await expect(page.getByRole('heading', { name: '实验回放' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'FROZEN_RESULT' }).first()).toBeVisible();
  await expect(page.getByRole('cell', { name: 'GLOBAL_MATCH' }).first()).toBeVisible();
  const before = await page.locator('pre').first().innerText();
  await page.getByRole('button', { name: '用当前引擎重放' }).click();
  await expect(page.locator('p', { hasText: 'CURRENT_ENGINE_REPLAY' }).filter({ hasText: '语义一致 是' })).toBeVisible();
  await page.getByRole('button', { name: '对照两次运行' }).click();
  await expect(page.getByText('不能写成策略增益')).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/12-replay.png', fullPage: true });

  await page.goto('/match-lab/profiles');
  await page.getByRole('button', { name: '保存修改' }).click();
  await page.goto('/match-lab/replay');
  await expect.poll(async () => page.locator('pre').first().innerText()).toBe(before);

  await page.goto('/match-lab/safety');
  await expect(page.getByRole('heading', { name: '安全审计' })).toBeVisible();
  await expect(page.getByRole('cell', { name: '生产启用', exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'NO-GO', exact: true }).first()).toBeVisible();
  await expect(page.getByRole('cell', { name: 'R-RELATIONSHIP-QUALITY', exact: true })).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/13-safety.png', fullPage: true });

  const token = await page.evaluate(() => {
    const raw = localStorage.getItem('jym-auth-v1');
    return raw ? JSON.parse(raw).state.token as string : '';
  });
  const foreign = await request.get(api + '/api/match-lab/safety', {
    headers: { Authorization: 'Bearer ' + token, 'X-Match-Lab-Workspace': 'ws-other-admin' },
  });
  expect(foreign.status()).toBe(403);
  await page.evaluate(async (value) => {
    await fetch('/api/match-lab/workspace/reset', { method: 'POST', headers: { Authorization: 'Bearer ' + value } });
  }, token);
  await page.goto('/match-lab/replay');
  await expect(page.getByText('这个工作区还没有运行。')).toBeVisible();
});
