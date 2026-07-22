import { test, expect } from '@playwright/test';

test('End-to-End MoMo Payment Flow', async ({ page }) => {
  test.setTimeout(60_000);
  // 1. Go to login
  await page.goto('http://localhost:4200/login');
  
  // 2. Login
  await page.fill('#username', 'customer1');
  await page.fill('#password', 'customer1');
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL(/\/$/, { timeout: 15000 });

  // 3. Use isolated future dates so retries do not consume the same inventory
  const checkIn = new Date(Date.now() + (365 + Date.now() % 3650) * 86_400_000);
  const checkOut = new Date(checkIn.getTime() + 86_400_000);
  await page.goto(`http://localhost:4200/search?checkInDate=${checkIn.toISOString().slice(0, 10)}&checkOutDate=${checkOut.toISOString().slice(0, 10)}&adultCount=2&childCount=0&roomCount=1`, { waitUntil: 'domcontentloaded' });

  // Click the first hotel "Xem phòng"
  const viewButton = page.locator('button.view-button').first();
  await viewButton.waitFor({ state: 'visible', timeout: 15000 });
  await viewButton.click();

  // Wait for hotel detail page
  await page.waitForSelector('text=Chọn phòng', { timeout: 15000 });

  // Select one room, then continue to checkout
  const qtySelect = page.locator('.room-choice select').first();
  await qtySelect.waitFor({ state: 'visible', timeout: 15000 });
  await qtySelect.selectOption({ index: 1 });
  await page.click('button:has-text("Tiếp tục đặt phòng")');

  // Wait for checkout page
  await page.waitForURL(/.*\/booking\/.*/, { timeout: 15000 });

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
  
  const reservationId = new URL(page.url()).searchParams.get('reservationId');
  expect(reservationId).toMatch(/^\d+$/);

  // 8. Confirm payment and verify completed flow
  await page.click('button:has-text("Xác nhận Thanh toán")');
  await page.waitForURL(/.*\/profile\?tab=bookings.*/, { timeout: 15000 });

  const booking = page.locator('.booking-list article').filter({
    has: page.getByText(`#${reservationId}`, { exact: true })
  });
  await expect(booking).toBeVisible({ timeout: 15000 });
  await expect(booking.locator('.status')).toHaveText('Đã xác nhận', { timeout: 15000 });
  await page.screenshot({ path: 'booking-history.png' });

  // 9. Cancel the confirmed booking and verify the customer-facing refund flow
  page.once('dialog', async dialog => {
    expect(dialog.message()).toContain('Bạn có chắc chắn muốn hủy đặt phòng này?');
    await dialog.accept();
  });

  await booking.locator('button.cancel-btn').click();
  await expect(page.getByText('Đã hủy đặt phòng và xử lý hoàn tiền.')).toBeVisible({ timeout: 15000 });
  await expect(booking.locator('.status')).toHaveText('Đã hủy', { timeout: 15000 });
  await expect(booking.locator('button.cancel-btn')).toHaveCount(0);
  await page.screenshot({ path: 'booking-cancelled.png' });
});
