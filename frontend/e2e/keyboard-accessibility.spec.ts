import { expect, Page, Route, test } from '@playwright/test';

test('supports skip navigation and traps focus in the customer support dialog', async ({ page }) => {
  await page.route('**/api/**', route => json(route, []));
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  const skipLink = page.locator('.skip-link');

  await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur());
  await page.keyboard.press('Tab');
  if (!(await skipLink.evaluate(element => element === document.activeElement))) {
    await page.keyboard.press('Tab');
  }
  await expect(skipLink).toBeFocused();
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T342-skip-link-focus.png', fullPage: true });
  await page.keyboard.press('Enter');
  await expect(page.locator('#client-main-content')).toBeFocused();

  const trigger = page.locator('.support-trigger');
  await trigger.focus();
  await page.keyboard.press('Enter');
  const dialog = page.locator('#support-chat-panel');
  await expect(dialog).toBeVisible();
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T342-chat-focus-trap.png', fullPage: true });

  const lastDialogAction = dialog.getByRole('link', { name: 'Dang nhap ngay' });
  await lastDialogAction.focus();
  await page.keyboard.press('Tab');
  await expect(dialog.locator('.close-button')).toBeFocused();

  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(trigger).toBeFocused();
});

test('moves focus to an assertive login error', async ({ page }) => {
  await page.route('**/api/auth/login', route => json(route, {
    code: 'INVALID_CREDENTIALS',
    message: 'Invalid credentials',
  }, 401));
  await page.goto('/login', { waitUntil: 'domcontentloaded' });

  await page.locator('#username').fill('keyboard@example.test');
  await page.locator('#password').fill('wrong-password');
  await page.locator('button[type="submit"]').click();

  const alert = page.getByRole('alert');
  await expect(alert).toBeVisible();
  await expect(alert).toHaveAttribute('aria-live', 'assertive');
  await expect(alert).toBeFocused();
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T342-login-error-focus.png', fullPage: true });
});

test('restores authenticated overlay focus and focuses main after route navigation', async ({ page }) => {
  const user = { id: 9, username: 'keyboard-admin', fullName: 'Keyboard Admin',
    roles: ['SUPER_ADMIN'], permissions: [] };
  await installAdminSession(page, user);
  await page.route('**/ws*/**', route => route.abort());
  await page.route('**/api/**', route => adminApi(route, user));
  await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('app-dashboard')).toBeVisible({ timeout: 30_000 });

  const notificationTrigger = page.locator('button[aria-controls="notification-panel"]');
  await notificationTrigger.focus();
  await page.keyboard.press('Enter');
  await expect(page.locator('#notification-panel')).toBeVisible();
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T342-admin-notification-dialog.png', fullPage: true });
  await page.keyboard.press('Escape');
  await expect(page.locator('#notification-panel')).toHaveCount(0);
  await expect(notificationTrigger).toBeFocused();

  const profileTrigger = page.locator('button[aria-controls="user-menu"]');
  await profileTrigger.focus();
  await page.keyboard.press('Enter');
  const profileAction = page.locator('#user-menu button[role="menuitem"]').first();
  await profileAction.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/admin\/profile$/);
  await expect(page.locator('#main-content')).toBeFocused();
});

async function installAdminSession(page: Page, user: Record<string, unknown>): Promise<void> {
  const token = browserToken(Date.now() + 10 * 60_000);
  await page.addInitScript(({ accessToken, sessionUser }) => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify(sessionUser));
  }, { accessToken: token, sessionUser: user });
}

async function adminApi(route: Route, user: Record<string, unknown>): Promise<void> {
  const path = new URL(route.request().url()).pathname;
  if (path === '/api/users/me') return json(route, { ...user, status: 'ACTIVE', email: 'keyboard@example.test' });
  if (path === '/api/auth/my-menu') return json(route, [{
    id: 1,
    code: 'SYSTEM',
    name: 'System',
    functions: [{ id: 1, code: 'REPORT', name: 'Dashboard', url: '/admin/dashboard', icon: 'pi pi-chart-bar' }],
  }]);
  if (path === '/api/analytics/dashboard') return json(route, {
    totalRevenue: 0, totalBookings: 0, occupancyRate: 0, totalRooms: 0, occupiedRooms: 0,
    operationalProperties: 0, scope: 'SYSTEM_NON_DEMO', revenueBasis: 'PLATFORM_BILLING_NET',
    occupancyBasis: 'ASSIGNED_AND_LEGACY_STAYS_OVER_OPERATIONAL_ROOMS', reconciliationStatus: 'RECONCILED',
    sourceWatermark: 'T342', generatedAt: '2026-08-04T06:00:00Z', periodFrom: '2026-07-29',
    periodTo: '2026-08-04', labels: [], revenueData: [], occupancyData: [],
  });
  if (path.includes('/notifications')) return json(route, []);
  return json(route, route.request().method() === 'GET' ? {} : {});
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1_000), sub: 't342-keyboard'
  })}.test-signature`;
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, json: body });
}
