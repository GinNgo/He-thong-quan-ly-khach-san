import { expect, Page, test } from '@playwright/test';

type DeliveryStatus = 'PERSISTED' | 'DELIVERED' | 'READ';

interface Conversation {
  conversationId: number;
  customerId: number;
  customerName: string;
  subject: string;
  status: 'OPEN';
  version: number;
  lastMessage: string;
  lastMessageAt: string;
}

interface Message {
  id: number;
  conversationId: number;
  clientMessageId: string;
  senderId: number;
  receiverId: number;
  content: string;
  timestamp: string;
  isRead: boolean;
  deliveryStatus: DeliveryStatus;
  deliveredAt?: string;
  readAt?: string;
}

test('retries one persisted message id and renders sent, delivered and read states for both roles', async ({ browser }) => {
  test.setTimeout(90_000);

  const conversation: Conversation = {
    conversationId: 327,
    customerId: 42,
    customerName: 'Minh Anh',
    subject: 'T327 duplicate-safe delivery',
    status: 'OPEN',
    version: 1,
    lastMessage: '',
    lastMessageAt: '2026-08-04T08:00:00Z',
  };
  const messages: Message[] = [];
  const customerAttempts: Array<{ clientMessageId: string; serverId: number }> = [];
  const failedClientIds = new Set<string>();
  let nextMessageId = 3270;

  const customerContext = await browser.newContext();
  const supportContext = await browser.newContext();
  const customerPage = await customerContext.newPage();
  const supportPage = await supportContext.newPage();

  await installSession(customerPage, {
    id: 42, username: 't327-customer', fullName: 'Minh Anh', roles: ['CUSTOMER'], permissions: [],
  });
  await installSession(supportPage, {
    id: 7, username: 't327-support', fullName: 'Support Agent', roles: ['SUPER_ADMIN'],
    permissions: [{ function: 'AI_CHAT', actionMask: 3 }],
  });
  await mockApplicationApis(
    customerPage, 'customer', conversation, messages, customerAttempts, failedClientIds, () => nextMessageId++);
  await mockApplicationApis(
    supportPage, 'support', conversation, messages, customerAttempts, failedClientIds, () => nextMessageId++);

  const customerText = 'T327 retry sau timeout van chi co mot tin nhan.';
  await customerPage.goto('/', { waitUntil: 'domcontentloaded' });
  await customerPage.getByRole('button', { name: 'Mo ho tro truc tuyen LuxeStay' }).click();
  await customerPage.getByRole('textbox', { name: 'Noi dung tin nhan ho tro' }).fill(customerText);
  await customerPage.getByRole('button', { name: 'Gui tin nhan ho tro' }).click();
  await expect(customerPage.locator('#support-send-status')).toContainText('Khong the gui tin nhan');

  await customerPage.getByRole('button', { name: 'Gui tin nhan ho tro' }).click();
  const customerRow = customerPage.locator('.message-row').filter({ hasText: customerText });
  await expect(customerRow).toBeVisible();
  await expect(customerRow.locator('.message-state')).toHaveText('Da gui');

  expect(customerAttempts).toHaveLength(2);
  expect(customerAttempts[0].clientMessageId).toBe(customerAttempts[1].clientMessageId);
  expect(customerAttempts[0].serverId).toBe(customerAttempts[1].serverId);
  expect(messages.filter(message => message.clientMessageId === customerAttempts[0].clientMessageId)).toHaveLength(1);
  await capture(customerPage, '#support-chat-panel', 'T327-customer-sent.png');

  const persisted = messages.find(message => message.id === customerAttempts[0].serverId)!;
  advanceState(persisted, 'DELIVERED');
  await reopenCustomerChat(customerPage);
  const deliveredRow = customerPage.locator('.message-row').filter({ hasText: customerText });
  await expect(deliveredRow.locator('.message-state')).toHaveText('Da nhan');
  await capture(customerPage, '#support-chat-panel', 'T327-customer-delivered.png');

  await supportPage.goto('/admin/chat', { waitUntil: 'domcontentloaded' });
  await supportPage.getByRole('button', { name: /T327 duplicate-safe delivery/ }).click();
  await expect(supportPage.getByText(customerText, { exact: true })).toBeVisible();
  await expect.poll(() => persisted.deliveryStatus).toBe('READ');
  await capture(supportPage, '.chat-dashboard', 'T327-support-read-customer-message.png');

  await reopenCustomerChat(customerPage);
  const readCustomerRow = customerPage.locator('.message-row').filter({ hasText: customerText });
  await expect(readCustomerRow.locator('.message-state')).toHaveText('Da doc');
  await capture(customerPage, '#support-chat-panel', 'T327-customer-read.png');

  const supportText = 'T327 phan hoi da duoc khach hang doc.';
  await supportPage.getByRole('textbox', { name: 'Phan hoi khach hang' }).fill(supportText);
  await supportPage.getByRole('button', { name: 'Gui phan hoi' }).click();
  const supportRow = supportPage.locator('.message-row').filter({ hasText: supportText });
  await expect(supportRow.locator('.message-state')).toHaveText('Da gui');

  await reopenCustomerChat(customerPage);
  await expect(customerPage.getByText(supportText, { exact: true })).toBeVisible();
  const reply = messages.find(message => message.content === supportText)!;
  await expect.poll(() => reply.deliveryStatus).toBe('READ');

  await supportPage.reload({ waitUntil: 'domcontentloaded' });
  await supportPage.getByRole('button', { name: /T327 duplicate-safe delivery/ }).click();
  const readSupportRow = supportPage.locator('.message-row').filter({ hasText: supportText });
  await expect(readSupportRow.locator('.message-state')).toHaveText('Da doc');
  await capture(supportPage, '.chat-dashboard', 'T327-support-read.png');

  await customerContext.close();
  await supportContext.close();
});

