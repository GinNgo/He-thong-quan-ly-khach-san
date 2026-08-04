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

test('keeps day-use unavailable and omits stayType from public search requests', async ({ page }) => {
  await page.goto('/');

  await expect(page.locator('input[value="DAY_USE"]')).toHaveCount(0);
  const responsePromise = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.endsWith('/api/public/properties/search')
      && url.searchParams.has('checkInDate')
      && response.request().method() === 'GET';
  });

  await page.locator('app-hero-search button').filter({ hasText: 'TÌM' }).click();
  const response = await responsePromise;

  expect(response.status()).toBe(200);
  expect(new URL(response.url()).searchParams.has('stayType')).toBe(false);
  expect(new URL(page.url()).searchParams.has('stayType')).toBe(false);
  await expect(page.locator('app-property-result-card').first()).toBeVisible();
});

test('supports grouped accent, property, landmark and keyboard autocomplete journeys', async ({ page }) => {
  await page.goto('/');
  const input = page.locator('app-hero-search app-location-autocomplete input');

  await input.fill('dong thap');
  await expect(page.locator('[data-suggestion-type="PROVINCE"]').first()).toBeVisible();
  await input.fill('Đồng Tháp');
  await expect(page.locator('[data-suggestion-type="PROVINCE"]').first()).toBeVisible();

  await input.fill('anh duong');
  const property = page.locator('[data-suggestion-type="PROPERTY"]')
    .filter({ hasText: 'Ánh Dương' }).first();
  await expect(property).toBeVisible();
  for (let index = 0; index < 10 && await property.getAttribute('aria-selected') !== 'true'; index++) {
    await input.press('ArrowDown');
  }
  await expect(property).toHaveAttribute('aria-selected', 'true');
  await input.press('Enter');
  await expect(page).toHaveURL(/\/hotel\/\d+/);

  await page.goto('/');
  const landmarkInput = page.locator('app-hero-search app-location-autocomplete input');
  await landmarkInput.fill('cong vien my tho');
  const landmark = page.locator('[data-suggestion-type="LANDMARK"]').first();
  await expect(landmark).toBeVisible();
  await landmark.click();

  const responsePromise = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.endsWith('/api/public/properties/search')
      && url.searchParams.has('landmarkId')
      && response.request().method() === 'GET';
  });
  await page.locator('app-hero-search button').filter({ hasText: 'TÌM' }).click();
  const response = await responsePromise;

  expect(response.status()).toBe(200);
  expect(new URL(response.url()).searchParams.get('sortBy')).toBe('NEAREST');
  await expect(page).toHaveURL(/landmarkId=\d+/);
  await expect(page.locator('app-property-result-card').first()).toBeVisible();

  const stickyInput = page.locator('app-sticky-search-bar app-location-autocomplete input').first();
  await expect(stickyInput).toHaveValue(/Công viên Mỹ Tho/);
  await page.locator('app-sticky-search-bar app-guest-room-selector > div > button').click();
  await page.getByRole('button', { name: 'Tăng số người lớn' }).click();
  const repeatedResponsePromise = page.waitForResponse(candidate => {
    const url = new URL(candidate.url());
    return url.pathname.endsWith('/api/public/properties/search')
      && url.searchParams.has('landmarkId')
      && candidate.request().method() === 'GET';
  });
  await page.locator('app-sticky-search-bar .desktop-fields .search-button').click();
  expect((await repeatedResponsePromise).status()).toBe(200);
  await expect(page).toHaveURL(/landmarkId=\d+/);
});

test('supports current province compatibility, legacy wards and duplicate landmarks', async ({ page }) => {
  const provinceResponse = await page.request.get(
    `http://localhost:${backendPort}/api/public/locations/provinces`
  );
  expect(provinceResponse.ok()).toBe(true);
  const provinces = await provinceResponse.json() as Array<{ id: number; sourceCode: string }>;
  expect(provinces).toHaveLength(34);
  expect(provinces.every(province => province.sourceCode.startsWith('VN34-'))).toBe(true);

  const currentDongThap = provinces.find(province => province.sourceCode === 'VN34-82');
  expect(currentDongThap).toBeTruthy();
  const wardResponse = await page.request.get(
    `http://localhost:${backendPort}/api/public/locations/provinces/${currentDongThap!.id}/wards`
  );
  const wards = await wardResponse.json() as Array<{ id: number; nameVi: string; normalizedName: string }>;
  expect(wardResponse.ok()).toBe(true);
  expect(wards.length).toBeGreaterThan(0);
  const propertySuggestionResponse = await page.request.get(
    `http://localhost:${backendPort}/api/public/search/suggestions?keyword=anh%20duong`
  );
  const propertySuggestions = await propertySuggestionResponse.json() as {
    properties: Array<{ wardId: number }>;
  };
  const seedWard = wards.find(ward => ward.id === propertySuggestions.properties[0]?.wardId);
  expect(seedWard).toBeTruthy();

  await page.goto('/');
  const input = page.locator('app-hero-search app-location-autocomplete input');
  await input.fill(seedWard!.normalizedName);
  const legacyWard = page.locator('[data-suggestion-type="WARD"]')
    .filter({ hasText: seedWard!.nameVi }).first();
  await expect(legacyWard).toBeVisible();
  await legacyWard.click();
  const wardSearch = page.waitForResponse(response =>
    response.url().includes('/api/public/properties/search') && response.request().method() === 'GET'
  );
  await page.getByRole('button', { name: 'TÌM', exact: true }).click();
  expect((await wardSearch).status()).toBe(200);
  await expect(page).toHaveURL(/wardId=\d+/);

  await page.goto('/');
  const landmarkInput = page.locator('app-hero-search app-location-autocomplete input');
  await landmarkInput.fill('ho xuan huong');
  const duplicates = page.locator('[data-suggestion-type="LANDMARK"]');
  await expect(duplicates).toHaveCount(2);
  await duplicates.first().click();
  const landmarkSearch = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.endsWith('/api/public/properties/search')
      && url.searchParams.has('landmarkId')
      && response.request().method() === 'GET';
  });
  await page.getByRole('button', { name: 'TÌM', exact: true }).click();
  expect((await landmarkSearch).status()).toBe(200);
  await expect(page).toHaveURL(/landmarkId=\d+/);
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
