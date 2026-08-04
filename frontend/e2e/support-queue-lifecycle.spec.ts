import { APIRequestContext, expect, Page, test } from '@playwright/test';

const apiBase = 'http://127.0.0.1:18083/api';

interface AuthResponse {
  accessToken: string;
  username: string;
  userId: number;
  roles: string[];
  permissions: unknown[];
}

interface Conversation {
  conversationId: number;
  subject: string;
  status: string;
  version: number;
}

test('runs the real support queue HTTP, WebSocket, conflict and lifecycle journey', async ({ browser, request }) => {
  test.setTimeout(90_000);

  const suffix = Date.now();
  const customerUsername = `t326_customer_${suffix}`;
  const customerPassword = 'T326Customer!2026';
  const subject = `T326 queue ${suffix}`;

  const registration = await request.post(`${apiBase}/auth/register`, {
    data: {
      username: customerUsername,
      password: customerPassword,
      email: `${customerUsername}@example.test`,
      fullName: 'T326 Queue Customer',
      phone: '+84901234567',
    },
  });
  expect(registration.status()).toBe(201);

  const customerAuth = await login(request, customerUsername, customerPassword);
  const supportAuth = await login(request, 'admin', 'admin');
  const customerContext = await browser.newContext();
  const supportContext = await browser.newContext();
  const customerPage = await customerContext.newPage();
  const supportPage = await supportContext.newPage();

  await installSession(customerPage, customerAuth, 'T326 Queue Customer');
  await installSession(supportPage, supportAuth, 'System Admin');

  await customerPage.goto('/', { waitUntil: 'domcontentloaded' });
  await customerPage.getByRole('button', { name: 'Mo ho tro truc tuyen LuxeStay' }).click();
  await expect(customerPage.locator('.connection-status')).toContainText('Da ket noi', { timeout: 15_000 });
  await customerPage.getByRole('textbox', { name: 'Chu de hoi thoai moi' }).fill(subject);
  await customerPage.getByRole('button', { name: 'Tao moi' }).click();
  await customerPage.getByRole('textbox', { name: 'Noi dung tin nhan ho tro' })
    .fill('Tin nhan dau tien cho hang doi T326.');
  await customerPage.getByRole('button', { name: 'Gui tin nhan ho tro' }).click();

  await supportPage.goto('/admin/chat', { waitUntil: 'domcontentloaded' });
  await expect(supportPage.locator('.connection-pill')).toContainText('Da ket noi', { timeout: 15_000 });
  await expect(supportPage.getByRole('button', { name: new RegExp(subject) })).toBeVisible();
  await supportPage.getByLabel('Trang thai').selectOption('OPEN');
  await supportPage.getByLabel('Phan cong').selectOption('UNASSIGNED');
  await supportPage.getByLabel('SLA').selectOption('ON_TRACK');
  await expect(supportPage.getByRole('button', { name: new RegExp(subject) })).toBeVisible();
  await supportPage.getByRole('button', { name: new RegExp(subject) }).click();
  await expect(supportPage.getByText('Tin nhan dau tien cho hang doi T326.', { exact: true })).toBeVisible();
  await capture(supportPage, '.chat-dashboard', 'T326-support-queue.png');

  const queue = await supportRequest<Conversation[]>(request, supportAuth, '/chat/support/conversations');
  const staleConversation = queue.find(item => item.subject === subject);
  expect(staleConversation).toBeTruthy();
  const directClaim = await request.post(
    `${apiBase}/chat/support/conversations/${staleConversation!.conversationId}/assign`, {
      headers: authorization(supportAuth),
      params: { expectedVersion: staleConversation!.version },
    });
  expect(directClaim.status()).toBe(200);

  await supportPage.getByRole('button', { name: 'Nhan xu ly' }).click();
  await expect(supportPage.getByRole('status'))
    .toContainText('Hang doi da thay doi boi mot nhan vien khac', { timeout: 10_000 });
  await capture(supportPage, '.chat-dashboard', 'T326-conflict-recovery.png');

  await supportPage.getByLabel('Trang thai').selectOption('ALL');
  await supportPage.getByLabel('Phan cong').selectOption('ALL');
  await supportPage.getByLabel('SLA').selectOption('ALL');
  await supportPage.getByRole('button', { name: new RegExp(subject) }).click();
  await supportPage.getByRole('button', { name: 'Tra lai hang doi' }).click();
  await expect(supportPage.locator('.conversation-status')).toContainText('Dang cho');
  await supportPage.getByRole('button', { name: 'Chuyen cap' }).click();
  await expect(supportPage.locator('.conversation-status')).toContainText('Da chuyen cap');
  await supportPage.getByPlaceholder('Nhap ly do dong / mo lai').fill('Khach phan hoi them');
  await supportPage.getByRole('button', { name: 'Mo lai' }).click();
  await expect(supportPage.locator('.conversation-status')).toContainText('Dang cho');
  await capture(supportPage, '.chat-dashboard', 'T326-lifecycle-actions.png');

  await customerPage.getByRole('textbox', { name: 'Noi dung tin nhan ho tro' })
    .fill('Tin nhan realtime tu khach hang T326.');
  await customerPage.getByRole('button', { name: 'Gui tin nhan ho tro' }).click();
  await expect(supportPage.getByText('Tin nhan realtime tu khach hang T326.', { exact: true }))
    .toBeVisible({ timeout: 10_000 });

  await supportPage.getByRole('textbox', { name: 'Phan hoi khach hang' })
    .fill('Phan hoi realtime tu nhan vien T326.');
  await supportPage.getByRole('button', { name: 'Gui phan hoi' }).click();
  await expect(customerPage.getByText('Phan hoi realtime tu nhan vien T326.', { exact: true }))
    .toBeVisible({ timeout: 10_000 });
  await capture(customerPage, '#support-chat-panel', 'T326-realtime-customer.png');

  await customerContext.close();
  await supportContext.close();
});

async function login(request: APIRequestContext, username: string, password: string): Promise<AuthResponse> {
  const response = await request.post(`${apiBase}/auth/login`, { data: { username, password } });
  expect(response.status()).toBe(200);
  return response.json() as Promise<AuthResponse>;
}

async function installSession(page: Page, auth: AuthResponse, fullName: string) {
  await page.addInitScript(({ response, name }) => {
    localStorage.setItem('token', response.accessToken);
    localStorage.setItem('user', JSON.stringify({
      id: response.userId,
      username: response.username,
      fullName: name,
      roles: response.roles,
      permissions: response.permissions,
    }));
  }, { response: auth, name: fullName });
}

async function supportRequest<T>(
  request: APIRequestContext, auth: AuthResponse, path: string
): Promise<T> {
  const response = await request.get(`${apiBase}${path}`, { headers: authorization(auth) });
  expect(response.status()).toBe(200);
  return response.json() as Promise<T>;
}

function authorization(auth: AuthResponse): Record<string, string> {
  return { Authorization: `Bearer ${auth.accessToken}` };
}

async function capture(page: Page, selector: string, filename: string) {
  if (process.env['CAPTURE_T326_EVIDENCE'] !== '1') return;
  await page.locator(selector).screenshot({
    path: `../docs/testing/evidence/007/remediation/assets/${filename}`,
  });
}
