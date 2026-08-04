import { expect, Page, Route, test } from '@playwright/test';

const dashboard = {
  totalRevenue: 125000000,
  totalBookings: 6,
  occupancyRate: 50,
  totalRooms: 10,
  occupiedRooms: 5,
  operationalProperties: 3,
  scope: 'SYSTEM_NON_DEMO',
  revenueBasis: 'PLATFORM_BILLING_NET',
  occupancyBasis: 'ASSIGNED_AND_LEGACY_STAYS_OVER_OPERATIONAL_ROOMS',
  reconciliationStatus: 'RECONCILED',
  sourceWatermark: 'PLATFORM:2026-08-04T06:00:00Z:12',
  generatedAt: '2026-08-04T06:00:00Z',
  periodFrom: '2026-07-29',
  periodTo: '2026-08-04',
  labels: ['29/07', '30/07', '31/07', '01/08', '02/08', '03/08', '04/08'],
  revenueData: [10000000, 15000000, 20000000, 10000000, 25000000, 20000000, 25000000],
  occupancyData: [20, 30, 40, 50, 50, 40, 50],
};

async function seedSystemAdmin(page: Page): Promise<void> {
  const token = browserToken(Date.now() + 60 * 60 * 1000);
  await page.addInitScript(accessToken => {
    const rewrite = (source: string) => source.startsWith('http://localhost:8080/api')
      ? source.replace('http://localhost:8080/api', '/api')
      : source;
    const originalFetch = globalThis.fetch.bind(globalThis);
    globalThis.fetch = (input: RequestInfo | URL, init?: RequestInit) => {
      const source = typeof input === 'string' || input instanceof URL ? input.toString() : input.url;
      const rewritten = rewrite(source);
      return input instanceof Request
        ? originalFetch(new Request(rewritten, input), init)
        : originalFetch(rewritten, init);
    };
    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method: string, url: string | URL, async = true,
      username?: string | null, password?: string | null) {
      return originalOpen.call(this, method, rewrite(url.toString()), async, username, password);
    };
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({
      id: 1,
      username: 'system-admin',
      fullName: 'System Admin',
      roles: ['SUPER_ADMIN'],
      permissions: [{ function: 'REPORT', actionMask: 17 }],
    }));
  }, token);
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ exp: Math.floor(expiresAt / 1000) })}.test-signature`;
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, json: body });
}

async function fulfillShellRequest(route: Route): Promise<void> {
  const path = new URL(route.request().url()).pathname;
  if (path === '/api/users/me') {
    await json(route, {
      id: 1, username: 'system-admin', fullName: 'System Admin',
      roles: ['SUPER_ADMIN'], status: 'ACTIVE',
    });
  } else if (path === '/api/auth/my-menu') {
    await json(route, []);
  } else if (path.includes('/notifications')) {
    await json(route, { content: [], totalElements: 0 });
  } else {
    await json(route, []);
  }
}

test.describe('T329 authoritative admin dashboard', () => {
  test.describe.configure({ mode: 'serial', retries: 0, timeout: 60_000 });

  test('shows loading before reconciled non-demo system metrics and evidence metadata', async ({ page }) => {
    await seedSystemAdmin(page);

    await page.route('**/api/**', async route => {
      if (new URL(route.request().url()).pathname === '/api/analytics/dashboard') {
        await new Promise(resolve => setTimeout(resolve, 1500));
        await json(route, dashboard);
      } else {
        await fulfillShellRequest(route);
      }
    });

    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Dang doi chieu du lieu bao cao va van hanh...')).toBeVisible();

    await expect(page.getByText('125.000.000')).toBeVisible();
    await expect(page.getByText('5 / 10')).toBeVisible();
    await expect(page.getByText('50.0%')).toBeVisible();
    await expect(page.getByText('PLATFORM_BILLING_NET')).toBeVisible();
    await expect(page.getByText('SYSTEM_NON_DEMO')).toBeVisible();
    await expect(page.getByText('RECONCILED')).toBeVisible();
    await expect(page.getByText(/PLATFORM:2026-08-04T06:00:00Z:12/)).toBeVisible();
    await expect(page.getByText('AI Du bao')).toHaveCount(0);

    await page.screenshot({
      path: '../docs/testing/evidence/007/remediation/T329-admin-authoritative-dashboard.png',
      fullPage: true,
    });
  });

  test('shows a truthful error and recovers through the visible retry action', async ({ page }) => {
    await seedSystemAdmin(page);
    let dashboardCalls = 0;
    await page.route('**/api/**', async route => {
      if (new URL(route.request().url()).pathname === '/api/analytics/dashboard') {
        dashboardCalls += 1;
        if (dashboardCalls === 1) {
          await json(route, { code: 'DASHBOARD_UNAVAILABLE' }, 503);
        } else {
          await json(route, dashboard);
        }
      } else {
        await fulfillShellRequest(route);
      }
    });

    await page.goto('/admin/dashboard', { waitUntil: 'domcontentloaded' });
    const alert = page.getByRole('alert');
    await expect(alert).toContainText('Khong the tai du lieu van hanh');
    await expect(page.locator('app-stat-card')).toHaveCount(0);
    await alert.getByRole('button', { name: 'Thu lai' }).click();
    await expect(page.getByText('RECONCILED')).toBeVisible();
    expect(dashboardCalls).toBe(2);

    await page.screenshot({
      path: '../docs/testing/evidence/007/remediation/T329-admin-dashboard-error-retry.png',
      fullPage: true,
    });
  });
});
