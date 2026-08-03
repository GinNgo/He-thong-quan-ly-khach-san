import { expect, test } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';

test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try {
      const response = await request.get(
        `http://localhost:${backendPort}/api/public/properties/search?pageNumber=1&pageSize=1`
      );
      const payload = await response.json() as { content?: unknown[] };
      return response.ok() && Boolean(payload.content?.length);
    } catch {
      return false;
    }
  }, { timeout: 120_000, intervals: [500, 1000, 2000] }).toBe(true);
});

test.beforeEach(async ({ page }) => {
  await page.route('http://localhost:8080/api/**', async route => {
    const response = await route.fetch({
      url: route.request().url().replace('http://localhost:8080', `http://localhost:${backendPort}`),
      headers: { ...route.request().headers(), origin: 'http://localhost:4200' }
    });
    await route.fulfill({
      response,
      headers: { ...response.headers(), 'access-control-allow-origin': `http://localhost:${frontendPort}` }
    });
  });
});

test('searches seeded API inventory and preserves the trip through detail and back', async ({ page }) => {
  await page.goto('/');

  await page.getByRole('button', { name: /Khách sạn/ }).click();
  const locationInput = page.locator('app-hero-search app-location-autocomplete input');
  await locationInput.fill('Đồng Tháp');
  await expect(page.locator('[data-suggestion-type="PROVINCE"]').first()).toBeVisible();
  await page.locator('[data-suggestion-type="PROVINCE"]').first().click();

  await page.locator('app-hero-search app-guest-room-selector > div > button').click();
  await page.getByRole('button', { name: 'Tăng số người lớn' }).click();
  await page.getByRole('button', { name: 'Tăng số phòng' }).click();

  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/api/public/properties/search') && response.request().method() === 'GET'
  );
  await page.getByRole('button', { name: 'TÌM', exact: true }).click();
  const response = await responsePromise;

  expect(response.status()).toBe(200);
  const payload = await response.json() as { content?: unknown[] };
  expect(payload.content?.length).toBeGreaterThan(0);
  await expect(page).toHaveURL(/propertyTypes=HOTEL/);
  await expect(page).toHaveURL(/adultCount=3/);
  await expect(page).toHaveURL(/roomCount=2/);
  await expect(page.locator('app-property-result-card').first()).toBeVisible();

  const searchUrl = page.url();
  await page.locator('app-property-result-card .view-button').first().click();
  await expect(page).toHaveURL(/\/hotel\/\d+/);
  await expect(page).toHaveURL(/adultCount=3/);
  await expect(page).toHaveURL(/roomCount=2/);

  await page.goBack();
  await expect(page).toHaveURL(searchUrl);
  await expect(page.getByRole('heading', { name: /Đồng Tháp/i })).toBeVisible();

  const searchParams = new URL(searchUrl).searchParams;
  const requestedBookingUrl = `/booking/999?checkIn=${searchParams.get('checkInDate')}`
    + `&checkOut=${searchParams.get('checkOutDate')}&adultCount=3&roomCount=2&quantity=2`;
  await page.goto(requestedBookingUrl);
  await expect(page).toHaveURL(/\/login\?returnUrl=/);
  expect(new URL(page.url()).searchParams.get('returnUrl')).toBe(requestedBookingUrl);
});

test('announces missing dates and returns keyboard focus to the date trigger on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');

  const dateTrigger = page.locator('app-hero-search app-date-range-selector .date-trigger').first();
  await dateTrigger.click();
  await page.locator('.p-datepicker-clear-button').click();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'TÌM', exact: true }).click();

  await expect(page.getByRole('alert')).toContainText('Vui lòng chọn ngày nhận phòng.');
  await expect(dateTrigger).toBeFocused();
  await expect(dateTrigger).toHaveAttribute('aria-invalid', 'true');
});
