import { test, expect, Page } from '@playwright/test';

const sessionUser = (roles: string[]) => ({ username: 'customer.demo@example.com', fullName: 'Nguyễn Minh An', avatarUrl: '', roles, permissions: [] });

async function openAs(page: Page, context: Record<string, unknown>, roles: string[] = ['CUSTOMER']) {
  await page.addInitScript(({ user, roleList }) => {
    localStorage.setItem('token', 'e2e-context-token');
    localStorage.setItem('user', JSON.stringify({ ...user, roles: roleList }));
  }, { user: sessionUser(roles), roleList: roles });
  await page.route('**/api/users/me', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(context) }));
  await page.goto('/');
}

test.describe('Public and customer data quality', () => {
  test('Home renders distinct database media, prices and no broken images', async ({ page }) => {
    const seriousErrors: string[] = [];
    page.on('console', message => { if (message.type() === 'error') seriousErrors.push(message.text()); });
    await page.goto('/');
    await expect(page.locator('app-popular-destinations img').first()).toBeVisible();
    await expect(page.locator('app-featured-properties .property-card').first()).toBeVisible();

    const destinationSources = await page.locator('app-popular-destinations img').evaluateAll(images => [...new Set(images.map(image => (image as HTMLImageElement).src))]);
    const propertySources = await page.locator('app-featured-properties .property-card img').evaluateAll(images => [...new Set(images.map(image => (image as HTMLImageElement).src))]);
    const prices = await page.locator('app-featured-properties .property-price strong').allTextContents();
    const distinctPrices = new Set(prices.map(price => price.trim()));

    expect(destinationSources.length).toBeGreaterThanOrEqual(3);
    expect(propertySources.length).toBeGreaterThanOrEqual(3);
    expect(propertySources.some(source => source.includes('/assets/demo/'))).toBeFalsy();
    expect(distinctPrices.size).toBeGreaterThanOrEqual(3);
    await expect(page.getByText('Chưa có đánh giá').first()).toBeVisible();
    await expect(page.getByText('9.4', { exact: true })).toHaveCount(0);

    const broken = await page.locator('main img').evaluateAll(images => images.filter(image => !(image as HTMLImageElement).complete || (image as HTMLImageElement).naturalWidth === 0).map(image => (image as HTMLImageElement).src));
    expect(broken).toEqual([]);
    expect(seriousErrors.filter(error => !error.includes('favicon'))).toEqual([]);
  });

  test('availability changes for an overlapping reservation date', async ({ request }) => {
    const fetchProperties = async (checkInDate: string, checkOutDate: string) => {
      const response = await request.get(`http://localhost:8080/api/public/properties/search?keyword=Mekong%20Garden%20Inn%20Phuong%20Quang%20An&checkInDate=${checkInDate}&checkOutDate=${checkOutDate}&adultCount=1&childCount=0&roomCount=1&pageNumber=0&pageSize=20`);
      expect(response.ok()).toBeTruthy();
      return (await response.json()).content as Array<{ id: number; availableRoomCount: number }>;
    };
    const overlap = await fetchProperties('2026-09-01', '2026-09-02');
    const clear = await fetchProperties('2026-10-01', '2026-10-02');
    const overlapHotel = overlap.find(property => property.id === 13);
    const clearHotel = clear.find(property => property.id === 13);
    expect(overlapHotel).toBeTruthy(); expect(clearHotel).toBeTruthy();
    expect(overlapHotel!.availableRoomCount).toBeLessThan(clearHotel!.availableRoomCount);
  });

  test('Customer menu exposes real routes and hides owner controls', async ({ page }) => {
    await openAs(page, {
      id: 91, username: 'customer.demo@example.com', email: 'customer.demo@example.com', fullName: 'Nguyễn Minh An', roles: [{ code: 'CUSTOMER' }],
      assignedProperties: [], partnerRegistrationStatus: 'NONE', pendingBookingCount: 2, unreadMessageCount: 0
    });
    await page.locator('.account-trigger').click();
    await expect(page.getByRole('menuitem', { name: /Thông tin cá nhân/ })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Chuyến đi của tôi/ })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Hóa đơn của tôi/ })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Đăng chỗ nghỉ của bạn/ })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Tổng quan quản lý/ })).toHaveCount(0);
    await page.getByRole('menuitem', { name: /Đăng chỗ nghỉ của bạn/ }).click();
    await expect(page).toHaveURL(/\/partner\/register$/);
  });

  test('Pending and owner contexts receive the correct partner actions', async ({ page }) => {
    await openAs(page, {
      id: 92, username: 'pending@example.com', email: 'pending@example.com', fullName: 'Hồ sơ chờ duyệt', roles: [{ code: 'CUSTOMER' }],
      assignedProperties: [], partnerRegistrationStatus: 'PENDING', pendingBookingCount: 0
    });
    await expect(page.locator('.partner-button')).toHaveText('Hồ sơ đang duyệt');
    await page.locator('.partner-button').click();
    await expect(page).toHaveURL(/\/partner\/registration-status$/);

    await page.context().clearCookies(); await page.evaluate(() => localStorage.clear());
    await page.unroute('**/api/users/me');
    await page.route('**/api/users/me', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      id: 93, username: 'owner@example.com', email: 'owner@example.com', fullName: 'Chủ cơ sở', roles: [{ code: 'PROPERTY_OWNER' }],
      assignedProperties: [{ id: 12, name: 'LuxeStay Demo' }], partnerRegistrationStatus: 'APPROVED', plan: 'STANDARD'
    }) }));
    await page.addInitScript(() => { localStorage.setItem('token', 'owner-token'); localStorage.setItem('user', JSON.stringify({ username: 'owner@example.com', roles: ['PROPERTY_OWNER'] })); });
    await page.goto('/'); await page.locator('.account-trigger').click();
    await expect(page.getByRole('menuitem', { name: /Tổng quan quản lý/ })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: /Gói dịch vụ/ })).toBeVisible();
  });

  test('mobile account menu fits the viewport and logout clears the session', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await openAs(page, { id: 94, username: 'mobile@example.com', email: 'mobile@example.com', fullName: 'Khách Mobile', roles: [{ code: 'CUSTOMER' }], assignedProperties: [], partnerRegistrationStatus: 'NONE' });
    await page.locator('.account-trigger').click();
    const box = await page.locator('.account-menu').boundingBox();
    expect(box).not.toBeNull(); expect(box!.x).toBeGreaterThanOrEqual(0); expect(box!.width).toBeLessThanOrEqual(390);
    await page.getByRole('menuitem', { name: /Đăng xuất/ }).click();
    await expect(page.locator('.login-button')).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('token'))).toBeNull();
  });
});
