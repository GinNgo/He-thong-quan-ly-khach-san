import { Browser, expect, Page, Route, test } from '@playwright/test';

const widths = [320, 375, 768, 1024, 1440];

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
  revenueData: [10, 15, 20, 10, 25, 20, 25],
  occupancyData: [20, 30, 40, 50, 50, 40, 50],
};

const managementContext = {
  properties: [{ id: 11, code: 'HAN', nameVi: 'LuxeStay Ha Noi', operational: true }],
  activePropertyId: 11,
  activePropertyOperational: true,
  planCode: 'STANDARD',
  subscriptionStatus: 'ACTIVE',
  subscriptionSource: 'PLATFORM',
  entitlementAuthoritative: true,
  entitlementReference: 'CONTRACT:11',
  lifetime: false,
  limits: { MAX_PROPERTIES: 3, MAX_ROOMS: 20, MAX_STAFF: 10 },
  usage: { properties: 1, roomTypes: 2, rooms: 4, staff: 2, images: 3 },
  usageScope: { properties: 'OWNER_ACCOUNT', rooms: 'SELECTED_PROPERTY', roomTypes: 'SELECTED_PROPERTY',
    staff: 'SELECTED_PROPERTY', images: 'SELECTED_PROPERTY' },
  scope: 'SELECTED_PROPERTY',
  generatedAt: '2026-08-04T06:00:00Z',
  sourceWatermark: 'PROPERTY:11',
  upgradeRequired: false,
  dashboard: { totalRooms: 4, availableRooms: 2, reservedRooms: 1, occupiedRooms: 1, dirtyRooms: 0,
    maintenanceRooms: 0, unclassifiedRooms: 0, pendingHousekeeping: 1, classifiedRooms: 4,
    reconciliationStatus: 'RECONCILED', countBasis: 'ROOM_STATUS_BY_SELECTED_PROPERTY' },
};

const scenarios = [
  { name: 'customer', path: '/notifications', ready: 'app-customer-notifications', roles: ['CUSTOMER'], permissions: [] },
  { name: 'owner', path: '/management/dashboard', ready: 'app-management-dashboard', roles: ['PROPERTY_OWNER'], permissions: [] },
  { name: 'receptionist', path: '/admin/reservations', ready: 'app-reservation-management', roles: ['RECEPTIONIST'],
    permissions: [{ function: 'RESERVATION', actionMask: 1 }] },
  { name: 'housekeeping', path: '/admin/rooms', ready: 'app-room-management', roles: ['HOUSEKEEPING'],
    permissions: [{ function: 'ROOM', actionMask: 1 }] },
  { name: 'admin', path: '/admin/dashboard', ready: 'app-dashboard', roles: ['SUPER_ADMIN'], permissions: [] },
];

