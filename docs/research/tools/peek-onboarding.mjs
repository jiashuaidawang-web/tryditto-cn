import { createRequire } from 'module';
const require = createRequire('/Users/null/.npm/_npx/9833c18b2d85bc59/node_modules/');
const { chromium } = require('playwright');

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ storageState: new URL('./.ditto-state.json', import.meta.url).pathname, viewport: { width: 390, height: 844 } });
const page = await context.newPage();
for (const url of ['https://app.tryditto.com/onboarding', 'https://app.tryditto.com/']) {
  await page.goto(url);
  await page.waitForTimeout(5000);
  const text = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 200));
  console.log(url, '->', page.url(), '|', text);
}
await browser.close();
