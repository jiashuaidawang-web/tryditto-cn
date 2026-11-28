import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 180_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'e2e-report' }]],
  outputDir: 'e2e-artifacts',
  use: {
    baseURL: process.env.MATCH_LAB_WEB || 'http://127.0.0.1:4173',
    screenshot: 'on',
    trace: 'retain-on-failure',
    viewport: { width: 1440, height: 900 },
  },
});
