import { expect, test } from '@playwright/test';

test('reloads persisted unread notifications after the customer session recovers', async ({ page }) => {
  const token = browserToken(Date.now() + 60_000);
  await page.addInitScript(({ accessToken }) => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({
      id: 7,
      username: 'reconnect-customer',
      fullName: 'Reconnect Customer',
      roles: ['CUSTOMER'],
      permissions: [],
    }));
  }, { accessToken: token });

  await page.route('**/ws/**', route => route.abort());
  await page.route('**/api/users/me', route => route.fulfill({
    status: 200,
    json: {
      id: 7,
      username: 'reconnect-customer',
      fullName: 'Reconnect Customer',
      roles: ['CUSTOMER'],
    },
  }));

  let recovered = false;
  await page.route('**/api/customer/notifications**', route => {
    const url = new URL(route.request().url());
    const content = [notification(31, 'Booking confirmed', '/booking-history')];
    if (recovered) {
      content.unshift(notification(32, 'Refund completed', '/refunds'));
    }
    if (url.pathname.endsWith('/unread-count')) {
      return route.fulfill({ status: 200, json: { unreadCount: content.length } });
    }
    return route.fulfill({
      status: 200,
      json: {
        content,
        totalElements: content.length,
        totalPages: 1,
        number: 0,
        size: 20,
        first: true,
        last: true,
        unreadCount: content.length,
      },
    });
  });

  await page.goto('/notifications', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Booking confirmed', exact: true })).toBeVisible();
  await expect(page.getByText('1 chua doc')).toBeVisible();

  recovered = true;
  await page.reload({ waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('heading', { name: 'Refund completed', exact: true })).toBeVisible();
  await expect(page.getByText('2 chua doc')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Xem chi tiet' }).first())
    .toHaveAttribute('href', '/refunds');
});

function notification(id: number, title: string, deepLink: string) {
  return {
    id,
    type: deepLink === '/refunds' ? 'REFUND' : 'BOOKING',
    title,
    message: `${title} message`,
    isRead: false,
    createdAt: '2026-08-04T10:00:00',
    deepLink,
  };
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ exp: Math.floor(expiresAt / 1_000), sub: 'reconnect-customer' })}.test-signature`;
}
