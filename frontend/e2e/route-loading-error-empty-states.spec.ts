import { expect, Page, test } from '@playwright/test';

test('renders accessible loading outcomes, retry recovery and empty guidance on remediated admin routes', async ({ page }) => {
  let claimAttempts = 0;
  await installSession(page);
  await page.route('**/api/**', route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === '/api/users/me') {
      return route.fulfill({ status: 200, json: {
        id: 7, username: 'route-state-admin', fullName: 'Route State Admin', roles: ['SUPER_ADMIN']
      } });
    }
    if (path === '/api/auth/my-menu') return route.fulfill({ status: 200, json: [] });
    if (path === '/api/admin/property-claims') {
      claimAttempts += 1;
      return claimAttempts === 1
        ? route.fulfill({ status: 503, json: { message: 'Claim queue temporarily unavailable' } })
        : route.fulfill({ status: 200, json: { content: [] } });
    }
    if (path === '/api/admin/property-imports') {
      return route.fulfill({ status: 200, json: { content: [] } });
    }
    if (path === '/api/subscriptions/plans') {
      return route.fulfill({ status: 503, json: { message: 'Catalog temporarily unavailable' } });
    }
    if (path === '/api/subscriptions/me') {
      return route.fulfill({ status: 503, json: { message: 'Assignments temporarily unavailable' } });
    }
    if (path === '/api/reservations') {
      return route.fulfill({ status: 503, json: { message: 'Reservation ledger temporarily unavailable' } });
    }
    if (path.endsWith('/notifications/unread-count')) {
      return route.fulfill({ status: 200, json: { unreadCount: 0 } });
    }
    return route.fulfill({ status: 200, json: request.method() === 'GET' ? [] : {} });
  });

  await page.goto('/admin/property-claims', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Claim queue temporarily unavailable')).toBeVisible();
  const retry = page.getByRole('button', { name: 'Retry' });
  await expect(retry).toBeVisible();
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T340-property-claims-retry.png' });
  await retry.click();
  await expect(page.getByText('No property claims')).toBeVisible();
  expect(claimAttempts).toBe(2);

  await page.goto('/admin/property-imports', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('No import batches')).toBeVisible();
  await expect(page.getByText('Search a provider to stage the first review batch.')).toBeVisible();

  await page.goto('/admin/plans', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('.feedback-state__message').filter({ hasText: 'Catalog temporarily unavailable' }))
    .toBeVisible();
  await expect(page.locator('.feedback-state__message').filter({ hasText: 'Assignments temporarily unavailable' }))
    .toBeVisible();
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T340-plan-route-errors.png' });

  await page.goto('/admin/invoices', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Reservation ledger temporarily unavailable')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Retry' })).toBeVisible();
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T340-invoice-route-error.png' });
});

async function installSession(page: Page) {
  const token = browserToken(Date.now() + 60_000);
  await page.addInitScript(accessToken => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({
      id: 7,
      username: 'route-state-admin',
      fullName: 'Route State Admin',
      roles: ['SUPER_ADMIN'],
      permissions: [],
    }));
  }, token);
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1_000), sub: 't340-route-state'
  })}.test-signature`;
}