async function installSession(page: Page, user: Record<string, unknown>) {
  const token = browserToken(Date.now() + 60_000);
  await page.addInitScript(({ accessToken, sessionUser }) => {
    localStorage.setItem('token', accessToken);
    localStorage.setItem('user', JSON.stringify(sessionUser));
  }, { accessToken: token, sessionUser: user });
}

async function mockApplicationApis(
  page: Page,
  role: 'customer' | 'support',
  conversation: Conversation,
  messages: Message[],
  customerAttempts: Array<{ clientMessageId: string; serverId: number }>,
  failedClientIds: Set<string>,
  nextMessageId: () => number,
) {
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') {
      return route.fulfill({ status: 200, json: role === 'customer'
        ? { id: 42, username: 't327-customer', fullName: 'Minh Anh', roles: ['CUSTOMER'] }
        : { id: 7, username: 't327-support', fullName: 'Support Agent', roles: ['SUPER_ADMIN'] } });
    }
    if (path === '/api/public/properties/search') {
      return route.fulfill({ status: 200, json: pageResult([]) });
    }
    if (path.endsWith('/customer/notifications/unread-count')) {
      return route.fulfill({ status: 200, json: { unreadCount: 0 } });
    }
    if (path.startsWith('/api/notifications')) return route.fulfill({ status: 200, json: [] });
    return route.fulfill({ status: 200, json: route.request().method() === 'GET' ? [] : {} });
  });

  await page.route('**/api/chat/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const conversationMessages = path.match(/\/conversations\/(\d+)\/messages$/);
    const acknowledgement = path.match(/\/messages\/(\d+)\/state$/);

    if (path === '/api/chat/me/conversations' && request.method() === 'GET') {
      return route.fulfill({ status: 200, json: pageResult([conversation]) });
    }
    if (path === '/api/chat/support/conversations' && request.method() === 'GET') {
      return route.fulfill({ status: 200, json: [conversation] });
    }
    if (acknowledgement && request.method() === 'POST') {
      const message = messages.find(item => item.id === Number(acknowledgement[1]));
      if (!message) return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
      advanceState(message, url.searchParams.get('state') === 'READ' ? 'READ' : 'DELIVERED');
      return route.fulfill({ status: 200, json: message });
    }
    if (conversationMessages && request.method() === 'GET') {
      return route.fulfill({ status: 200, json: pageResult(messages) });
    }
    if (conversationMessages && request.method() === 'POST') {
      const body = request.postDataJSON() as { content: string; clientMessageId: string };
      const senderId = role === 'customer' ? 42 : 7;
      let message = messages.find(item => item.senderId === senderId
        && item.clientMessageId === body.clientMessageId);
      if (!message) {
        message = {
          id: nextMessageId(),
          conversationId: conversation.conversationId,
          clientMessageId: body.clientMessageId,
          senderId,
          receiverId: role === 'customer' ? 0 : 42,
          content: body.content,
          timestamp: new Date().toISOString(),
          isRead: false,
          deliveryStatus: 'PERSISTED',
        };
        messages.push(message);
        conversation.lastMessage = message.content;
        conversation.lastMessageAt = message.timestamp;
      }
      if (role === 'customer') {
        customerAttempts.push({ clientMessageId: body.clientMessageId, serverId: message.id });
        if (!failedClientIds.has(body.clientMessageId)) {
          failedClientIds.add(body.clientMessageId);
          return route.fulfill({ status: 504, json: { code: 'ACK_TIMEOUT', retryable: true } });
        }
      }
      return route.fulfill({ status: 200, json: message });
    }
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
  });

  await page.route('**/ws-chat/**', route => route.abort());
}

function advanceState(message: Message, requested: 'DELIVERED' | 'READ') {
  const now = new Date().toISOString();
  if (requested === 'READ') {
    message.deliveryStatus = 'READ';
    message.deliveredAt ??= now;
    message.readAt ??= now;
    message.isRead = true;
  } else if (message.deliveryStatus === 'PERSISTED') {
    message.deliveryStatus = 'DELIVERED';
    message.deliveredAt = now;
  }
}

async function reopenCustomerChat(page: Page) {
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Mo ho tro truc tuyen LuxeStay' }).click();
  await expect(page.getByLabel('Cuoc tro chuyen').locator('option:checked'))
    .toHaveText('T327 duplicate-safe delivery');
}

function pageResult<T>(content: T[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    number: 0,
    size: 50,
    first: true,
    last: true,
    retentionDays: 365,
  };
}

function browserToken(expiresAt: number): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString('base64url');
  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1_000), sub: 'chat-e2e'
  })}.test-signature`;
}

async function capture(page: Page, selector: string, filename: string) {
  if (process.env['CAPTURE_T327_EVIDENCE'] !== '1') return;
  await page.locator(selector).screenshot({
    path: `../docs/testing/evidence/007/remediation/assets/${filename}`,
  });
}
