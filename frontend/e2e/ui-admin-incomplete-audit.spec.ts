import { expect, type Page, test } from '@playwright/test';
import { seedSession, syntheticAdminSession } from './helpers/audit-fixtures';
import { attachJson, collectRuntimeIssues } from './helpers/ui-audit';

async function installAdminFixtures(page: Page): Promise<void> {
  await seedSession(page, syntheticAdminSession());
  await page.route('**/api/users/me', route => route.fulfill({
    contentType: 'application/json',
    json: {
      createdAt: '2026-08-01T00:00:00Z', email: 'audit-admin@example.test', fullName: 'UI Audit Admin',
      id: 1, roles: ['ADMIN'], status: 'ACTIVE', username: 'admin',
    },
  }));
  await page.route('**/api/notifications', route => route.fulfill({ contentType: 'application/json', json: [] }));
  await page.route('**/api/auth/my-menu', route => route.fulfill({
    contentType: 'application/json',
    json: [{
      code: 'REPORTING',
      functions: [{ code: 'REPORT', icon: 'pi pi-chart-bar', id: 1, name: 'Dashboard', url: '/admin/dashboard' }],
      id: 1,
      name: 'Audit',
    }],
  }));
  await page.route('**/api/analytics/dashboard', route => route.fulfill({
    contentType: 'application/json',
    json: {
      aiPredictedOccupancy: [51, 62], labels: ['T1', 'T2'], occupancyData: [50, 60], occupancyRate: 60,
      revenueData: [1_000_000, 2_000_000], totalBookings: 7, totalRevenue: 3_000_000,
    },
  }));
  await page.route('**/ws/**', route => route.abort());
}

test.describe('Admin dashboard incomplete capability audit', () => {
  test.describe.configure({ retries: 0 });
  test.beforeEach(async ({ page }) => installAdminFixtures(page));

  test('profile onboarding CTA performs navigation or a mutation', async ({ page }, testInfo) => {
    const requests: string[] = [];
    page.on('request', request => {
      if (request.method() !== 'GET') requests.push(`${request.method()} ${request.url()}`);
    });
    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    const cta = page.locator('app-dashboard section').first().locator('button').first();
    await expect(cta).toBeVisible();
    const before = page.url();
    await cta.click();
    await page.waitForTimeout(300);
    const outcome = { after: page.url(), before, mutationRequests: requests };
    await attachJson(testInfo, 'profile-onboarding-cta-outcome', outcome);
    expect(outcome.after !== outcome.before || outcome.mutationRequests.length > 0,
      'Visible onboarding CTA has no navigation or mutation').toBe(true);
  });

  test('approval CTA can become actionable from loaded dashboard state', async ({ page }, testInfo) => {
    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    const approval = page.locator('app-dashboard section').first().locator('button').nth(3);
    await expect(approval).toBeVisible();
    const state = { disabled: await approval.isDisabled(), text: (await approval.textContent())?.trim() };
    await attachJson(testInfo, 'approval-cta-state', state);
    await testInfo.attach('approval-cta-screenshot', {
      body: await page.screenshot({ fullPage: true }),
      contentType: 'image/png',
    });
    expect(state.disabled, 'Approval CTA remains disabled because onboarding state is hardcoded and not loaded').toBe(false);
  });

  test('dashboard stat cards reflect non-zero analytics response', async ({ page }, testInfo) => {
    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    const cards = page.locator('app-dashboard app-stat-card');
    await expect(cards).toHaveCount(4);
    const text = (await cards.allTextContents()).map(value => value.replace(/\s+/g, ' ').trim());
    await attachJson(testInfo, 'dashboard-stat-cards', text);
    expect(text.some(value => /[1-9]/.test(value)), 'All dashboard stat cards stay at zero despite non-zero analytics').toBe(true);
  });

  test('work-order table loads from an API rather than a timer-only empty state', async ({ page }, testInfo) => {
    const workOrderRequests: string[] = [];
    page.on('request', request => {
      if (/work.?orders?|maintenance/i.test(request.url())) workOrderRequests.push(request.url());
    });
    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('app-dashboard app-data-table')).toBeVisible();
    await page.waitForTimeout(800);
    await attachJson(testInfo, 'work-order-requests', workOrderRequests);
    expect(workOrderRequests, 'Work-order table never requests real data').not.toEqual([]);
  });

  test('Excel export produces a download', async ({ page }) => {
    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    const exportButton = page.locator('app-dashboard app-data-table button:has(.pi-file-excel)');
    await expect(exportButton).toBeVisible();
    const download = page.waitForEvent('download', { timeout: 2_000 });
    await exportButton.click();
    await download;
  });

  test('PDF export produces a download without runtime errors', async ({ page }, testInfo) => {
    const issues = collectRuntimeIssues(page);
    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    const exportButton = page.locator('app-dashboard app-data-table button:has(.pi-file-pdf)');
    await expect(exportButton).toBeVisible();
    const download = page.waitForEvent('download', { timeout: 2_000 });
    await exportButton.click();
    await attachJson(testInfo, 'pdf-export-runtime-issues', issues);
    await download;
  });
});
