import { expect, Page, Route, test } from '@playwright/test';

interface Conversation {
  conversationId: number;
  customerId: number;
  customerName: string;
  subject: string;
  hotelId: number;
  hotelName: string;
  status: 'OPEN' | 'CLOSED';
  version: number;
  slaDeadlineAt?: string;
  slaState: string;
  createdAt: string;
  lastActivityAt: string;
  closedAt?: string;
  closedReason?: string;
  reopenedAt?: string;
  reopenReason?: string;
  lastMessage: string;
  lastMessageAt: string;
}

test('searches, attaches, closes and reopens a support conversation with visible evidence', async ({ page }) => {
  const state = await installApi(page);
  await installAdminSession(page);
  await page.goto('/admin/chat', { waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('button', { name: /Hoa don can doi chieu/ })).toBeVisible();
  await page.getByLabel('Tim hoi thoai').fill('doi chieu');
  await page.getByLabel('Tim hoi thoai').press('Enter');
  await expect.poll(() => state.lastQuery).toBe('doi chieu');

  await page.getByRole('button', { name: /Hoa don can doi chieu/ }).click();
  await expect(page.getByText('Han SLA:')).toBeVisible();
  await page.locator('input[type=file]').setInputFiles({
    name: 'doi-chieu.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('T328 safe attachment'),
  });
  await expect(page.getByRole('button', { name: /doi-chieu.txt/ })).toBeVisible();

  await page.getByPlaceholder('Nhap ly do dong / mo lai').fill('Da doi chieu xong');
  await page.getByRole('button', { name: 'Dong hoi thoai' }).click();
  await expect(page.locator('.conversation-status')).toContainText('Da dong');
  await expect.poll(() => state.conversation.closedReason).toBe('Da doi chieu xong');
  await capture(page, 'T328-reasoned-close-attachment.png');

  await page.getByPlaceholder('Nhap ly do dong / mo lai').fill('Khach gui them chung tu');
  await page.getByRole('button', { name: 'Mo lai' }).click();
  await expect(page.locator('.conversation-status')).toContainText('Dang cho');
  await expect.poll(() => state.conversation.reopenReason).toBe('Khach gui them chung tu');
  await capture(page, 'T328-reopened-sla-search.png');
});

async function installAdminSession(page: Page) {
  const token = browserToken(Date.now() + 60_000);
  await page.addInitScript(accessToken => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify({
      id: 7,
      username: 'support',
      fullName: 'Support Agent',
      roles: ['SUPER_ADMIN'],
      permissions: [{ function: 'AI_CHAT', actionMask: 3 }],
    }));
  }, token);
}

async function installApi(page: Page) {
  const now = '2026-08-04T06:30:00Z';
  const state: { conversation: Conversation; lastQuery: string; attachments: unknown[] } = {
    conversation: {
      conversationId: 328,
      customerId: 42,
      customerName: 'Nguyen Minh Anh',
      subject: 'Hoa don can doi chieu',
      hotelId: 5,
      hotelName: 'LuxeStay Sai Gon',
      status: 'OPEN',
      version: 4,
      slaDeadlineAt: '2026-08-04T07:00:00Z',
      slaState: 'ON_TRACK',
      createdAt: now,
      lastActivityAt: now,
      lastMessage: 'Can doi chieu tong tien',
      lastMessageAt: now,
    },
    lastQuery: '',
    attachments: [],
  };

  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    if (path === '/api/users/me') {
      return json(route, {
        id: 7, username: 'support', fullName: 'Support Agent', roles: ['SUPER_ADMIN'],
      });
    }
    if (path.endsWith('/customer/notifications/unread-count')) {
      return json(route, { unreadCount: 0 });
    }
    if (path.startsWith('/api/notifications')) return json(route, []);
    if (path === '/api/chat/support/conversations' && request.method() === 'GET') {
      state.lastQuery = url.searchParams.get('query') || '';
      return json(route, [state.conversation]);
    }
    if (path === '/api/chat/support/conversations/328/messages') {
      return json(route, {
        content: [{
          id: 1, conversationId: 328, senderId: 42, receiverId: 0,
          content: 'Can doi chieu tong tien', timestamp: now, deliveryStatus: 'READ',
        }],
        totalElements: 1, totalPages: 1, number: 0, size: 50,
        first: true, last: true, retentionDays: 365,
      });
    }
    if (path === '/api/chat/support/conversations/328/attachments' && request.method() === 'GET') {
      return json(route, state.attachments);
    }
    if (path === '/api/chat/support/conversations/328/attachments' && request.method() === 'POST') {
      const attachment = {
        id: 81, conversationId: 328, filename: 'doi-chieu.txt', contentType: 'text/plain',
        sizeBytes: 20, checksumSha256: 'a'.repeat(64), uploadedByUserId: 7, uploadedAt: now,
      };
      state.attachments.push(attachment);
      return json(route, attachment);
    }
    if (path === '/api/chat/support/conversations/328/close') {
      const body = request.postDataJSON() as { reason: string };
      state.conversation = {
        ...state.conversation,
        status: 'CLOSED', version: 5, slaDeadlineAt: undefined,
        closedAt: now, closedReason: body.reason,
      };
      return json(route, state.conversation);
    }
    if (path === '/api/chat/support/conversations/328/reopen') {
      const body = request.postDataJSON() as { reason: string };
      state.conversation = {
        ...state.conversation,
        status: 'OPEN', version: 6, slaDeadlineAt: '2026-08-04T07:30:00Z',
        reopenedAt: now, reopenReason: body.reason,
      };
      return json(route, state.conversation);
    }
    if (path === '/api/chat/messages/1/state') {
      return json(route, {
        id: 1, conversationId: 328, senderId: 42, receiverId: 0,
        content: 'Can doi chieu tong tien', timestamp: now, deliveryStatus: 'READ',
      });
    }
    if (path === '/api/public/properties/search') {
      return json(route, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 });
    }
    return json(route, {});
  });
  return state;
}

async function json(route: Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

async function capture(page: Page, filename: string) {
  if (process.env['CAPTURE_T328_EVIDENCE'] !== '1') return;
  await page.locator('.chat-dashboard').screenshot({
    path: `../docs/testing/evidence/007/remediation/assets/${filename}`,
  });
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1_000), sub: 't328-support'
  })}.test-signature`;
}
