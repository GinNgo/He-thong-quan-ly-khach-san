import { expect, test } from '@playwright/test';

test('keeps mandatory events, saves channel choices and moves own rows through archive history', async ({ page }) => {
  const token = browserToken(Date.now() + 60_000);
  await page.addInitScript(({ accessToken }) => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({
      id: 7,
      username: 'preference-customer',
      fullName: 'Preference Customer',
      roles: ['CUSTOMER'],
      permissions: [],
    }));
  }, { accessToken: token });

  await page.route('**/ws/**', route => route.abort());
  await page.route('**/api/users/me', route => route.fulfill({
    status: 200,
    json: { id: 7, username: 'preference-customer', fullName: 'Preference Customer', roles: ['CUSTOMER'] },
  }));

  let archived = false;
  let marketingEmail = false;
  const activeNotification = {
    id: 31,
    type: 'BOOKING',
    title: 'Booking confirmed',
    message: 'Your booking is ready.',
    isRead: false,
    createdAt: '2026-08-04T10:00:00',
    archivedAt: null,
    deepLink: '/booking-history',
  };

  await page.route('**/api/customer/notifications**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (url.pathname.endsWith('/preferences')) {
      if (request.method() === 'PUT') {
        const body = request.postDataJSON() as { preferences: Array<{ eventClass: string; channel: string; enabled: boolean }> };
        marketingEmail = body.preferences.some(item =>
          item.eventClass === 'MARKETING' && item.channel === 'EMAIL' && item.enabled);
      }
      return route.fulfill({ status: 200, json: preferences(marketingEmail) });
    }
    if (url.pathname.endsWith('/31/archive')) {
      archived = true;
      return route.fulfill({ status: 200, json: { ...activeNotification, archivedAt: '2026-08-04T11:00:00' } });
    }
    if (url.pathname.endsWith('/31/restore')) {
      archived = false;
      return route.fulfill({ status: 200, json: activeNotification });
    }
    if (url.pathname.endsWith('/unread-count')) {
      return route.fulfill({ status: 200, json: { unreadCount: archived ? 0 : 1 } });
    }
    const archivedView = url.searchParams.get('archived') === 'true';
    const visible = archivedView === archived;
    return route.fulfill({
      status: 200,
      json: {
        content: visible ? [{ ...activeNotification, archivedAt: archived ? '2026-08-04T11:00:00' : null }] : [],
        totalElements: visible ? 1 : 0,
        totalPages: visible ? 1 : 0,
        number: 0,
        size: 20,
        first: true,
        last: true,
        unreadCount: archived ? 0 : 1,
        archived: archivedView,
        retentionDays: 365,
      },
    });
  });

  await page.goto('/notifications', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Tuy chon' }).click();

  const bookingGroup = page.locator('fieldset').filter({ hasText: 'Dat phong' });
  await expect(bookingGroup.getByRole('checkbox', { name: 'Trong ung dung' })).toBeChecked();
  await expect(bookingGroup.getByRole('checkbox', { name: 'Trong ung dung' })).toBeDisabled();

  const marketingGroup = page.locator('fieldset').filter({ hasText: 'Uu dai va tin tuc' });
  await marketingGroup.getByRole('checkbox', { name: 'Email' }).check();
  await page.getByRole('button', { name: 'Luu tuy chon' }).click();
  await expect(page.getByText('Da luu tuy chon thong bao.')).toBeVisible();
  expect(marketingEmail).toBe(true);

  await page.getByRole('button', { name: 'Luu tru: Booking confirmed' }).click();
  await expect(page.getByText('Chua co thong bao')).toBeVisible();
  await page.getByRole('button', { name: 'Da luu tru' }).click();
  await expect(page.getByRole('heading', { name: 'Booking confirmed', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Khoi phuc: Booking confirmed' }).click();
  await expect(page.getByText('Chua co thong bao luu tru')).toBeVisible();
});

function preferences(marketingEmail: boolean) {
  return [{
    eventClass: 'BOOKING',
    label: 'Dat phong',
    mandatory: true,
    channels: [
      { channel: 'IN_APP', enabled: true, locked: true },
      { channel: 'EMAIL', enabled: true, locked: false },
    ],
  }, {
    eventClass: 'MARKETING',
    label: 'Uu dai va tin tuc',
    mandatory: false,
    channels: [
      { channel: 'IN_APP', enabled: false, locked: false },
      { channel: 'EMAIL', enabled: marketingEmail, locked: false },
    ],
  }];
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ exp: Math.floor(expiresAt / 1_000), sub: 'preference-customer' })}.test-signature`;
}
