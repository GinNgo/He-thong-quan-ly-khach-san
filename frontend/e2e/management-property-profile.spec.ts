import { expect, Page, Route, test } from '@playwright/test';

test.setTimeout(60_000);

const properties = [
  { id: 11, code: 'HAN', nameVi: 'LuxeStay Ha Noi', nameEn: 'LuxeStay Hanoi', propertyType: 'HOTEL', address: '1 Old Street', provinceId: 1, wardId: 2, approvalStatus: 'APPROVED', operationStatus: 'ACTIVE', operational: true, phone: '0900000000', isDemo: false },
  { id: 22, code: 'HUE', nameVi: 'LuxeStay Hue', propertyType: 'HOTEL', address: '2 Old Street', provinceId: 3, wardId: 4, approvalStatus: 'PENDING_APPROVAL', operationStatus: 'INACTIVE', operational: false, isDemo: false },
];

async function seedOwner(page: Page): Promise<void> {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${encode({ alg: 'HS256' })}.${encode({ exp: Math.floor(Date.now() / 1000) + 3600 })}.test`;
  await page.addInitScript(accessToken => {
    const rewrite = (source: string) => source.startsWith('http://localhost:8080/api') ? source.replace('http://localhost:8080/api', '/api') : source;
    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method: string, url: string | URL, async = true, username?: string | null, password?: string | null) {
      return originalOpen.call(this, method, rewrite(url.toString()), async, username, password);
    };
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'owner', fullName: 'Property Owner', roles: ['PROPERTY_OWNER'], permissions: [] }));
  }, token);
}

async function json(route: Route, body: unknown, status = 200): Promise<void> { await route.fulfill({ status, json: body }); }

test('shows a distinct property profile and edits allowlisted fields without controlled state', async ({ page }) => {
  await seedOwner(page);
  let updateBody: Record<string, unknown> | undefined;
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/management/properties' && route.request().method() === 'GET') return json(route, properties);
    if (url.pathname === '/api/management/properties/11' && route.request().method() === 'PUT') {
      updateBody = route.request().postDataJSON();
      return json(route, { ...properties[0], ...updateBody });
    }
    if (url.pathname === '/api/management/context') return json(route, { properties, activePropertyId: 11, activePropertyOperational: true, planCode: 'PRO', subscriptionStatus: 'ACTIVE', lifetime: false, limits: {}, usage: {}, upgradeRequired: false });
    if (url.pathname === '/api/public/locations/provinces') return json(route, [{ id: 1, nameVi: 'Ha Noi' }, { id: 3, nameVi: 'Hue' }]);
    if (url.pathname === '/api/public/locations/provinces/1/wards') return json(route, [{ id: 2, nameVi: 'Ba Dinh' }]);
    if (url.pathname === '/api/users/me') return json(route, { id: 7, username: 'owner', fullName: 'Property Owner', roles: ['PROPERTY_OWNER'], status: 'ACTIVE' });
    if (url.pathname === '/api/auth/my-menu') return json(route, []);
    if (url.pathname.includes('/notifications')) return json(route, { content: [], totalElements: 0 });
    return json(route, []);
  });

  await page.goto('/management/properties');
  const main = page.locator('#management-main-content');
  await expect(main.getByRole('heading', { name: 'Cơ sở lưu trú' })).toBeVisible();
  await expect(page.getByText('Đối soát số phòng')).toHaveCount(0);
  await expect(main.getByText('APPROVED · ACTIVE').last()).toBeVisible();
  await main.getByLabel('Tên tiếng Việt').fill('LuxeStay Capital');
  await main.getByLabel('Địa chỉ').fill('99 New Street');
  await main.getByRole('button', { name: 'Lưu hồ sơ' }).click();
  await expect(page.getByText('Đã lưu hồ sơ cơ sở.')).toBeVisible();
  expect(updateBody?.['nameVi']).toBe('LuxeStay Capital');
  expect(updateBody?.['approvalStatus']).toBeUndefined();
  expect(updateBody?.['operationStatus']).toBeUndefined();
  expect(updateBody?.['isDemo']).toBeUndefined();
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T333-management-property-profile.png' });
});
