import { defineConfig, devices } from '@playwright/test';

const port = Number(process.env['PLAYWRIGHT_PORT'] || 4200);
const baseURL = `http://localhost:${port}`;

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  workers: process.env.CI ? 1 : 2,
  reporter: [['html', { open: 'never' }]],
  timeout: 30000,
  use: {
    baseURL,
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
    command: `npm run start -- --port ${port}`,
    url: baseURL,
    reuseExistingServer: !process.env['PLAYWRIGHT_PORT'],
    timeout: 120 * 1000,
  },
});
