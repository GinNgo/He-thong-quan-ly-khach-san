import { expect, Page, test } from '@playwright/test';

interface Conversation {
  conversationId: number;
  customerId: number;
  customerName: string;
  subject: string;
  lastMessage: string;
  lastMessageAt: string;
}

interface Message {
  id: number;
  conversationId: number;
  senderId: number;
  receiverId: number;
  content: string;
  timestamp: string;
  isRead: boolean;
}

test('runs a two-context customer and support conversation journey', async ({ browser }) => {
  let nextConversationId = 100;
  let nextMessageId = 1000;
  const conversations: Conversation[] = [{
    conversationId: 11,
    customerId: 42,
    customerName: 'Minh Anh',
    subject: 'Dat phong truoc do',
    lastMessage: 'Da hoan tat',
    lastMessageAt: '2026-08-04T08:00:00Z',
  }];
  const messages = new Map<number, Message[]>([[11, [{
    id: 10,
    conversationId: 11,
    senderId: 42,
    receiverId: 0,
    content: 'Da hoan tat',
    timestamp: '2026-08-04T08:00:00Z',
    isRead: true,
  }]]]);

  const customerContext = await browser.newContext();
  const supportContext = await browser.newContext();
  const customerPage = await customerContext.newPage();
  const supportPage = await supportContext.newPage();

  await installSession(customerPage, {
    id: 42,
    username: 'customer-chat',
    fullName: 'Minh Anh',
    roles: ['CUSTOMER'],
    permissions: [],
  });
  await installSession(supportPage, {
    id: 7,
    username: 'support-chat',
    fullName: 'Support Agent',
    roles: ['SUPER_ADMIN'],
    permissions: [{ function: 'AI_CHAT', actionMask: 3 }],
  });

  await mockApplicationApis(customerPage, 'customer', conversations, messages, () => nextConversationId++,
    () => nextMessageId++);
  await mockApplicationApis(supportPage, 'support', conversations, messages, () => nextConversationId++,
    () => nextMessageId++);

  await customerPage.goto('/', { waitUntil: 'domcontentloaded' });
  await customerPage.getByRole('button', { name: 'Mo ho tro truc tuyen LuxeStay' }).click();
  await customerPage.getByRole('textbox', { name: 'Chu de hoi thoai moi' }).fill('Hoa don phong 402');
  await customerPage.getByRole('button', { name: 'Tao moi' }).click();
  await expect(customerPage.getByLabel('Cuoc tro chuyen').locator('option:checked'))
    .toHaveText('Hoa don phong 402');

  await customerPage.getByRole('textbox', { name: 'Noi dung tin nhan ho tro' })
    .fill('Toi can kiem tra hoa don phong 402.');
  await customerPage.getByRole('button', { name: 'Gui tin nhan ho tro' }).click();
  await expect(customerPage.getByText('Toi can kiem tra hoa don phong 402.', { exact: true })).toBeVisible();

  await supportPage.goto('/admin/chat', { waitUntil: 'domcontentloaded' });
  await supportPage.getByRole('button', { name: /Hoa don phong 402/ }).click();
  await expect(supportPage.getByText('Toi can kiem tra hoa don phong 402.', { exact: true })).toBeVisible();
  await supportPage.getByRole('textbox', { name: 'Phan hoi khach hang' })
    .fill('Hoa don da duoc doi chieu va gui lai.');
  await supportPage.getByRole('button', { name: 'Gui phan hoi' }).click();
  await expect(supportPage.getByText('Hoa don da duoc doi chieu va gui lai.', { exact: true })).toBeVisible();

  await customerPage.reload({ waitUntil: 'domcontentloaded' });
  await customerPage.getByRole('button', { name: 'Mo ho tro truc tuyen LuxeStay' }).click();
  await expect(customerPage.getByLabel('Cuoc tro chuyen').locator('option:checked'))
    .toHaveText('Hoa don phong 402');
  await expect(customerPage.getByText('Hoa don da duoc doi chieu va gui lai.', { exact: true })).toBeVisible();

  if (process.env['CAPTURE_T325_EVIDENCE'] === '1') {
    await customerPage.waitForTimeout(300);
    await customerPage.locator('#support-chat-panel').screenshot({
      path: '../docs/testing/evidence/007/remediation/assets/T325-customer-conversation.png',
    });
    await supportPage.locator('.chat-dashboard').screenshot({
      path: '../docs/testing/evidence/007/remediation/assets/T325-support-conversation.png',
    });
  }

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
  conversations: Conversation[],
  messages: Map<number, Message[]>,
  nextConversationId: () => number,
  nextMessageId: () => number,
) {
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/users/me') {
      return route.fulfill({ status: 200, json: role === 'customer'
        ? { id: 42, username: 'customer-chat', fullName: 'Minh Anh', roles: ['CUSTOMER'] }
        : { id: 7, username: 'support-chat', fullName: 'Support Agent', roles: ['SUPER_ADMIN'] } });
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
    const conversationMatch = path.match(/\/conversations\/(\d+)\/messages$/);

    if (path === '/api/chat/me/conversations' && request.method() === 'GET') {
      return route.fulfill({ status: 200, json: pageResult(conversations) });
    }
    if (path === '/api/chat/me/conversations' && request.method() === 'POST') {
      const body = request.postDataJSON() as { subject: string };
      const conversation: Conversation = {
        conversationId: nextConversationId(),
        customerId: 42,
        customerName: 'Minh Anh',
        subject: body.subject,
        lastMessage: '',
        lastMessageAt: new Date().toISOString(),
      };
      conversations.unshift(conversation);
      messages.set(conversation.conversationId, []);
      return route.fulfill({ status: 200, json: conversation });
    }
    if (path === '/api/chat/support/conversations' && request.method() === 'GET') {
      return route.fulfill({ status: 200, json: conversations });
    }
    if (conversationMatch && request.method() === 'GET') {
      const conversationId = Number(conversationMatch[1]);
      return route.fulfill({ status: 200, json: pageResult(messages.get(conversationId) ?? []) });
    }
    if (conversationMatch && request.method() === 'POST') {
      const conversationId = Number(conversationMatch[1]);
      const body = request.postDataJSON() as { content: string };
      const message: Message = {
        id: nextMessageId(),
        conversationId,
        senderId: role === 'customer' ? 42 : 7,
        receiverId: role === 'customer' ? 0 : 42,
        content: body.content,
        timestamp: new Date().toISOString(),
        isRead: false,
      };
      messages.get(conversationId)?.push(message);
      const conversation = conversations.find(item => item.conversationId === conversationId);
      if (conversation) {
        conversation.lastMessage = message.content;
        conversation.lastMessageAt = message.timestamp;
      }
      return route.fulfill({ status: 200, json: message });
    }
    return route.fulfill({ status: 404, json: { code: 'NOT_FOUND' } });
  });

  await page.route('**/ws-chat/**', route => route.abort());
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
