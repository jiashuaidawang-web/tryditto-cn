import { expect, test } from '@playwright/test';

const admin = process.env.MATCH_LAB_ADMIN || '13800000001';
const code = process.env.MATCH_LAB_CODE || '888888';

test('sandbox history stays fixed when the current profile changes', async ({ page }) => {
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
  const summary = page.getByText(/^输入摘要 /).first();
  await expect(summary).toBeVisible();
  const hash = (await summary.innerText()).replace('输入摘要 ', '').trim();
  await page.getByRole('link', { name: '进入反馈' }).first().click();
  await expect(page.locator('table').getByRole('cell', { name: 'MATCH_PUBLISHED', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '接受', exact: true }).nth(0).click();
  await page.getByRole('button', { name: '把接受事件当作偏好来源' }).click();
  await expect(page.getByText('INSUFFICIENT_SEMANTICS')).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/08b-source-rejected.png', fullPage: true });

  await page.goto('/match-lab/profiles');
  await page.getByRole('button', { name: '保存修改' }).click();
  await page.goto('/match-lab/global');
  await expect(page.getByRole('cell', { name: hash, exact: true })).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/08c-history-fixed.png', fullPage: true });
});
