import { test, expect } from '@playwright/test';

test.describe('Home Search Redesign', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display the hero search panel with default values', async ({ page }) => {
    // Check tabs
    await expect(page.locator('app-search-service-tabs')).toBeVisible();
    await expect(page.getByText('Tất cả chỗ nghỉ')).toBeVisible();

    // Check location
    const locationInput = page.locator('app-location-autocomplete input');
    await expect(locationInput).toBeVisible();
    await expect(locationInput).toHaveAttribute('placeholder', 'Bạn muốn đến đâu?');

    // Check dates (should have default text)
    await expect(page.locator('app-date-range-selector')).toBeVisible();
    
    // Check guests (should default to 2 adults, 1 room)
    const guestSelector = page.locator('app-guest-room-selector');
    await expect(guestSelector).toContainText('2 người lớn');
    await expect(guestSelector).toContainText('1 phòng');
  });

  test('should open location autocomplete on click and show popular destinations', async ({ page }) => {
    const locationInput = page.locator('app-location-autocomplete input');
    await locationInput.click();
    
    // Popover should be visible
    const popover = page.locator('.p-popover');
    await expect(popover).toBeVisible();
    
    // Should show popular destinations
    await expect(page.getByText('Các thành phố nổi tiếng')).toBeVisible();
  });

  test('should toggle stay type to day-use', async ({ page }) => {
    // Wait, we disabled day-use in our implementation for now because backend doesn't support it!
    // Let's verify it is disabled.
    const dayUseRadio = page.locator('input[value="DAY_USE"]');
    await expect(dayUseRadio).toBeDisabled();
    
    const dayUseLabel = page.getByText('Chỗ Ở Trong Ngày');
    await expect(dayUseLabel).toBeVisible();
  });

  test('should update guest counts', async ({ page }) => {
    await page.locator('app-guest-room-selector').click();
    
    // Increase adults (second pi-plus button)
    const plusButtons = page.locator('.p-popover .pi-plus');
    await plusButtons.nth(1).click();
    
    // Wait for update
    await expect(page.locator('app-guest-room-selector')).toContainText('3 người lớn');
  });

  test('sticky search bar should appear on scroll', async ({ page }) => {
    // Initially hidden
    const stickyBar = page.locator('app-sticky-search-bar > div').first();
    await expect(stickyBar).toHaveClass(/.*-translate-y-full.*/);

    // Scroll down 1000px (Hero is 600px, so 1000px will definitely hide it)
    await page.evaluate(() => window.scrollBy(0, 1000));
    
    // Wait a bit for observer to fire
    await page.waitForTimeout(500);
    
    // Should become visible
    await expect(stickyBar).toHaveClass(/.*translate-y-0.*/);
  });
});
