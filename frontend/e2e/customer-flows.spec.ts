import { test, expect, Page } from '@playwright/test';

// ============================================================
// E2E Test Suite: Customer Flows
// Kiểm thử đăng nhập Customer, truy cập Profile, Lịch sử đặt phòng.
// ============================================================

const CUSTOMER_USER = 'customer1';
const CUSTOMER_PASS = 'customer1';

async function customerLogin(page: Page) {
  await page.goto('/login');
  await page.locator('#username, input[formcontrolname="username"]').first().fill(CUSTOMER_USER);
  await page.locator('#password, input[formcontrolname="password"]').first().fill(CUSTOMER_PASS);
  await page.locator('button[type="submit"]').click();
  // Wait for redirect to home after login
  await page.waitForURL('**/', { timeout: 15000 });
}

test.describe('Customer - Profile & Booking History', () => {
  test('Đăng nhập thành công và truy cập Profile', async ({ page }) => {
    await customerLogin(page);
    await page.goto('/client/profile');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('body')).toBeVisible();
    
    // Check if profile details are visible
    // Wait for the app to render content
    await page.waitForTimeout(1000); 
    const bodyContent = await page.locator('body').textContent();
    expect(bodyContent!.length).toBeGreaterThan(0);
  });

  test('Truy cập trang Lịch sử đặt phòng', async ({ page }) => {
    await customerLogin(page);
    await page.goto('/client/profile?tab=bookings');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('body')).toBeVisible();
  });
});
