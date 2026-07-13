# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: home-search.spec.ts >> Home Search Redesign >> sticky search bar should appear on scroll
- Location: e2e\home-search.spec.ts:60:7

# Error details

```
Error: expect(locator).toHaveClass(expected) failed

Locator: locator('app-sticky-search-bar > div').first()
Expected pattern: /.*translate-y-0.*/
Received string:  "fixed top-[80px] left-0 right-0 z-40 bg-white shadow-md border-b border-gray-200 transition-transform duration-300 transform -translate-y-full"
Timeout: 5000ms

Call log:
  - Expect "toHaveClass" with timeout 5000ms
  - waiting for locator('app-sticky-search-bar > div').first()
    13 × locator resolved to <div _ngcontent-ng-c141084617="" class="fixed top-[80px] left-0 right-0 z-40 bg-white shadow-md border-b border-gray-200 transition-transform duration-300 transform -translate-y-full">…</div>
       - unexpected value "fixed top-[80px] left-0 right-0 z-40 bg-white shadow-md border-b border-gray-200 transition-transform duration-300 transform -translate-y-full"

```

```yaml
- text: LuxeStay 
- textbox "Bạn muốn đến đâu?"
- text:  13 tháng 7 2026 Thứ Hai  14 tháng 7 2026 Thứ Ba  2 người lớn 1 phòng 
- button "TÌM"
```

# Test source

```ts
  1  | import { test, expect } from '@playwright/test';
  2  | 
  3  | test.describe('Home Search Redesign', () => {
  4  | 
  5  |   test.beforeEach(async ({ page }) => {
  6  |     await page.goto('/');
  7  |   });
  8  | 
  9  |   test('should display the hero search panel with default values', async ({ page }) => {
  10 |     // Check tabs
  11 |     await expect(page.locator('app-search-service-tabs')).toBeVisible();
  12 |     await expect(page.getByText('Tất cả chỗ nghỉ')).toBeVisible();
  13 | 
  14 |     // Check location
  15 |     const locationInput = page.locator('app-location-autocomplete input');
  16 |     await expect(locationInput).toBeVisible();
  17 |     await expect(locationInput).toHaveAttribute('placeholder', 'Bạn muốn đến đâu?');
  18 | 
  19 |     // Check dates (should have default text)
  20 |     await expect(page.locator('app-date-range-selector')).toBeVisible();
  21 |     
  22 |     // Check guests (should default to 2 adults, 1 room)
  23 |     const guestSelector = page.locator('app-guest-room-selector');
  24 |     await expect(guestSelector).toContainText('2 người lớn');
  25 |     await expect(guestSelector).toContainText('1 phòng');
  26 |   });
  27 | 
  28 |   test('should open location autocomplete on click and show popular destinations', async ({ page }) => {
  29 |     const locationInput = page.locator('app-location-autocomplete input');
  30 |     await locationInput.click();
  31 |     
  32 |     // Popover should be visible
  33 |     const popover = page.locator('.p-popover');
  34 |     await expect(popover).toBeVisible();
  35 |     
  36 |     // Should show popular destinations
  37 |     await expect(page.getByText('Các thành phố nổi tiếng')).toBeVisible();
  38 |   });
  39 | 
  40 |   test('should toggle stay type to day-use', async ({ page }) => {
  41 |     // Click Day Use
  42 |     await page.getByText('Chỗ Ở Trong Ngày').click();
  43 |     // Wait, we disabled day-use in our implementation for now because backend doesn't support it!
  44 |     // Let's verify it is disabled.
  45 |     const dayUseRadio = page.locator('input[value="DAY_USE"]');
  46 |     await expect(dayUseRadio).toBeDisabled();
  47 |   });
  48 | 
  49 |   test('should update guest counts', async ({ page }) => {
  50 |     await page.locator('app-guest-room-selector').click();
  51 |     
  52 |     // Increase adults (second pi-plus button)
  53 |     const plusButtons = page.locator('.p-popover .pi-plus');
  54 |     await plusButtons.nth(1).click();
  55 |     
  56 |     // Wait for update
  57 |     await expect(page.locator('app-guest-room-selector')).toContainText('3 người lớn');
  58 |   });
  59 | 
  60 |   test('sticky search bar should appear on scroll', async ({ page }) => {
  61 |     // Initially hidden
  62 |     const stickyBar = page.locator('app-sticky-search-bar > div').first();
  63 |     await expect(stickyBar).toHaveClass(/.*-translate-y-full.*/);
  64 | 
  65 |     // Scroll down 500px
  66 |     await page.evaluate(() => window.scrollBy(0, 500));
  67 |     
  68 |     // Should become visible
> 69 |     await expect(stickyBar).toHaveClass(/.*translate-y-0.*/);
     |                             ^ Error: expect(locator).toHaveClass(expected) failed
  70 |   });
  71 | });
  72 | 
```