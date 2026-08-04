import { expect, test, type Page } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';

interface SearchProperty {
  id: number;
  name: string;
}

interface SearchPage {
  content: SearchProperty[];
  totalElements: number;
}

interface SuggestionGroups {
  provinces: Array<{ id: number; displayName: string }>;
  landmarks: Array<{
    id: number;
    provinceId: number;
    wardId?: number;
    latitude: number;
    longitude: number;
    defaultRadiusKm: number;
  }>;
}

test.beforeAll(async ({ request }) => {
  await expect.poll(async () => {
    try {
      const response = await request.get(
        `http://localhost:${backendPort}/api/public/properties/search?pageNumber=1&pageSize=1`,
      );
      return response.ok();
    } catch {
      return false;
    }
  }, { timeout: 120_000, intervals: [500, 1000, 2000] }).toBe(true);
});

test.beforeEach(async ({ page }) => {
  await page.route('http://localhost:8080/api/**', async route => {
    const response = await route.fetch({
      url: route.request().url().replace('http://localhost:8080', `http://localhost:${backendPort}`),
      headers: { ...route.request().headers(), origin: 'http://localhost:4200' },
    });
    await route.fulfill({
      response,
      headers: { ...response.headers(), 'access-control-allow-origin': `http://localhost:${frontendPort}` },
    });
  });
});

test('forwards only the canonical public search allowlist from route state', async ({ page }) => {
  const suggestionsResponse = await page.request.get(
    `http://localhost:${backendPort}/api/public/search/suggestions?keyword=cong%20vien%20my%20tho&limit=10`,
  );
  expect(suggestionsResponse.ok()).toBe(true);
  const suggestions = await suggestionsResponse.json() as SuggestionGroups;
  const landmark = suggestions.landmarks[0];
  expect(landmark).toBeTruthy();

  const routeParams = new URLSearchParams({
    displayLocation: 'Công viên Mỹ Tho, Tỉnh Đồng Tháp',
    propertyId: '999999',
    arbitrary: 'must-not-reach-api',
    _retry: '274',
    keyword: 'khach san',
    provinceId: String(landmark.provinceId),
    landmarkId: String(landmark.id),
    checkInDate: localDate(2),
    checkOutDate: localDate(4),
    adultCount: '2',
    childCount: '1',
    roomCount: '1',
    latitude: String(landmark.latitude),
    longitude: String(landmark.longitude),
    radiusKm: String(landmark.defaultRadiusKm),
    sortBy: 'NEAREST',
    pageNumber: '1',
    pageSize: '10',
    propertyTypes: 'HOTEL,HOMESTAY',
    stayType: 'OVERNIGHT',
    minPrice: '100000',
    maxPrice: '1500000',
    starRatings: '3,4,5',
    minReviewScore: '7',
    freeCancellation: 'false',
    payAtProperty: 'false',
    breakfastIncluded: 'false',
  });
  if (landmark.wardId) routeParams.set('wardId', String(landmark.wardId));

  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/api/public/properties/search')
      && response.request().method() === 'GET',
  );
  await page.goto(`/search?${routeParams}`);
  const response = await responsePromise;
  const apiParams = new URL(response.url()).searchParams;

  expect(response.status()).toBe(200);
  for (const key of [
    'keyword', 'provinceId', 'landmarkId', 'checkInDate', 'checkOutDate', 'adultCount',
    'childCount', 'roomCount', 'latitude', 'longitude', 'radiusKm', 'sortBy', 'pageNumber',
    'pageSize', 'propertyTypes', 'stayType', 'minPrice', 'maxPrice', 'starRatings',
    'minReviewScore', 'freeCancellation', 'payAtProperty', 'breakfastIncluded',
  ]) {
    expect(apiParams.has(key), `${key} should reach the canonical API`).toBe(true);
  }
  if (landmark.wardId) expect(apiParams.get('wardId')).toBe(String(landmark.wardId));
  expect(apiParams.has('displayLocation')).toBe(false);
  expect(apiParams.has('propertyId')).toBe(false);
  expect(apiParams.has('arbitrary')).toBe(false);
  expect(apiParams.has('_retry')).toBe(false);
});

