import { expect, Page, Route, test } from '@playwright/test';

test.setTimeout(60_000);

const properties = [
  { id: 11, code: 'HAN', nameVi: 'LuxeStay Ha Noi', propertyType: 'HOTEL', address: 'Ha Noi', approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', operational: true, isDemo: false },
  { id: 22, code: 'HUE', nameVi: 'LuxeStay Hue', propertyType: 'HOTEL', address: 'Hue', approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', operational: true, isDemo: false },
];

function context(propertyId: number) {
  const hue = propertyId === 22;
  const totalRooms = hue ? 7 : 4;
  return {
    properties,
    activePropertyId: propertyId,
    activePropertyOperational: true,
    planCode: hue ? 'PRO' : 'STANDARD',
    subscriptionStatus: 'ACTIVE',
    subscriptionSource: 'PLATFORM',
    entitlementAuthoritative: true,
    entitlementReference: `CONTRACT:${propertyId}`,
    lifetime: false,
    limits: { MAX_PROPERTIES: 3, MAX_ROOMS: hue ? 30 : 20, MAX_STAFF: 10 },
    usage: { properties: 2, roomTypes: 2, rooms: totalRooms, staff: hue ? 5 : 2, images: 3 },
    usageScope: { properties: 'OWNER_ACCOUNT', rooms: 'SELECTED_PROPERTY', roomTypes: 'SELECTED_PROPERTY', staff: 'SELECTED_PROPERTY', images: 'SELECTED_PROPERTY' },
    scope: 'SELECTED_PROPERTY',
    generatedAt: '2026-08-04T06:00:00Z',
    sourceWatermark: `PROPERTY:${propertyId}`,
    upgradeRequired: false,
    dashboard: {
      totalRooms,
      availableRooms: hue ? 2 : 3,
      reservedRooms: hue ? 1 : 0,
      occupiedRooms: hue ? 3 : 1,
      dirtyRooms: hue ? 1 : 0,
      maintenanceRooms: hue ? 1 : 0,
      unclassifiedRooms: 0,
      pendingHousekeeping: hue ? 2 : 0,
      classifiedRooms: totalRooms,
      reconciliationStatus: 'RECONCILED',
      countBasis: 'ROOM_STATUS_BY_SELECTED_PROPERTY',
    },
  };
}

async function seedOwner(page: Page): Promise<void> {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ exp: Math.floor(Date.now() / 1000) + 3600 })}.test`;
  await page.addInitScript(accessToken => {
    const rewrite = (source: string) => source.startsWith('http://localhost:8080/api')
      ? source.replace('http://localhost:8080/api', '/api') : source;
    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method: string, url: string | URL, async = true, username?: string | null, password?: string | null) {
      return originalOpen.call(this, method, rewrite(url.toString()), async, username, password);
    };
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'owner', fullName: 'Property Owner', roles: ['PROPERTY_OWNER'], permissions: [] }));
  }, token);
}

async function json(route: Route, body: unknown, status = 200): Promise<void> {
  await route.fulfill({ status, json: body });
}

test('switches authoritative selected-property counts and hides foreign data after privacy-safe denial', async ({ page }) => {
  await seedOwner(page);
  const requestedPropertyIds: string[] = [];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/management/context') {
      const selected = url.searchParams.get('activePropertyId');
      requestedPropertyIds.push(selected || 'default');
      if (selected === '999') return json(route, { message: 'Khong tim thay co so.' }, 404);
      return json(route, context(selected ? Number(selected) : 11));
    }
    if (url.pathname === '/api/users/me') return json(route, { id: 7, username: 'owner', fullName: 'Property Owner', roles: ['PROPERTY_OWNER'], status: 'ACTIVE' });
    if (url.pathname === '/api/auth/my-menu') return json(route, []);
    if (url.pathname.includes('/notifications')) return json(route, { content: [], totalElements: 0 });
    return json(route, []);
  });

  await page.goto('/management/dashboard');
  await expect(page.getByText('PROPERTY:11')).toBeVisible();
  await expect(page.getByText(/4 da phan loai|4 đã phân loại/)).toBeVisible();

  const propertySelect = page.locator('#active-property');
  await propertySelect.selectOption({ label: 'LuxeStay Hue' });
  await expect(page.getByText('PROPERTY:22')).toBeVisible();
  await expect(page.getByText(/7 da phan loai|7 đã phân loại/)).toBeVisible();
  await expect(page.getByText('PRO', { exact: true })).toBeVisible();
  await expect(page.locator('#active-property')).toHaveValue('22');
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T332-management-dashboard-property-switch.png', fullPage: true });

  await page.evaluate(() => {
    const select = document.querySelector('#active-property') as HTMLSelectElement;
    const option = document.createElement('option');
    option.value = '999';
    option.textContent = 'Foreign Property';
    select.append(option);
    select.value = '999';
    select.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await expect(page.getByText('Khong tim thay co so.')).toBeVisible();
  await expect(page.getByText('PROPERTY:22')).toHaveCount(0);
  await expect(page.getByText(/7 da phan loai|7 đã phân loại/)).toHaveCount(0);
  expect(requestedPropertyIds).toContain('22');
  expect(requestedPropertyIds).toContain('999');

  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T332-management-dashboard-idor-denial.png', fullPage: true });
});
