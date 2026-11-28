import { expect, test } from '@playwright/test';

const admin = process.env.MATCH_LAB_ADMIN || '13800000001';
const code = process.env.MATCH_LAB_CODE || '888888';

test('sandbox relationship loop', async ({ page }) => {
  await page.goto('/login');
  await page.locator('input[type="tel"]').fill(admin);
  await page.locator('input[autocomplete="one-time-code"]').fill(code);
  await page.locator('input[type="checkbox"]').check();
  await page.locator('form button[type="submit"]').click();
  await page.waitForURL(/\/(onboarding|home)/);

  await page.goto('/match-lab/global');
  await page.getByRole('button', { name: '执行沙箱全局匹配' }).click();
  await expect(page.getByText('ENGINE_EXECUTED').first()).toBeVisible();
  await expect(page.getByText(/可继续 可以/)).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/08-global.png', fullPage: true });
  await page.getByRole('link', { name: '进入反馈' }).first().click();

  const timeline = page.locator('table');
  await expect(page.getByRole('heading', { name: '反馈事件' })).toBeVisible();
  await expect(timeline.getByRole('cell', { name: 'MATCH_PUBLISHED', exact: true })).toBeVisible();
  await page.getByRole('button', { name: '接受', exact: true }).nth(0).click();
  await expect(timeline.getByRole('cell', { name: 'MATCH_ACCEPTED', exact: true })).toHaveCount(1);
  await page.getByRole('button', { name: '接受', exact: true }).nth(1).click();
  await expect(timeline.getByRole('cell', { name: 'MUTUAL_ACCEPTANCE_REACHED', exact: true })).toBeVisible();
  await expect(page.getByText('互相接受 是')).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/09-feedback.png', fullPage: true });
  await page.getByRole('link', { name: '进入画像学习' }).click();

  await expect(page.getByRole('heading', { name: '画像学习' })).toBeVisible();
  await page.getByRole('button', { name: '生成相处节奏提案' }).click();
  const proposal = page.locator('p').filter({ hasText: 'partner.pace' });
  await expect(proposal).toContainText('PENDING_CONFIRMATION');
  await page.getByRole('button', { name: '模拟用户确认' }).click();
  await expect(proposal).toContainText('CONFIRMED');
  await expect(page.locator('td', { hasText: 'self.pace' }).first()).toBeVisible();
  await page.getByRole('button', { name: '用新画像重新匹配' }).click();
  await expect(page.getByRole('heading', { name: '重新匹配' })).toBeVisible();
  await expect(page.locator('p', { hasText: '父运行' })).toBeVisible();
  await page.screenshot({ path: 'e2e-artifacts/10-learning.png', fullPage: true });
});
