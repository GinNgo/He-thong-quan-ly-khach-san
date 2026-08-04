import AxeBuilder from '@axe-core/playwright';
import { Browser, BrowserContext, expect, Page, Route, test } from '@playwright/test';

type Permission = { function: string; actionMask: number };
type Scenario = {
  name: string;
  path: string;
  ready: string;
  roles: string[];
  permissions: Permission[];
};

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
  usageScope: {
    properties: 'OWNER_ACCOUNT', rooms: 'SELECTED_PROPERTY', roomTypes: 'SELECTED_PROPERTY',
    staff: 'SELECTED_PROPERTY', images: 'SELECTED_PROPERTY',
  },
  scope: 'SELECTED_PROPERTY',
  generatedAt: '2026-08-04T06:00:00Z',
  sourceWatermark: 'PROPERTY:11',
  upgradeRequired: false,
  dashboard: {
    totalRooms: 4, availableRooms: 2, reservedRooms: 1, occupiedRooms: 1, dirtyRooms: 0,
    maintenanceRooms: 0, unclassifiedRooms: 0, pendingHousekeeping: 1, classifiedRooms: 4,
    reconciliationStatus: 'RECONCILED', countBasis: 'ROOM_STATUS_BY_SELECTED_PROPERTY',
  },
};

const scenarios: Scenario[] = [
  { name: 'customer', path: '/notifications', ready: 'app-customer-notifications', roles: ['CUSTOMER'], permissions: [] },
  { name: 'owner', path: '/management/dashboard', ready: 'app-management-dashboard', roles: ['PROPERTY_OWNER'], permissions: [] },
  {
    name: 'receptionist', path: '/admin/reservations', ready: 'app-reservation-management', roles: ['RECEPTIONIST'],
    permissions: [{ function: 'RESERVATION', actionMask: 1 }],
  },
  {
    name: 'housekeeping', path: '/admin/rooms', ready: 'app-room-management', roles: ['HOUSEKEEPING'],
    permissions: [{ function: 'ROOM', actionMask: 1 }],
  },
  { name: 'admin', path: '/admin/dashboard', ready: 'app-dashboard', roles: ['SUPER_ADMIN'], permissions: [] },
];

test('gates critical authenticated journeys on WCAG A/AA and screen-reader semantics', async ({ browser }) => {
  test.setTimeout(180_000);

  for (const scenario of scenarios) {
    const { context, page } = await rolePage(browser, scenario);
    await page.goto(scenario.path, { waitUntil: 'domcontentloaded' });
    await expect(page.locator(scenario.ready)).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('.forbidden-container')).toHaveCount(0);

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
      .analyze();
    const releaseBlocking = results.violations.filter(violation =>
      violation.impact === 'critical' || violation.impact === 'serious');
    expect(releaseBlocking, `${scenario.name} axe violations:\n${formatViolations(releaseBlocking)}`).toEqual([]);

    const contrast = results.passes.find(rule => rule.id === 'color-contrast');
    expect(contrast?.nodes.length ?? 0, `${scenario.name} did not produce passing color-contrast evidence`).toBeGreaterThan(0);

    const tree = await accessibilityTree(context, page);
    expect(tree.mainCount, `${scenario.name} must expose a main landmark`).toBeGreaterThanOrEqual(1);
    expect(tree.headingNames, `${scenario.name} must expose a named heading`).not.toEqual([]);
    expect(tree.unnamedInteractive, `${scenario.name} unnamed controls in the accessibility tree`).toEqual([]);

    await page.screenshot({
      path: `../docs/testing/evidence/007/remediation/T343-${scenario.name}-accessibility.png`,
      fullPage: true,
    });
    await context.close();
  }
});

async function rolePage(browser: Browser, scenario: Scenario): Promise<{ context: BrowserContext; page: Page }> {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const user = {
    id: 7,
    username: `${scenario.name}-accessibility`,
    fullName: `${scenario.name} Accessibility`,
    roles: scenario.roles,
    permissions: scenario.permissions,
  };
  await page.addInitScript(({ accessToken, sessionUser }) => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify(sessionUser));
  }, { accessToken: browserToken(Date.now() + 10 * 60_000), sessionUser: user });
  await page.route('**/ws*/**', route => route.abort());
  await page.route('**/api/**', route => fulfillApi(route, user));
  return { context, page };
}

async function accessibilityTree(context: BrowserContext, page: Page) {
  const client = await context.newCDPSession(page);
  const response = await client.send('Accessibility.getFullAXTree');
  const nodes = response.nodes.filter(node => !node.ignored);
  const interactiveRoles = new Set([
    'button', 'checkbox', 'combobox', 'link', 'listbox', 'menuitem', 'radio', 'searchbox',
    'slider', 'spinbutton', 'switch', 'tab', 'textbox', 'treeitem',
  ]);
  const nameOf = (node: (typeof nodes)[number]) => node.name?.value?.trim() ?? '';
  return {
    mainCount: nodes.filter(node => node.role?.value === 'main').length,
    headingNames: nodes.filter(node => node.role?.value === 'heading').map(nameOf).filter(Boolean),
    unnamedInteractive: nodes
      .filter(node => interactiveRoles.has(node.role?.value ?? '') && !nameOf(node))
      .map(node => ({ role: node.role?.value, backendDOMNodeId: node.backendDOMNodeId })),
  };
}

async function fulfillApi(route: Route, user: Record<string, unknown>): Promise<void> {
  const request = route.request();
  const path = new URL(request.url()).pathname;
  if (path === '/api/users/me') return json(route, { ...user, status: 'ACTIVE', email: 'accessibility@example.test' });
  if (path === '/api/auth/my-menu') return json(route, menuFor(user));
  if (path === '/api/analytics/dashboard') return json(route, dashboard);
  if (path === '/api/management/context') return json(route, managementContext);
  if (path.startsWith('/api/customer/notifications')) {
    if (path.endsWith('/unread-count')) return json(route, { unreadCount: 0 });
    return json(route, {
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
      first: true, last: true, unreadCount: 0,
    });
  }
  if (path === '/api/reservations' || path === '/api/rooms') return json(route, []);
  if (path.includes('/notifications')) return json(route, { content: [], totalElements: 0 });
  return json(route, request.method() === 'GET' ? [] : {});
}

function menuFor(user: Record<string, unknown>) {
  const permissions = Array.isArray(user['permissions']) ? user['permissions'] as Permission[] : [];
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
    functions.push({ id: 99, code: 'REPORT', name: 'Bang dieu khien', url: '/admin/dashboard', icon: 'pi pi-chart-bar' });
  }
  return functions.length === 0 ? [] : [{ id: 1, code: 'OPERATIONS', name: 'Van hanh', functions }];
}

function formatViolations(violations: Array<{ id: string; impact?: string | null; help: string; nodes: Array<{ target: string[] }> }>) {
  return violations.map(violation =>
    `${violation.impact ?? 'unknown'} ${violation.id}: ${violation.help} (${violation.nodes.map(node => node.target.join(' ')).join(', ')})`,
  ).join('\n');
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1_000), sub: 't343-accessibility',
  })}.test-signature`;
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, json: body });
}
