import { test, expect } from '@playwright/test';

test('End-to-End MoMo Payment Flow', async ({ page }) => {
  // 1. Go to login
  await page.goto('http://localhost:4200/login');
  
  // 2. Login
  await page.fill('input[type="text"]', 'customer1');
  await page.fill('input[type="password"]', 'password');
  await page.click('button[type="submit"]');
  await page.waitForURL('http://localhost:4200/');

  // 3. Go to search and pick a room
  await page.goto('http://localhost:4200/search');
  await page.waitForLoadState('networkidle');
  
  // Click the first hotel "Xem chi tiết"
  await page.click('text=Xem chi tiết >> nth=0');
  
  // Wait for hotel detail page
  await page.waitForSelector('text=Đặt phòng ngay');
  
  // Click first "Đặt phòng ngay"
  await page.click('text=Đặt phòng ngay >> nth=0');
  
  // Wait for checkout page
  await page.waitForURL(/.*\/booking\/.*/);

  // 4. Fill checkout form
  await page.fill('input[name="lastName"]', 'Test');
  await page.fill('input[name="firstName"]', 'Customer');
  await page.fill('input[name="phone"]', '0123456789');
  
  // 5. Select MoMo payment
  await page.click('input[value="MOMO"]');
  
  // 6. Submit
  await page.click('button[type="submit"]');

  // 7. Verify Redirect to Payment Simulator
  await page.waitForURL(/.*\/payment-simulator.*/);
  
  // Take screenshot of simulator
  await page.screenshot({ path: 'payment-simulator.png' });
  
  // 8. Confirm Payment
  await page.click('button:has-text("Xác nhận Thanh toán")');
  
  // 9. Verify Success Screen
  await page.waitForSelector('text=Thanh toán thành công!');
  await page.screenshot({ path: 'payment-success.png' });
  
  // 10. Wait for auto redirect to profile bookings
  await page.waitForURL(/.*\/profile\?tab=bookings.*/, { timeout: 10000 });
  await page.screenshot({ path: 'booking-history.png' });
});