test('presents the authoritative invalid-date 400 instead of a connection error', async ({ page }) => {
  const checkInDate = localDate(4);
  const checkOutDate = localDate(2);
  const directResponse = await page.request.get(
    `http://localhost:${backendPort}/api/public/properties/search`
      + `?checkInDate=${checkInDate}&checkOutDate=${checkOutDate}`,
  );
  expect(directResponse.status()).toBe(400);
  expect(await directResponse.json()).toMatchObject({
    code: 'INVALID_REQUEST',
    message: 'Ngày trả phòng phải sau ngày nhận phòng.',
  });

  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/api/public/properties/search')
      && response.request().method() === 'GET',
  );
  await page.goto(
    `/search?displayLocation=Đồng%20Tháp&checkInDate=${checkInDate}&checkOutDate=${checkOutDate}`
      + '&adultCount=2&childCount=0&roomCount=1',
  );
  expect((await responsePromise).status()).toBe(400);

  const error = page.locator('[data-search-api-error][data-error-code="INVALID_REQUEST"]');
  await expect(error).toBeVisible();
  await expect(error).toContainText('Yêu cầu tìm kiếm không hợp lệ');
  await expect(error).toContainText('Ngày trả phòng phải sau ngày nhận phòng.');
  await expect(error.getByRole('button', { name: 'Chỉnh sửa tìm kiếm' })).toBeVisible();
  await expect(page.getByText('Ngày lưu trú không hợp lệ')).toBeVisible();
  await expect(page.locator('app-property-result-card')).toHaveCount(0);
});

test('keeps current and legacy province aliases equivalent and exposes only eligible demo inventory', async ({ page }) => {
  const currentSuggestion = await provinceSuggestion(page, 'dong thap');
  const legacySuggestion = await provinceSuggestion(page, 'tien giang');
  expect(legacySuggestion.id).toBe(currentSuggestion.id);
  expect(legacySuggestion.displayName).toContain('Đồng Tháp');

  const currentIds = await searchProvinceFromHome(page, 'Đồng Tháp');
  const legacyIds = await searchProvinceFromHome(page, 'Tiền Giang');
  expect(legacyIds).toEqual(currentIds);

  const visibleResponse = await page.request.get(
    `http://localhost:${backendPort}/api/public/properties/search?keyword=song%20han&pageSize=20`,
  );
  expect(visibleResponse.ok()).toBe(true);
  const visible = await visibleResponse.json() as SearchPage;
  expect(visible.content.map(property => property.name)).toContain('Khách sạn Sông Hàn');

  const excludedResponse = await page.request.get(
    `http://localhost:${backendPort}/api/public/properties/search?keyword=T274&pageSize=20`,
  );
  expect(excludedResponse.ok()).toBe(true);
  const excluded = await excludedResponse.json() as SearchPage;
  expect(excluded.content.map(property => property.name)).not.toEqual(expect.arrayContaining([
    'T274 Draft Sentinel',
    'T274 Suspended Sentinel',
  ]));

  const browserResponse = page.waitForResponse(response =>
    response.url().includes('/api/public/properties/search')
      && new URL(response.url()).searchParams.get('keyword') === 'song han',
  );
  await page.goto(`/search?keyword=song%20han&checkInDate=${localDate(2)}&checkOutDate=${localDate(3)}`
    + '&adultCount=2&childCount=0&roomCount=1');
  expect((await browserResponse).status()).toBe(200);
  await expect(page.locator('app-property-result-card').filter({ hasText: 'Khách sạn Sông Hàn' })).toBeVisible();
  await expect(page.getByText('T274 Draft Sentinel')).toHaveCount(0);
  await expect(page.getByText('T274 Suspended Sentinel')).toHaveCount(0);
});

async function provinceSuggestion(
  page: Page,
  keyword: string,
): Promise<{ id: number; displayName: string }> {
  const response = await page.request.get(
    `http://localhost:${backendPort}/api/public/search/suggestions?keyword=${encodeURIComponent(keyword)}&limit=10`,
  );
  expect(response.ok()).toBe(true);
  const groups = await response.json() as SuggestionGroups;
  const province = groups.provinces.find(item => item.displayName.includes('Đồng Tháp'));
  expect(province).toBeTruthy();
  return province!;
}

async function searchProvinceFromHome(
  page: Page,
  keyword: string,
): Promise<number[]> {
  await page.goto('/');
  const input = page.locator('app-hero-search app-location-autocomplete input');
  await input.fill(keyword);
  const province = page.locator('[data-suggestion-type="PROVINCE"]')
    .filter({ hasText: 'Đồng Tháp' }).first();
  await expect(province).toBeVisible();
  await province.click();
  const responsePromise = page.waitForResponse(response =>
    response.url().includes('/api/public/properties/search')
      && response.request().method() === 'GET',
  );
  await page.getByRole('button', { name: 'TÌM', exact: true }).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const payload = await response.json() as SearchPage;
  await expect(page.locator('app-property-result-card').first()).toBeVisible();
  return payload.content.map(property => property.id);
}

function localDate(offsetDays: number): string {
  const value = new Date();
  value.setHours(0, 0, 0, 0);
  value.setDate(value.getDate() + offsetDays);
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
