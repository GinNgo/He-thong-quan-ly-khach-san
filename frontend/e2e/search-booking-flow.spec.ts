import { expect, test } from '@playwright/test';

test.describe('Search result and room selection', () => {
  test('keeps search state, applies real filters and opens room selection', async ({ page }) => {
    await page.goto('/search?displayLocation=Ti%E1%BB%81n%20Giang&provinceId=1&checkInDate=2026-08-01&checkOutDate=2026-08-03&adultCount=2&childCount=0&roomCount=1');
    await expect(page.getByRole('heading', { name: 'Tiền Giang' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Xem phòng/ }).first()).toBeVisible({ timeout: 15000 });

    await page.getByRole('checkbox', { name: 'Khách sạn', exact: true }).check();
    await page.getByRole('button', { name: 'Áp dụng bộ lọc' }).click();
    await expect(page).toHaveURL(/propertyTypes=HOTEL/);
    await expect(page.getByRole('button', { name: /Khách sạn/ }).first()).toBeVisible();

    await page.getByRole('button', { name: /Xem phòng/ }).first().click();
    await expect(page).toHaveURL(/checkInDate=2026-08-01/);
    await expect(page).toHaveURL(/checkOutDate=2026-08-03/);
    await expect(page.locator('#rooms')).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('heading', { name: 'Chọn phòng' })).toBeVisible();
  });

  test('mobile search and filter do not overflow', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/search?checkInDate=2026-08-01&checkOutDate=2026-08-02&adultCount=2&roomCount=1');
    await page.locator('.mobile-summary').click();
    await expect(page.getByRole('dialog', { name: 'Thay đổi tìm kiếm' })).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(overflow).toBe(false);
  });
});
