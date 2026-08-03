import { expect, test } from '@playwright/test';

test.describe('customer notification inbox', () => {
  test.beforeEach(async ({ page }) => {
    const token = browserToken(Date.now() + 60_000);
    await page.addInitScript(({ accessToken }) => {
      localStorage.setItem('token', accessToken);
      localStorage.setItem('user', JSON.stringify({
        id: 7,
        username: 'notification-customer',
        fullName: 'Notification Customer',
        roles: ['CUSTOMER'],
        permissions: [],
      }));
    }, { accessToken: token });

    await page.route('**/ws/**', route => route.abort());
    await page.route('**/api/users/me', route => route.fulfill({
      status: 200,
      json: {
        id: 7,
        username: 'notification-customer',
        email: 'notification@example.com',
        fullName: 'Notification Customer',
        roles: ['CUSTOMER'],
      },
    }));

    let unreadCount = 1;
    const notifications = [
      {
        id: 31,
        type: 'BOOKING',
        title: 'Booking confirmed',
        message: 'Your booking is ready.',
        isRead: false,
        createdAt: '2026-08-04T10:00:00',
        deepLink: '/booking-history',
      },
      {
        id: 30,
        type: 'INVOICE',
        title: 'Invoice available',
        message: 'Your invoice is ready.',
        isRead: true,
        createdAt: '2026-08-03T10:00:00',
        deepLink: '/my-invoices',
      },
    ];

    await page.route('**/api/customer/notifications**', route => {
      const url = new URL(route.request().url());
      if (url.pathname.endsWith('/unread-count')) {
        return route.fulfill({ status: 200, json: { unreadCount } });
      }
      if (url.pathname.endsWith('/31/read')) {
        unreadCount = 0;
        return route.fulfill({
          status: 200,
          json: { ...notifications[0], isRead: true },
        });
      }
      return route.fulfill({
        status: 200,
        json: {
          content: notifications,
          totalElements: notifications.length,
          totalPages: 1,
          number: 0,
          size: 20,
          first: true,
          last: true,
          unreadCount,
        },
      });
    });
  });

  test('shows unread count, own rows and actionable deep links', async ({ page }) => {
    await page.goto('/notifications', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('heading', { name: 'Thong bao cua ban' })).toBeVisible();
    await expect(page.getByText('Booking confirmed')).toBeVisible();
    await expect(page.getByText('Invoice available')).toBeVisible();
    await expect(page.getByText('1 chua doc')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Xem chi tiet' }).first())
      .toHaveAttribute('href', '/booking-history');

    await page.getByRole('button', { name: 'Danh dau da doc: Booking confirmed' }).click();
    await expect(page.getByText('0 chua doc')).toBeVisible();
  });
});

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ exp: Math.floor(expiresAt / 1_000), sub: 'notification-customer' })}.test-signature`;
}
