import { expect, Page, test } from '@playwright/test';

test('shows immutable support lifecycle/read events and hides foreign conversation history', async ({ page }) => {
  const conversation = {
    conversationId: 339,
    customerId: 42,
    customerName: 'Minh Anh',
    subject: 'T339 audit conversation',
    hotelId: 5,
    hotelName: 'LuxeStay Sai Gon',
    status: 'OPEN',
    version: 8,
    lastMessage: 'Can kiem tra lich su ho tro',
    lastMessageAt: '2026-08-04T10:01:00Z',
  };
  const events = [
    event(93, 'MESSAGE_READ', 'Message 3391 state changed to READ', '2026-08-04T10:03:00Z'),
    event(92, 'REOPENED', 'Khach phan hoi them', '2026-08-04T10:02:00Z'),
    event(91, 'CLOSED', 'Da xu ly yeu cau', '2026-08-04T10:01:30Z'),
  ];

  await installSession(page);
  await page.route('**/api/chat/**', route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path === '/api/chat/support/conversations') {
      return route.fulfill({ status: 200, json: [conversation] });
    }
    if (path === '/api/chat/support/audit-policy') {
      return route.fulfill({
        status: 200,
        json: { appendOnly: true, retentionDays: 730, pageMaxRows: 100,
          events: ['CLOSED', 'REOPENED', 'MESSAGE_READ'] },
      });
    }
    if (path === '/api/chat/support/conversations/999/events') {
      return route.fulfill({ status: 404, json: { code: 'NOT_FOUND', message: 'Resource not found' } });
    }
    if (path === '/api/chat/support/conversations/339/events') {
      return route.fulfill({ status: 200, json: pageResult(events) });
    }
    if (path === '/api/chat/support/conversations/339/messages') {
      return route.fulfill({ status: 200, json: pageResult([{
        id: 3391,
        conversationId: 339,
        senderId: 42,
        receiverId: 7,
        content: 'Can kiem tra lich su ho tro',
        timestamp: '2026-08-04T10:01:00Z',
        deliveryStatus: 'READ',
        isRead: true,
      }]) });
    }
    if (path === '/api/chat/support/conversations/339/attachments') {
      return route.fulfill({ status: 200, json: [] });
    }
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
  });
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path.startsWith('/api/chat/')) return route.fallback();
    if (path === '/api/users/me') {
      return route.fulfill({ status: 200, json: {
        id: 7, username: 'audit-supervisor', fullName: 'Support Supervisor', roles: ['SUPER_ADMIN']
      } });
    }
    if (path === '/api/auth/my-menu') return route.fulfill({ status: 200, json: [] });
    return route.fulfill({ status: 200, json: route.request().method() === 'GET' ? [] : {} });
  });
  await page.route('**/ws-chat/**', route => route.abort());

  await page.goto('/admin/chat', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: /T339 audit conversation/ }).click();

  const audit = page.locator('.conversation-audit');
  await expect(audit).toContainText('Chi ghi them / luu 730 ngay');
  await expect(audit).toContainText('Da dong hoi thoai');
  await expect(audit).toContainText('Da mo lai hoi thoai');
  await expect(audit).toContainText('Tin nhan da doc');
  await expect(audit.locator('li')).toHaveCount(3);

  const foreignStatus = await page.evaluate(async () =>
    (await fetch('/api/chat/support/conversations/999/events')).status);
  expect(foreignStatus).toBe(404);

  await page.locator('.chat-dashboard').screenshot({
    path: '../docs/testing/evidence/007/remediation/T339-support-conversation-audit.png',
  });
});

function event(id: number, eventType: string, details: string, occurredAt: string) {
  return { id, conversationId: 339, hotelId: 5, actorUserId: 7, eventType, details, occurredAt };
}

function pageResult<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    number: 0,
    size: 20,
    first: true,
    last: true,
  };
}

async function installSession(page: Page) {
  const token = browserToken(Date.now() + 60_000);
  await page.addInitScript(accessToken => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({
      id: 7,
      username: 'audit-supervisor',
      fullName: 'Support Supervisor',
      roles: ['SUPER_ADMIN'],
      permissions: [{ function: 'AI_CHAT', actionMask: 3 }],
    }));
  }, token);
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1_000), sub: 't339-audit'
  })}.test-signature`;
}
