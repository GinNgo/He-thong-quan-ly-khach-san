import { expect, test } from '@playwright/test';

const backendPort = process.env['PUBLIC_BOOKING_BACKEND_PORT'] || '28743';
const frontendPort = process.env['PUBLIC_BOOKING_FRONTEND_PORT'] || '42769';

interface PopularDestination {
  id: number;
  name: string;
  displayName: string;
  propertyCount: number;
  imageUrl: string;
  imageAltText: string;
  imageProvenance: string;
}

interface FeaturedProperty {
  id: number;
  name: string;
  propertyType?: string;
  thumbnailUrl?: string;
  mainImageUrl?: string;
  mainImage?: string;
  imageAltText: string;
  imageProvenance: string;
  reviewScore?: number;
  reviewCount?: number;
}

interface FeaturedPropertyPage {
  content: FeaturedProperty[];
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

function compareText(left: string, right: string): number {
  if (left === right) return 0;
  return left < right ? -1 : 1;
}

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

test('renders deterministic seeded popular and featured discovery with governed image fallback', async ({ page }) => {
  const featuredApiUrl = new URL(`http://localhost:${backendPort}/api/public/properties/search`);
  featuredApiUrl.searchParams.set('adultCount', '2');
  featuredApiUrl.searchParams.set('childCount', '0');
  featuredApiUrl.searchParams.set('roomCount', '1');
  featuredApiUrl.searchParams.set('checkInDate', localDate(0));
  featuredApiUrl.searchParams.set('checkOutDate', localDate(1));
  featuredApiUrl.searchParams.set('pageNumber', '0');
  featuredApiUrl.searchParams.set('pageSize', '8');
  featuredApiUrl.searchParams.set('sortBy', 'RATING');

  const seededFeaturedResponse = await page.request.get(featuredApiUrl.toString());
  expect(seededFeaturedResponse.ok()).toBe(true);
  const seededFeatured = await seededFeaturedResponse.json() as FeaturedPropertyPage;
  expect(seededFeatured.content.length).toBeGreaterThan(0);
  const brokenAsset = seededFeatured.content[0].thumbnailUrl
    || seededFeatured.content[0].mainImageUrl
    || seededFeatured.content[0].mainImage;
  expect(brokenAsset).toMatch(/^\/assets\/properties\//);

  await page.route(`**${brokenAsset}`, route => route.fulfill({
    status: 404,
    contentType: 'text/plain',
    body: 'T273 intentional missing seeded asset'
  }));

  const popularResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.endsWith('/api/public/popular-destinations')
      && response.request().method() === 'GET';
  });
  const featuredResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url());
    return url.pathname.endsWith('/api/public/properties/search')
      && url.searchParams.get('sortBy') === 'RATING'
      && response.request().method() === 'GET';
  });

  await page.goto('/');
  const [popularResponse, featuredResponse] = await Promise.all([
    popularResponsePromise,
    featuredResponsePromise
  ]);

  expect(popularResponse.status()).toBe(200);
  expect(popularResponse.headers()['cache-control']).toContain('max-age=60');
  expect(popularResponse.headers()['cache-control']).toContain('public');
  expect(popularResponse.headers()['x-luxestay-freshness-seconds']).toBe('60');
  const popular = await popularResponse.json() as PopularDestination[];
  expect(popular.length).toBeGreaterThan(0);
  expect(popular.map(destination => destination.id)).toEqual(
    [...popular]
      .sort((left, right) => right.propertyCount - left.propertyCount
        || compareText(left.displayName, right.displayName)
        || left.id - right.id)
      .map(destination => destination.id)
  );

  const popularCards = page.locator('app-popular-destinations [data-destination-id]');
  await expect(popularCards).toHaveCount(popular.length);
  expect(await popularCards.evaluateAll(cards => cards.map(card => Number(card.getAttribute('data-destination-id')))))
    .toEqual(popular.map(destination => destination.id));
  for (let index = 0; index < popular.length; index++) {
    const destination = popular[index];
    expect(destination.imageAltText.trim()).not.toBe('');
    expect(destination.imageProvenance).toMatch(/^BUNDLED_DESTINATION:destination-\d{2}\.webp$/);
    const image = popularCards.nth(index).locator('img');
    await expect(image).toHaveAttribute('alt', destination.imageAltText);
    await expect(image).toHaveAttribute('data-image-provenance', destination.imageProvenance);
  }

  expect(featuredResponse.status()).toBe(200);
  expect(featuredResponse.headers()['cache-control']).toContain('no-store');
  expect(featuredResponse.headers()['x-luxestay-freshness']).toBe('LIVE_SEARCH');
  const featured = await featuredResponse.json() as FeaturedPropertyPage;
  expect(featured.content.length).toBeGreaterThan(0);
  expect(featured.content.map(property => property.id)).toEqual(
    [...featured.content]
      .sort((left, right) => {
        const leftReviewed = left.reviewScore !== null && left.reviewScore !== undefined;
        const rightReviewed = right.reviewScore !== null && right.reviewScore !== undefined;
        if (leftReviewed !== rightReviewed) return leftReviewed ? -1 : 1;
        return (right.reviewScore ?? 0) - (left.reviewScore ?? 0)
          || (right.reviewCount ?? 0) - (left.reviewCount ?? 0)
          || left.id - right.id;
      })
      .map(property => property.id)
  );

  const featuredCards = page.locator('app-featured-properties [data-property-id]');
  await expect(featuredCards).toHaveCount(featured.content.length);
  expect(await featuredCards.evaluateAll(cards => cards.map(card => Number(card.getAttribute('data-property-id')))))
    .toEqual(featured.content.map(property => property.id));
  for (let index = 0; index < featured.content.length; index++) {
    const property = featured.content[index];
    expect(property.imageAltText.trim()).not.toBe('');
    expect(['PROPERTY_MEDIA', 'PROPERTY_CATALOG_MAIN']).toContain(property.imageProvenance);
    const image = featuredCards.nth(index).locator('img');
    await expect(image).toHaveAttribute('alt', property.imageAltText);
    await expect(image).toHaveAttribute('data-image-provenance', property.imageProvenance);
  }

  const firstFeaturedImage = featuredCards.first().locator('img');
  await firstFeaturedImage.scrollIntoViewIfNeeded();
  await expect(firstFeaturedImage).toHaveAttribute('src', /\/assets\/fallbacks\/.+-default\.webp$/);
  await expect.poll(() => firstFeaturedImage.evaluate(image => {
    const element = image as HTMLImageElement;
    return element.complete && element.naturalWidth > 0;
  })).toBe(true);
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
