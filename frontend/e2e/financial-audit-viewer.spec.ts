import { expect, Page, Route, test } from '@playwright/test';

async function seed(page: Page): Promise<void> {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  const token = `${encode({ alg: 'HS256' })}.${encode({ exp: Math.floor(Date.now() / 1000) + 3600 })}.test`;
  await page.addInitScript(accessToken => { const rewrite = (source: string) => source.startsWith('http://localhost:8080/api') ? source.replace('http://localhost:8080/api', '/api') : source; const open = XMLHttpRequest.prototype.open; XMLHttpRequest.prototype.open = function(method: string, url: string | URL, async = true, username?: string | null, password?: string | null) { return open.call(this, method, rewrite(url.toString()), async, username, password); }; localStorage.setItem('token', accessToken); localStorage.setItem('user', JSON.stringify({ id: 7, username: 'owner', roles: ['PROPERTY_OWNER'], permissions: [{ function: 'AUDIT_LOG', actionMask: 17 }] })); }, token);
}
async function json(route: Route, body: unknown, status = 200): Promise<void> { await route.fulfill({ status, json: body }); }

test('tenant viewer sees only redacted property financial events and cannot enumerate foreign scope', async ({ page }) => {
  await seed(page); const auditRequests: URL[] = [];
  await page.route('**/api/**', async route => { const url = new URL(route.request().url());
    if (url.pathname === '/api/admin/financial-audit-events/policy') return json(route, { appendOnly: true, retentionDays: 2555, exportMaxRows: 10000, redactionPolicy: 'REDACT_SECRETS_AND_PII_HASH_EXTERNAL_IDENTITIES' });
    if (url.pathname === '/api/admin/financial-audit-events') { auditRequests.push(url); if (url.searchParams.get('hotelId') === '99' || url.searchParams.get('context') === 'PLATFORM_BILLING') return json(route, { message: 'Financial audit scope not found.' }, 404); return json(route, { content: [{ id: 1, context: 'PROPERTY_COMMERCE', hotelId: 11, aggregateType: 'PAYMENT', aggregateId: 'PAY-1', actorType: 'USER', actorId: 7, source: 'CALLBACK', previousState: 'PENDING', newState: 'PAID', providerReference: 'sha256:1234abcd', idempotencyReference: 'sha256:5678abcd', correlationId: 'corr-1', metadataJson: '{"email":"[REDACTED]","safe":"ok"}', occurredAt: '2026-08-04T10:00:00Z' }], totalElements: 1, totalPages: 1, number: 0, size: 25 }); }
    if (url.pathname === '/api/management/context') return json(route, { properties: [{ id: 11, nameVi: 'Da Nang' }], activePropertyId: 11 });
    if (url.pathname === '/api/users/me') return json(route, { id: 7, username: 'owner', roles: ['PROPERTY_OWNER'] }); if (url.pathname === '/api/auth/my-menu') return json(route, []); return json(route, []);
  });
  await page.goto('/management/financial-audit', { waitUntil: 'domcontentloaded' });
  await expect(page.locator('.financial-audit').getByRole('heading', { name: 'Nhat ky tai chinh' })).toBeVisible(); await expect(page.getByText('PAYMENT #PAY-1')).toBeVisible(); await expect(page.getByText(/2555 ngay/)).toBeVisible();
  await page.getByRole('button', { name: 'Chi tiet' }).click(); await expect(page.getByText('sha256:1234abcd')).toBeVisible(); await expect(page.getByText(/REDACTED/)).toBeVisible(); await expect(page.locator('select[name="context"]')).toHaveCount(0);
  expect(auditRequests.every(url => !url.searchParams.has('hotelId') && !url.searchParams.has('context'))).toBe(true);
  await page.screenshot({ path: '../docs/testing/evidence/007/remediation/T338-financial-audit-redacted-viewer.png', fullPage: true });
});
