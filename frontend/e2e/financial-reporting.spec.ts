import { expect, Page, Route, test } from '@playwright/test';

interface SessionUser {
  id: number;
  username: string;
  fullName: string;
  roles: string[];
  permissions: Array<{ function: string; actionMask: number }>;
}

const propertyId = 11;

async function seedSession(page: Page, user: SessionUser): Promise<void> {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${encode({ alg: 'HS256' })}.${encode({ exp: Math.floor(Date.now() / 1000) + 3600 })}.test`;
  await page.addInitScript(({ sessionUser, accessToken }) => {
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
    XMLHttpRequest.prototype.open = function(method: string, url: string | URL, async = true, username?: string | null, password?: string | null) {
      return originalOpen.call(this, method, rewrite(url.toString()), async, username, password);
    };
    localStorage.setItem('luxestay.locale', 'vi');
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify(sessionUser));
  }, { sessionUser: user, accessToken: token });
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, json: body });
}

function managementContext() {
  return {
    properties: [{
      id: propertyId,
      code: 'PROP-11',
      nameVi: 'LuxeStay Đà Nẵng',
      propertyType: 'HOTEL',
      address: 'Đà Nẵng',
      approvalStatus: 'APPROVED',
      operationStatus: 'ACTIVE',
      isDemo: false,
    }],
    activePropertyId: propertyId,
    planCode: 'PRO',
    subscriptionStatus: 'ACTIVE',
    lifetime: false,
    limits: {},
    usage: {},
    upgradeRequired: false,
  };
}

function report(context: 'PROPERTY_COMMERCE' | 'PLATFORM_BILLING') {
  const property = context === 'PROPERTY_COMMERCE';
  const publicId = property ? 'PROP-TX-11' : 'PLAT-TX-PRO';
  const filters = property
    ? { context, basis: 'NET', fromInclusive: '2026-07-01T00:00:00Z', toExclusive: '2026-08-01T00:00:00Z', zoneId: 'Asia/Ho_Chi_Minh', propertyId }
    : { context, basis: 'NET', fromInclusive: '2026-07-01T00:00:00Z', toExclusive: '2026-08-01T00:00:00Z', zoneId: 'Asia/Ho_Chi_Minh', planCode: 'PRO' };
  return {
    context,
    basis: 'NET',
    filters,
    totals: {
      grossRevenue: property ? 1500000 : 3000000,
      refunds: property ? 100000 : 200000,
      credits: property ? 0 : 100000,
      netRevenue: property ? 1400000 : 2700000,
      cashCollected: property ? 1500000 : 3000000,
      invoicedRevenue: property ? 1500000 : 3000000,
      unpaidBalance: property ? 200000 : 0,
      heldDeposits: property ? 50000 : 0,
      successfulTransactionCount: property ? 2 : 3,
      failedTransactionCount: property ? 0 : 1,
      unreconciledTransactionCount: 0,
    },
    breakdowns: [{
      dimension: property ? 'ROOM_TYPE' : 'PLAN',
      code: property ? 'DELUXE' : 'PRO',
      label: property ? 'Deluxe' : 'PRO',
      transactionCount: property ? 2 : 3,
      grossRevenue: property ? 1500000 : 3000000,
      refunds: property ? 100000 : 200000,
      credits: property ? 0 : 100000,
      netRevenue: property ? 1400000 : 2700000,
      recurringEligible: !property,
    }],
    rows: [{
      context,
      publicId,
      occurredAt: '2026-07-20T10:00:00Z',
      transactionType: property ? 'BOOKING_DEPOSIT' : 'PURCHASE',
      sourceType: property ? 'TRANSACTION' : 'PLATFORM_TRANSACTION',
      sourceId: publicId,
      ...(property ? { propertyId } : {}),
      method: property ? 'BANK_TRANSFER' : 'EWALLET',
      provider: property ? 'BANK' : 'MOMO',
      grossAmount: property ? 1500000 : 3000000,
      refundAmount: property ? 100000 : 200000,
      creditAmount: property ? 0 : 100000,
      netAmount: property ? 1400000 : 2700000,
      dimensions: property ? { ROOM_TYPE: 'DELUXE' } : { PLAN_CODE: 'PRO' },
      reconciliationStatus: 'RECONCILED',
    }],
    reconciliationIssues: [],
    totalRowCount: 1,
    sourceWatermark: property ? 'PROPERTY:11:2026-07-20T10:00:00Z' : 'PLATFORM:2026-07-20T10:00:00Z',
    generatedAt: '2026-07-20T10:00:00Z',
  };
}

test.describe('Financial reporting browser journey', () => {
  test.describe.configure({ mode: 'serial', retries: 0, timeout: 120_000 });

  test('owner filters a property report and cross-property scope is denied', async ({ page }) => {
    await seedSession(page, {
      id: 110,
      username: 'property-owner',
      fullName: 'Property Owner',
      roles: ['PROPERTY_OWNER'],
      permissions: [{ function: 'REPORT', actionMask: 17 }],
    });
    const reportRequests: string[] = [];
    const largeReport = report('PROPERTY_COMMERCE');
    largeReport.rows = Array.from({ length: 120 }, (_, index) => ({
      ...largeReport.rows[0],
      publicId: `PROP-TX-${String(index + 1).padStart(3, '0')}`,
      sourceId: `PROP-TX-${String(index + 1).padStart(3, '0')}`,
    }));
    largeReport.totalRowCount = 120;
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/management/context') {
        await json(route, managementContext());
      } else if (url.pathname === '/api/management/reports/property-revenue') {
        reportRequests.push(url.toString());
        const requestedPropertyId = Number(url.searchParams.get('propertyId'));
        if (requestedPropertyId !== propertyId) {
          await json(route, { message: 'Không thể truy cập cơ sở này.' }, 404);
        } else {
          await json(route, largeReport);
        }
      } else if (url.pathname === '/api/management/reports/property-revenue/export') {
        await route.fulfill({
          status: 200,
          headers: {
            'content-type': 'text/csv',
            'content-disposition': 'attachment; filename="property-revenue.csv"',
          },
          body: 'publicId,netAmount\nPROP-TX-001,1400000\n',
        });
      } else {
        await json(route, []);
      }
    });

    await page.goto(`/management/property-revenue?propertyId=${propertyId}`, { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Doanh thu cơ sở', level: 2 })).toBeVisible();
    await expect(page.getByText('PROP-TX-001')).toBeVisible();
    await expect(page.getByText('PLAT-TX-PRO')).toHaveCount(0);
    await expect(page.getByText('1 / 3', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Trang sau' }).click();
    await expect(page.getByText('PROP-TX-051')).toBeVisible();
    await expect(page.getByText('PROP-TX-001')).toHaveCount(0);
    await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T334-property-revenue-pagination-export.png' });

    await page.locator('select[name="basis"]').selectOption('CASH_COLLECTED');
    await page.locator('input[name="fromDate"]').fill('2026-07-10');
    await page.getByRole('button', { name: 'Cập nhật báo cáo' }).click();
    await expect.poll(() => reportRequests.some(request => {
      const url = new URL(request);
      return url.searchParams.get('basis') === 'CASH_COLLECTED'
        && url.searchParams.get('from') === '2026-07-10'
        && url.searchParams.get('propertyId') === String(propertyId);
    })).toBe(true);

    await page.goto('/management/property-revenue?propertyId=99', { waitUntil: 'domcontentloaded' });
    await expect(page.getByText('Không thể truy cập cơ sở này.')).toBeVisible();
    expect(reportRequests.some(request => new URL(request).searchParams.get('propertyId') === '99')).toBe(true);

    await page.goto(`/management/property-revenue?propertyId=${propertyId}`, { waitUntil: 'domcontentloaded' });
    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'CSV', exact: true }).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toContain('property-revenue');
  });

  test('system admin filters and exports Platform Billing without property leakage', async ({ page }) => {
    await seedSession(page, {
      id: 1,
      username: 'system-admin',
      fullName: 'System Admin',
      roles: ['SUPER_ADMIN'],
      permissions: [{ function: 'PLATFORM_REVENUE', actionMask: 17 }],
    });
    const platformRequests: string[] = [];
    const exportChecksum = 'a'.repeat(64);
    let propertyReportRequests = 0;
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/admin/reports/platform-revenue/export') {
        platformRequests.push(url.toString());
        await route.fulfill({
          status: 200,
          headers: {
            'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'X-Report-Checksum': exportChecksum,
            'X-Report-Row-Count': '1',
          },
          body: `deterministic-excel-fixture checksum=${exportChecksum}`,
        });
      } else if (url.pathname === '/api/admin/reports/platform-revenue') {
        platformRequests.push(url.toString());
        await json(route, report('PLATFORM_BILLING'));
      } else if (url.pathname.includes('/reports/property-revenue')) {
        propertyReportRequests += 1;
        await json(route, { message: 'Wrong bounded context.' }, 500);
      } else if (url.pathname === '/api/users/me') {
        await json(route, { id: 1, username: 'system-admin', fullName: 'System Admin', email: 'admin@luxestay.test', roles: ['SUPER_ADMIN'], status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' });
      } else if (url.pathname === '/api/auth/my-menu') {
        await json(route, [{ id: 1, code: 'REPORTING', name: 'Báo cáo', functions: [{ id: 1, code: 'PLATFORM_REVENUE', name: 'Doanh thu nền tảng', url: '/admin/platform-revenue', icon: 'pi pi-chart-line' }] }]);
      } else {
        await json(route, []);
      }
    });

    await page.goto('/admin/platform-revenue', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { name: 'Doanh thu nền tảng', level: 2 })).toBeVisible();
    await expect(page.getByText('PLAT-TX-PRO')).toBeVisible();
    await expect(page.getByText('PROP-TX-11')).toHaveCount(0);

    await page.locator('select[name="planCode"]').selectOption('PRO');
    await page.getByRole('button', { name: 'Cập nhật', exact: true }).click();
    await expect.poll(() => platformRequests.some(request => {
      const url = new URL(request);
      return url.pathname.endsWith('/platform-revenue')
        && url.searchParams.get('planCode') === 'PRO'
        && !url.searchParams.has('propertyId');
    })).toBe(true);

    const downloadPromise = page.waitForEvent('download');
    const exportResponsePromise = page.waitForResponse(response =>
      new URL(response.url()).pathname === '/api/admin/reports/platform-revenue/export');
    await page.getByRole('button', { name: 'Excel' }).click();
    const [download, exportResponse] = await Promise.all([downloadPromise, exportResponsePromise]);
    expect(download.suggestedFilename()).toBe('luxestay-platform-revenue.xlsx');
    expect(exportResponse.headers()['x-report-checksum']).toBe(exportChecksum);
    expect(exportResponse.headers()['x-report-row-count']).toBe('1');
    const stream = await download.createReadStream();
    const chunks: Buffer[] = [];
    for await (const chunk of stream) chunks.push(Buffer.from(chunk));
    expect(Buffer.concat(chunks).toString('utf8')).toContain(exportChecksum);
    expect(platformRequests.some(request => {
      const url = new URL(request);
      return url.pathname.endsWith('/export')
        && url.searchParams.get('format') === 'EXCEL'
        && url.searchParams.get('planCode') === 'PRO';
    })).toBe(true);
    expect(propertyReportRequests).toBe(0);
    await page.screenshot({
      path: '../docs/testing/evidence/007/remediation/T335-platform-revenue-database-export.png',
      fullPage: true,
    });
  });

  test('property report permission cannot open the platform dashboard', async ({ page }) => {
    await seedSession(page, {
      id: 111,
      username: 'property-only-user',
      fullName: 'Property Only User',
      roles: ['HOTEL_ADMIN'],
      permissions: [{ function: 'REPORT', actionMask: 17 }],
    });
    await page.goto('/admin/platform-revenue', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/403$/);
  });
});
