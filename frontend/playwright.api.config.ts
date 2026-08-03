import { defineConfig, devices } from '@playwright/test';

const backendEnv = {
  ...process.env,
  JWT_SECRET: 'e2e-public-booking-jwt-secret-at-least-thirty-two-characters',
  DB_PASSWORD: 'e2e-not-used',
  MAIL_PASSWORD: 'e2e-not-used',
  PROPERTY_PAYMENT_ENCRYPTION_KEY: 'e2e-public-booking-property-payment-key-32-chars',
  PAYMENT_DEMO_SIGNING_SECRET: 'e2e-public-booking-demo-signing-secret',
  PAYMENT_SANDBOX_ENABLED: 'false',
  PASSWORD_RESET_EMAIL_ENABLED: 'false',
  VNPAY_TMN_CODE: 'E2E',
  VNPAY_HASH_SECRET: 'e2e-not-used',
  SERVER_PORT: '28743'
};
const useExternalBackend = process.env['PLAYWRIGHT_EXTERNAL_BACKEND'] === 'true';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';

export default defineConfig({
  testDir: './e2e',
  testMatch: 'home-search.api.spec.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  timeout: 60_000,
  use: {
    baseURL: `http://localhost:${frontendPort}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    ...(!useExternalBackend ? [{
      command: '.\\mvnw.cmd -q spring-boot:run -Dspring-boot.run.profiles=e2e',
      cwd: '../backend',
      env: backendEnv,
      url: 'http://localhost:28743/api/public/properties/search?pageNumber=1&pageSize=1',
      reuseExistingServer: false,
      timeout: 180_000
    }] : []),
    {
      command: `npm run start -- --port ${frontendPort}`,
      cwd: '.',
      url: `http://localhost:${frontendPort}`,
      reuseExistingServer: false,
      timeout: 180_000
    }
  ]
});
