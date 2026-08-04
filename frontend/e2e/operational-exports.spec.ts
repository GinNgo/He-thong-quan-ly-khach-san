import { expect, Page, Route, test } from '@playwright/test';

async function seed(page: Page): Promise<void> {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${encode({ alg: 'HS256' })}.${encode({ exp: Math.floor(Date.now() / 1000) + 3600 })}.test`;
  await page.addInitScript(accessToken => {
    const rewrite = (source: string) => source.startsWith('http://localhost:8080/api') ? source.replace('http://localhost:8080/api', '/api') : source;
    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method: string, url: string | URL, async = true, username?: string | null, password?: string | null) {
      return originalOpen.call(this, method, rewrite(url.toString()), async, username, password);
    };
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'owner', fullName: 'Owner', roles: ['PROPERTY_OWNER'], permissions: [{ function: 'REPORT', actionMask: 17 }] }));
  }, token);
}

async function json(route: Route, body: unknown, status = 200): Promise<void> { await route.fulfill({ status, json: body }); }

test('exports a PII-minimized operational schema within selected property scope', async ({ page }) => {
  await seed(page);
  const requests: URL[] = [];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/management/context') return json(route, { properties: [{ id: 11, nameVi: 'LuxeStay Da Nang' }], activePropertyId: 11 });
    if (url.pathname === '/api/management/rooms') return json(route, [{ id: 1, roomNumber: '201', roomTypeNameVi: 'Deluxe', status: 'AVAILABLE', floor: 2 }]);
    if (url.pathname === '/api/management/room-types') return json(route, []);
    if (url.pathname === '/api/management/operational-exports') {
      requests.push(url);
      if (url.searchParams.get('propertyId') !== '11') return json(route, { message: 'Not found' }, 404);
      return route.fulfill({ status: 200, headers: {
        'content-type': 'text/csv', 'content-disposition': 'attachment; filename="customers-property-11.csv"',
        'x-export-schema': 'operational-customers-v1', 'x-export-row-count': '1', 'x-export-checksum': 'e'.repeat(64),
        'access-control-expose-headers': 'Content-Disposition, X-Export-Schema, X-Export-Row-Count, X-Export-Checksum',
      }, body: '"customerRef","maskedEmail","maskedPhone","accountStatus"\n"CUS-9","gu***@example.com","***4567","ACTIVE"\n' });
    }
    if (url.pathname === '/api/users/me') return json(route, { id: 7, username: 'owner', roles: ['PROPERTY_OWNER'] });
    if (url.pathname === '/api/auth/my-menu') return json(route, []);
    return json(route, []);
  });

  await page.goto('/management/rooms', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Tap du lieu').selectOption('CUSTOMERS');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Tai CSV' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('customers-property-11.csv');
  await expect(page.getByText(/operational-customers-v1/)).toBeVisible();
  await expect(page.getByText(/SHA-256/)).toBeVisible();
  expect(requests[0].searchParams.get('propertyId')).toBe('11');
  expect(requests[0].searchParams.get('dataset')).toBe('CUSTOMERS');
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T337-operational-export-pii-minimized.png', fullPage: true });
});