test('keeps critical authenticated role routes inside every required viewport', async ({ browser }) => {
  test.setTimeout(180_000);
  const coverage: string[] = [];
  const selectedScenarios = process.env['RESPONSIVE_ROLE']
    ? scenarios.filter(scenario => scenario.name === process.env['RESPONSIVE_ROLE'])
    : scenarios;

  for (const scenario of selectedScenarios) {
    const page = await rolePage(browser, scenario);
    await page.goto(scenario.path, { waitUntil: 'domcontentloaded' });
    await expect(page.locator(scenario.ready)).toBeVisible({ timeout: 30_000 });
    const session = await page.evaluate(() => ({
      href: window.location.href,
      text: document.body.innerText.slice(0, 160),
      user: localStorage.getItem('user'),
      hasToken: Boolean(sessionStorage.getItem('token')),
    }));
    expect(page.url(), `${scenario.name} route denied with session ${JSON.stringify(session)}`)
      .not.toMatch(/\/403(?:[/?#]|$)/);
    await expect(page.locator('.forbidden-container'),
      `${scenario.name} rendered forbidden content with session ${JSON.stringify(session)}`).toHaveCount(0);

    for (const width of widths) {
      await page.setViewportSize({ width, height: width <= 375 ? 812 : 900 });
      await page.waitForTimeout(350);
      const result = await responsiveResult(page);
      expect(result.documentOverflow,
        `${scenario.name} ${width}px document overflow: ${JSON.stringify(result)}`).toBe(false);
      expect(result.overlayOverflow, `${scenario.name} ${width}px overlay overflow`).toEqual([]);
      coverage.push(`${scenario.name}:${width}`);

      if (width === 320) {
        const toggle = page.locator('.mobile-toggle, button[aria-controls="admin-navigation"], '
          + 'button[aria-controls="management-navigation"]').first();
        if (await toggle.isVisible()) {
          await toggle.click({ timeout: 5_000 });
          await page.waitForTimeout(350);
          const overlayResult = await responsiveResult(page);
          expect(overlayResult.documentOverflow, `${scenario.name} ${width}px navigation overflow`).toBe(false);
          await expect(page.locator('.forbidden-container')).toHaveCount(0);
          await page.screenshot({
            path: `../docs/testing/evidence/007/remediation/T341-${scenario.name}-320.png`,
            fullPage: true,
          });
        } else {
          await expect(page.locator('.forbidden-container')).toHaveCount(0);
          await page.screenshot({
            path: `../docs/testing/evidence/007/remediation/T341-${scenario.name}-320.png`,
            fullPage: true,
          });
        }
        await page.reload({ waitUntil: 'domcontentloaded' });
        await expect(page.locator(scenario.ready)).toBeVisible({ timeout: 30_000 });
      }
    }

    await page.context().close();
  }

  expect(coverage).toHaveLength(selectedScenarios.length * widths.length);
});

async function rolePage(
  browser: Browser,
  scenario: { name: string; roles: string[]; permissions: Array<{ function: string; actionMask: number }> },
): Promise<Page> {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const user = { id: 7, username: `${scenario.name}-responsive`, fullName: `${scenario.name} Responsive`,
    roles: scenario.roles, permissions: scenario.permissions };
  const token = browserToken(Date.now() + 10 * 60_000);
  await page.addInitScript(({ accessToken, sessionUser }) => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify(sessionUser));
  }, { accessToken: token, sessionUser: user });
  await page.route('**/ws*/**', route => route.abort());
  await page.route('**/api/**', route => fulfillApi(route, user));
  return page;
}

async function fulfillApi(route: Route, user: Record<string, unknown>): Promise<void> {
  const request = route.request();
  const path = new URL(request.url()).pathname;
  if (path === '/api/users/me') return json(route, { ...user, status: 'ACTIVE', email: 'responsive@example.test' });
  if (path === '/api/auth/my-menu') return json(route, menuFor(user));
  if (path === '/api/analytics/dashboard') return json(route, dashboard);
  if (path === '/api/management/context') return json(route, managementContext);
  if (path.startsWith('/api/customer/notifications')) {
    if (path.endsWith('/unread-count')) return json(route, { unreadCount: 0 });
    return json(route, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
      first: true, last: true, unreadCount: 0 });
  }
  if (path === '/api/reservations' || path === '/api/rooms') return json(route, []);
  if (path.includes('/notifications')) return json(route, { content: [], totalElements: 0 });
  return json(route, request.method() === 'GET' ? [] : {});
}

function menuFor(user: Record<string, unknown>) {
  const permissions = Array.isArray(user['permissions'])
    ? user['permissions'] as Array<{ function: string; actionMask: number }>
    : [];
  const roles = Array.isArray(user['roles']) ? user['roles'] as string[] : [];
  const functions = permissions
    .filter(permission => (permission.actionMask & 1) === 1)
    .map((permission, index) => ({
      id: index + 1,
      code: permission.function,
      name: permission.function === 'RESERVATION' ? 'Dat phong' : 'Phong',
      url: permission.function === 'RESERVATION' ? '/admin/reservations' : '/admin/rooms',
      icon: permission.function === 'RESERVATION' ? 'pi pi-calendar' : 'pi pi-building',
    }));
  if (roles.includes('SUPER_ADMIN')) {
    functions.push({ id: 99, code: 'REPORT', name: 'Bang dieu khien',
      url: '/admin/dashboard', icon: 'pi pi-chart-bar' });
  }
  return functions.length === 0 ? [] : [{ id: 1, code: 'OPERATIONS', name: 'Van hanh', functions }];
}

async function responsiveResult(page: Page) {
  return page.evaluate(() => {
    const viewportWidth = document.documentElement.clientWidth;
    const overlaySelectors = '[role="dialog"], .p-dialog, .p-overlaypanel, '
      + '.mobile-nav-open .admin-sidebar, .mobile-nav-open .management-sidebar';
    const overlayOverflow = Array.from(document.querySelectorAll<HTMLElement>(overlaySelectors))
      .filter(element => {
        const style = getComputedStyle(element);
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        const rect = element.getBoundingClientRect();
        return rect.left < -1 || rect.right > viewportWidth + 1;
      })
      .map(element => element.className || element.getAttribute('role') || element.tagName);
    const offenders = Array.from(document.querySelectorAll<HTMLElement>('body *'))
      .filter(element => {
        const style = getComputedStyle(element);
        if (style.display === 'none' || style.visibility === 'hidden') return false;
        const rect = element.getBoundingClientRect();
        return rect.right > viewportWidth + 1;
      })
      .sort((left, right) => right.getBoundingClientRect().right - left.getBoundingClientRect().right)
      .slice(0, 8)
      .map(element => ({ selector: element.className || element.tagName,
        left: Math.round(element.getBoundingClientRect().left),
        right: Math.round(element.getBoundingClientRect().right) }));
    return {
      documentOverflow: document.documentElement.scrollWidth > viewportWidth + 1,
      scrollWidth: document.documentElement.scrollWidth,
      viewportWidth,
      overlayOverflow,
      offenders,
    };
  });
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1_000), sub: 't341-responsive'
  })}.test-signature`;
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, json: body });
}
