import { defineConfig, devices } from '@playwright/test';

<<<<<<< HEAD
const port = Number(process.env['PLAYWRIGHT_PORT'] || 4200);
const baseURL = `http://localhost:${port}`;
=======
const e2eWebUrl = process.env.LUXESTAY_E2E_WEB_URL || 'http://localhost:4200';
const e2eWebPort = new URL(e2eWebUrl).port || '4200';
>>>>>>> codex/ui-functional-audit-polish

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  workers: process.env.CI ? 1 : 2,
  reporter: [['html', { open: 'never' }]],
  timeout: 30000,
  use: {
<<<<<<< HEAD
    baseURL,
=======
    baseURL: e2eWebUrl,
>>>>>>> codex/ui-functional-audit-polish
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
<<<<<<< HEAD
    command: `npm run start -- --port ${port}`,
    url: baseURL,
    reuseExistingServer: !process.env['PLAYWRIGHT_PORT'],
=======
    command: `npm run start -- --port ${e2eWebPort}`,
    url: e2eWebUrl,
    reuseExistingServer: true,
>>>>>>> codex/ui-functional-audit-polish
    timeout: 120 * 1000,
  },
});
